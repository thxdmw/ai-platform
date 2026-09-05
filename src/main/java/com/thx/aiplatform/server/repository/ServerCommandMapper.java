package com.thx.aiplatform.server.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.thx.aiplatform.server.entity.ServerCommandEntity;
import org.apache.ibatis.annotations.Mapper;

/** server_assistant_command 表的 MyBatis-Plus 映射器。 */
@Mapper
public interface ServerCommandMapper extends BaseMapper<ServerCommandEntity> { }