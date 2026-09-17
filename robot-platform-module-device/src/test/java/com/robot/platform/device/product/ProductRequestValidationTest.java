package com.robot.platform.device.product;

import com.robot.platform.device.product.controller.admin.vo.ProductCreateReqVO;
import com.robot.platform.device.product.controller.admin.vo.ProductUpdateReqVO;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void apiRequestsRejectStatusOutsideProductStatusEnum() {
        ProductCreateReqVO create = new ProductCreateReqVO();
        create.setProductKey("model-1");
        create.setName("型号");
        create.setStatus(99);
        ProductUpdateReqVO update = new ProductUpdateReqVO();
        update.setProductKey("model-1");
        update.setName("型号");
        update.setStatus(99);

        assertThat(validator.validate(create)).extracting(violation -> violation.getPropertyPath().toString())
                .contains("status");
        assertThat(validator.validate(update)).extracting(violation -> violation.getPropertyPath().toString())
                .contains("status");
    }
}
