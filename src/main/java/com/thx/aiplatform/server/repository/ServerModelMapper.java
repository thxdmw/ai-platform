package com.thx.aiplatform.server.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.thx.aiplatform.server.entity.ServerModelEntity;
import org.apache.ibatis.annotations.Mapper;

/** server_assistant_model 表的 MyBatis-Plus 映射器。 */
@Mapper
public interface ServerModelMapper extends BaseMapper<ServerModelEntity> { }