package com.robot.platform.device.device.service;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import com.robot.platform.device.device.dal.dataobject.DeviceDO;
import com.robot.platform.device.device.dal.mysql.DeviceMapper;
import com.robot.platform.device.device.enums.DeviceLifecycle;
import com.robot.platform.device.device.service.command.DeviceActivateCommand;
import com.robot.platform.device.device.service.command.DeviceCreateCommand;
import com.robot.platform.device.device.service.command.DeviceInventoryUpdateCommand;
import com.robot.platform.device.product.dal.dataobject.ProductDO;
import com.robot.platform.device.product.service.ProductService;
import com.robot.platform.device.spi.RobotProvisionCommand;
import com.robot.platform.device.spi.RobotProvisioningGateway;
import com.robot.platform.security.crypto.SecretCipher;
import com.robot.platform.tenant.quota.service.TenantRobotQuotaService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static com.robot.platform.device.device.enums.DeviceErrorCodeConstants.*;

@Service @RequiredArgsConstructor
public class DeviceServiceImpl implements DeviceService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final DeviceMapper deviceMapper; private final ProductService productService; private final TenantRobotQuotaService quotaService;
    private final RobotProvisioningGateway provisioningGateway; private final SecretCipher secretCipher; private final PasswordEncoder passwordEncoder;
    private final DeviceAccessPolicy accessPolicy; private final TenantNamespaceResolver namespaceResolver;
    private final DeviceCredentialRevocationPublisher revocationPublisher;

    @Override @Transactional(rollbackFor = Exception.class)
    public long createInventoryDevice(DeviceCreateCommand command) {
        requirePlatformAdmin(); productService.requireEnabledForInventory(command.getProductId());
        if (deviceMapper.selectByDeviceSn(command.getDeviceSn()) != null) throw exception(DEVICE_SERIAL_EXISTS);
        DeviceDO device=DeviceDO.builder().productId(command.getProductId()).deviceSn(command.getDeviceSn()).name(command.getName())
                .lifecycleStatus(DeviceLifecycle.UNACTIVATED.name()).credentialVersion(0).build();
        try { deviceMapper.insert(device); } catch (DuplicateKeyException e) { throw exception(DEVICE_SERIAL_EXISTS); }
        return device.getId();
    }
    @Override @Transactional(rollbackFor = Exception.class)
    public void updateInventoryDevice(long deviceId, DeviceInventoryUpdateCommand command) {
        requirePlatformAdmin(); DeviceDO device=requireDeviceForUpdate(deviceId);
        if(lifecycle(device)!=DeviceLifecycle.UNACTIVATED || device.getTenantId()!=null) throw exception(DEVICE_LIFECYCLE_INVALID);
        productService.requireEnabledForInventory(command.getProductId());
        if(!device.getDeviceSn().equals(command.getDeviceSn()) && deviceMapper.selectByDeviceSn(command.getDeviceSn())!=null) throw exception(DEVICE_SERIAL_EXISTS);
        device.setProductId(command.getProductId()); device.setDeviceSn(command.getDeviceSn()); device.setName(command.getName()); deviceMapper.updateById(device);
    }
    @Override @Transactional(rollbackFor = Exception.class)
    public void deleteInventoryDevice(long deviceId) {
        requirePlatformAdmin(); DeviceDO device=requireDeviceForUpdate(deviceId);
        if(lifecycle(device)!=DeviceLifecycle.UNACTIVATED || device.getTenantId()!=null) throw exception(DEVICE_LIFECYCLE_INVALID);
        deviceMapper.deleteById(deviceId);
    }
    @Override public List<DeviceDO> listInventory() { requirePlatformAdmin(); return deviceMapper.selectInventory(); }
    @Override @Transactional(rollbackFor = Exception.class)
    public DeviceActivationResult activate(long deviceId, DeviceActivateCommand command) {
        long tenantId=requiredTenant(); DeviceDO device=requireDeviceForUpdate(deviceId);
        if (lifecycle(device)!=DeviceLifecycle.UNACTIVATED || device.getTenantId()!=null || device.getRobotId()!=null) throw exception(DEVICE_NOT_UNACTIVATED);
        ProductDO product=productService.requireActivatable(device.getProductId(),tenantId); quotaService.checkCanActivate(tenantId);
        Credentials credentials=newCredentials();
        device.setTenantId(tenantId); device.setLifecycleStatus(DeviceLifecycle.ACTIVATED.name()); device.setCredentialVersion(device.getCredentialVersion()+1);
        device.setMqttUsername(mqttUsername(namespaceResolver.resolve(tenantId),product.getProductKey(),device.getDeviceSn()));
        device.setMqttSecretHash(passwordEncoder.encode(credentials.mqttSecret())); device.setHttpSecretCiphertext(secretCipher.encrypt(credentials.httpSecret()));
        device.setActivateTime(LocalDateTime.now()); device.setLastBindTime(LocalDateTime.now()); deviceMapper.updateById(device);
        long robotId=provisioningGateway.provision(new RobotProvisionCommand(tenantId,device.getId(),product.getId(),command.getRobotCode(),command.getRobotName()));
        device.setRobotId(robotId); deviceMapper.updateById(device); quotaService.changeRobotUsage(tenantId,1); return result(device,credentials);
    }
    @Override @Transactional(rollbackFor = Exception.class)
    public void unbind(long deviceId) {
        long tenantId=requiredTenant(); DeviceDO device=requireDeviceForUpdate(deviceId); requireOwned(device,tenantId); if(device.getRobotId()==null) throw exception(DEVICE_NOT_ACTIVATED);
        int expectedVersion=device.getCredentialVersion();
        provisioningGateway.deprovision(tenantId,device.getRobotId()); quotaService.changeRobotUsage(tenantId,-1); invalidateSessions(device,tenantId,"UNBOUND");
        if (deviceMapper.resetToInventoryAfterUnbind(deviceId, tenantId, expectedVersion, device.getCredentialVersion()) != 1) throw exception(DEVICE_LIFECYCLE_INVALID);
        clearCredentials(device); device.setTenantId(null); device.setRobotId(null); device.setLifecycleStatus(DeviceLifecycle.UNACTIVATED.name()); device.setActivateTime(null); device.setLastBindTime(null);
    }
    @Override @Transactional(rollbackFor = Exception.class)
    public void changeLifecycle(long deviceId, DeviceLifecycle target) {
        DeviceDO device=requireDeviceForUpdate(deviceId); DeviceLifecycle from=lifecycle(device);
        if(from==DeviceLifecycle.UNACTIVATED || device.getTenantId()==null) requirePlatformAdmin(); else requireOwned(device,requiredTenant());
        if(target==DeviceLifecycle.ACTIVATED && from==DeviceLifecycle.UNACTIVATED) throw exception(DEVICE_LIFECYCLE_INVALID);
        if(!DeviceLifecyclePolicy.canTransition(from,target)) throw exception(DEVICE_LIFECYCLE_INVALID);
        long tenantId=device.getTenantId()==null?0L:device.getTenantId();
        if(target==DeviceLifecycle.DISABLED) invalidateSessions(device,tenantId,"DISABLED");
        if(target==DeviceLifecycle.SCRAPPED) { if(device.getRobotId()!=null){provisioningGateway.deprovision(tenantId,device.getRobotId());quotaService.changeRobotUsage(tenantId,-1);device.setRobotId(null);} invalidateSessions(device,tenantId,"SCRAPPED"); clearCredentials(device); }
        device.setLifecycleStatus(target.name()); deviceMapper.updateById(device);
    }
    @Override @Transactional(rollbackFor = Exception.class)
    public DeviceActivationResult rotateCredentials(long deviceId) {
        long tenantId=requiredTenant(); DeviceDO device=requireDeviceForUpdate(deviceId); requireOwned(device,tenantId); if(lifecycle(device)!=DeviceLifecycle.ACTIVATED) throw exception(DEVICE_NOT_ACTIVATED);
        Credentials credentials=newCredentials(); invalidateSessions(device,tenantId,"ROTATED"); device.setMqttSecretHash(passwordEncoder.encode(credentials.mqttSecret())); device.setHttpSecretCiphertext(secretCipher.encrypt(credentials.httpSecret())); deviceMapper.updateById(device); return result(device,credentials);
    }
    @Override public DeviceDO get(long id) { DeviceDO d=deviceMapper.selectById(id); if(d==null) throw exception(DEVICE_NOT_EXISTS); if(d.getTenantId()==null) requirePlatformAdmin(); else requireOwned(d,requiredTenant()); return d; }
    @Override public List<DeviceDO> listMine() { return deviceMapper.selectByTenantId(requiredTenant()); }
    private void invalidateSessions(DeviceDO d,long tenantId,String reason) { d.setCredentialVersion(d.getCredentialVersion()+1); revocationPublisher.publish(new DeviceCredentialsRevokedEvent(d.getId(),tenantId,d.getCredentialVersion(),reason)); }
    private static void clearCredentials(DeviceDO d) { d.setMqttUsername(null); d.setMqttSecretHash(null); d.setHttpSecretCiphertext(null); }
    private DeviceDO requireDeviceForUpdate(long id){DeviceDO d=deviceMapper.selectByIdForUpdate(id);if(d==null)throw exception(DEVICE_NOT_EXISTS);return d;}
    private long requiredTenant(){try{return TenantContextHolder.getRequiredTenantId();}catch(Exception e){throw exception(DEVICE_TENANT_REQUIRED);}}
    private void requirePlatformAdmin(){if(!accessPolicy.isPlatformSuperAdmin())throw exception(DEVICE_SCOPE_FORBIDDEN);}
    private void requireOwned(DeviceDO d,long t){if(!Long.valueOf(t).equals(d.getTenantId()))throw exception(DEVICE_SCOPE_FORBIDDEN);}
    private static DeviceLifecycle lifecycle(DeviceDO d){return DeviceLifecycle.of(d.getLifecycleStatus());}
    private static String mqttUsername(String ns,String key,String sn){return ns+"/"+key+"/"+sn;}
    private static Credentials newCredentials(){return new Credentials(randomSecret(),randomSecret());}
    private static String randomSecret(){byte[] b=new byte[32];RANDOM.nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
    private static DeviceActivationResult result(DeviceDO d,Credentials c){return new DeviceActivationResult(d.getId(),d.getRobotId(),d.getMqttUsername(),c.mqttSecret(),c.httpSecret(),d.getCredentialVersion());}
    private record Credentials(String mqttSecret,String httpSecret){@Override public String toString(){return "Credentials[mqttSecret=<redacted>, httpSecret=<redacted>]";}}
}
