package com.thx.aiplatform.server.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.thx.aiplatform.server.entity.ServerEntity;
import org.apache.ibatis.annotations.Mapper;

/** server_assistant_server 表的 MyBatis-Plus 映射器。 */
@Mapper
public interface ServerMapper extends BaseMapper<ServerEntity> { }