package com.robot.platform.device.product.convert;

import com.robot.platform.device.product.controller.admin.vo.ProductCreateReqVO;
import com.robot.platform.device.product.controller.admin.vo.ProductRespVO;
import com.robot.platform.device.product.controller.admin.vo.ProductUpdateReqVO;
import com.robot.platform.device.product.dal.dataobject.ProductDO;
import com.robot.platform.device.product.service.command.ProductCreateCommand;
import com.robot.platform.device.product.service.command.ProductUpdateCommand;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface ProductConvert {
    ProductConvert INSTANCE = Mappers.getMapper(ProductConvert.class);
    ProductCreateCommand convert(ProductCreateReqVO source);
    ProductUpdateCommand convert(ProductUpdateReqVO source);
    ProductRespVO convert(ProductDO source);
    List<ProductRespVO> convertList(List<ProductDO> source);
}
