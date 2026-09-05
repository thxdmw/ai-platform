package com.thx.aiplatform.website.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.thx.aiplatform.website.entity.WebsiteKnowledgeEntryEntity;
import org.apache.ibatis.annotations.Mapper;

/** website_knowledge_entries 表的 MyBatis-Plus 映射器。 */
@Mapper
public interface WebsiteKnowledgeMapper extends BaseMapper<WebsiteKnowledgeEntryEntity> { }