package com.robot.platform.device.product.controller.admin;

import com.robot.platform.framework.common.pojo.CommonResult;
import com.robot.platform.device.product.controller.admin.vo.ProductCreateReqVO;
import com.robot.platform.device.product.controller.admin.vo.ProductRespVO;
import com.robot.platform.device.product.controller.admin.vo.ProductUpdateReqVO;
import com.robot.platform.device.product.convert.ProductConvert;
import com.robot.platform.device.product.service.ProductService;
import com.robot.platform.device.product.service.command.ProductPageQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.robot.platform.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 产品型号")
@RestController
@RequestMapping("/admin-api/device/products")
@Validated
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    @Operation(summary = "创建产品型号")
    @PreAuthorize("@ss.hasPermission('device:product:create')")
    public CommonResult<Long> create(@Valid @RequestBody ProductCreateReqVO request) {
        return success(productService.create(ProductConvert.INSTANCE.convert(request)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新产品型号")
    @PreAuthorize("@ss.hasPermission('device:product:update')")
    public CommonResult<Boolean> update(@PathVariable long id, @Valid @RequestBody ProductUpdateReqVO request) {
        productService.update(id, ProductConvert.INSTANCE.convert(request));
        return success(true);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除产品型号")
    @PreAuthorize("@ss.hasPermission('device:product:delete')")
    public CommonResult<Boolean> delete(@PathVariable long id) {
        productService.delete(id);
        return success(true);
    }

    @GetMapping
    @Operation(summary = "获取当前租户可见产品型号")
    @PreAuthorize("@ss.hasPermission('device:product:query')")
    public CommonResult<List<ProductRespVO>> list(ProductPageQuery query) {
        return success(ProductConvert.INSTANCE.convertList(productService.listVisible(query)));
    }
}
