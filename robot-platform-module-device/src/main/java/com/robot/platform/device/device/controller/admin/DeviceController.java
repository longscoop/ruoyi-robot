package com.robot.platform.device.device.controller.admin;

import com.robot.platform.framework.common.pojo.CommonResult;
import com.robot.platform.device.device.controller.admin.vo.*;
import com.robot.platform.device.device.convert.DeviceConvert;
import com.robot.platform.device.device.enums.DeviceLifecycle;
import com.robot.platform.device.device.service.DeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static com.robot.platform.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 设备") @RestController @RequestMapping("/admin-api/device/devices") @Validated @RequiredArgsConstructor
public class DeviceController {
    private final DeviceService deviceService;
    @PostMapping @PreAuthorize("@ss.hasPermission('device:device:create')") @Operation(summary = "设备入库")
    public CommonResult<Long> create(@Valid @RequestBody DeviceCreateReqVO request) { return success(deviceService.createInventoryDevice(DeviceConvert.INSTANCE.convert(request))); }
    @PutMapping("/{id}/inventory") @PreAuthorize("@ss.hasPermission('device:device:update')")
    public CommonResult<Boolean> updateInventory(@PathVariable long id,@Valid @RequestBody DeviceInventoryUpdateReqVO request) { deviceService.updateInventoryDevice(id,DeviceConvert.INSTANCE.convert(request)); return success(true); }
    @DeleteMapping("/{id}/inventory") @PreAuthorize("@ss.hasPermission('device:device:delete')")
    public CommonResult<Boolean> deleteInventory(@PathVariable long id) { deviceService.deleteInventoryDevice(id); return success(true); }
    @GetMapping("/inventory") @PreAuthorize("@ss.hasPermission('device:device:query')")
    public CommonResult<List<DeviceRespVO>> inventory() { return success(deviceService.listInventory().stream().map(DeviceConvert.INSTANCE::convert).toList()); }
    @PostMapping("/{id}/activate") @PreAuthorize("@ss.hasPermission('device:device:activate')") @Operation(summary = "激活设备并显示一次性凭据")
    public CommonResult<DeviceActivationResultVO> activate(@PathVariable long id, @Valid @RequestBody DeviceActivateReqVO request) { return success(DeviceConvert.INSTANCE.convert(deviceService.activate(id, DeviceConvert.INSTANCE.convert(request)))); }
    @PostMapping("/{id}/unbind") @PreAuthorize("@ss.hasPermission('device:device:unbind')") @Operation(summary = "解绑设备")
    public CommonResult<Boolean> unbind(@PathVariable long id) { deviceService.unbind(id); return success(true); }
    @PostMapping("/{id}/credentials/rotate") @PreAuthorize("@ss.hasPermission('device:device:rotate')") @Operation(summary = "轮换设备凭据")
    public CommonResult<DeviceActivationResultVO> rotate(@PathVariable long id) { return success(DeviceConvert.INSTANCE.convert(deviceService.rotateCredentials(id))); }
    @PutMapping("/{id}/lifecycle/{lifecycle}") @PreAuthorize("@ss.hasPermission('device:device:update')")
    public CommonResult<Boolean> lifecycle(@PathVariable long id, @PathVariable DeviceLifecycle lifecycle) { deviceService.changeLifecycle(id, lifecycle); return success(true); }
    @GetMapping("/{id}") @PreAuthorize("@ss.hasPermission('device:device:query')") public CommonResult<DeviceRespVO> get(@PathVariable long id) { return success(DeviceConvert.INSTANCE.convert(deviceService.get(id))); }
    @GetMapping @PreAuthorize("@ss.hasPermission('device:device:query')") public CommonResult<List<DeviceRespVO>> list() { return success(deviceService.listMine().stream().map(DeviceConvert.INSTANCE::convert).toList()); }
}
