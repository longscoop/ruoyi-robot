package com.robot.platform.member.member;

import com.robot.platform.member.member.controller.admin.MemberAdminController;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class MemberAdminRequestValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    @Test void createRequiresPasswordButUpdateAllowsItToBeOmitted() {
        MemberAdminController.CreateReqVO create = new MemberAdminController.CreateReqVO(); create.setMobile("13800138000");
        MemberAdminController.UpdateReqVO update = new MemberAdminController.UpdateReqVO(); update.setMobile("13800138000");
        assertThat(validator.validate(create)).extracting(v -> v.getPropertyPath().toString()).contains("password");
        assertThat(validator.validate(update)).isEmpty();
    }
}
