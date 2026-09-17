package com.robot.platform.device.group.controller.admin;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import com.robot.platform.device.group.dal.dataobject.DeviceGroupDO;
import com.robot.platform.device.group.service.DeviceGroupService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
@RestController @RequestMapping("/admin-api/device/groups") @Validated @RequiredArgsConstructor public class DeviceGroupController {
 private final DeviceGroupService service;
 @PostMapping @PreAuthorize("@ss.hasPermission('device:group:create')") public CommonResult<Long> create(@Validated @RequestBody GroupReq r){return success(service.create(r.name,r.remark));}
 @PutMapping("/{id}") @PreAuthorize("@ss.hasPermission('device:group:update')") public CommonResult<Boolean> update(@PathVariable @Positive long id,@Validated @RequestBody GroupReq r){service.update(id,r.name,r.remark);return success(true);}
 @DeleteMapping("/{id}") @PreAuthorize("@ss.hasPermission('device:group:delete')") public CommonResult<Boolean> delete(@PathVariable @Positive long id){service.delete(id);return success(true);}
 @PostMapping("/{id}/devices/{deviceId}") @PreAuthorize("@ss.hasPermission('device:group:update')") @Operation(summary="添加设备") public CommonResult<Boolean> add(@PathVariable @Positive long id,@PathVariable @Positive long deviceId){service.addDevice(id,deviceId);return success(true);}
 @DeleteMapping("/{id}/devices/{deviceId}") @PreAuthorize("@ss.hasPermission('device:group:update')") public CommonResult<Boolean> remove(@PathVariable @Positive long id,@PathVariable @Positive long deviceId){service.removeDevice(id,deviceId);return success(true);}
 @GetMapping @PreAuthorize("@ss.hasPermission('device:group:query')") public CommonResult<List<DeviceGroupDO>> list(){return success(service.listMine());}
 @Data public static class GroupReq { @NotBlank @Size(max=128) private String name; @Size(max=500) private String remark; }
}
