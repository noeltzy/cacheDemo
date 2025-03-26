package com.zhongyuan.cachedemo.mapper;

import com.baomidou.mybatisplus.core.injector.methods.SelectById;
import com.zhongyuan.cachedemo.domain.Product;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.springframework.cache.annotation.Cacheable;

import java.io.Serializable;

/**
* @author Windows11
* @description 针对表【product】的数据库操作Mapper
* @createDate 2025-03-12 23:10:53
* @Entity generator.domain.Product
*/
public interface ProductMapper extends BaseMapper<Product> {

}




