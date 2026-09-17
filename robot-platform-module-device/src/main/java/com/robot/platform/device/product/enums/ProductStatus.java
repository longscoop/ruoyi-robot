package com.robot.platform.device.product.enums;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/** 产品型号状态。 */
@Getter
@AllArgsConstructor
public enum ProductStatus implements ArrayValuable<Integer> {

    ENABLE(0),
    DISABLE(1);

    public static final Integer[] ARRAYS = Arrays.stream(values()).map(ProductStatus::getStatus).toArray(Integer[]::new);

    private final int status;

    @Override
    public Integer[] array() {
        return ARRAYS;
    }

    public static boolean isValid(Integer status) {
        return Arrays.asList(ARRAYS).contains(status);
    }
}
