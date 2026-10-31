package com.deepsea.lumenote.note.biz.domain.mapper;

import com.deepsea.lumenote.note.biz.domain.dataobject.TopicDO;
import org.apache.ibatis.annotations.Param;

public interface TopicDOMapper {
    int deleteByPrimaryKey(Long id);

    int insert(TopicDO record);

    int insertSelective(TopicDO record);

    TopicDO selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(TopicDO record);

    int updateByPrimaryKey(TopicDO record);

    String selectNameByPrimaryKey(Long id);

    /**
     * 根据话题名称查询话题
     */
    TopicDO selectByName(@Param("name") String name);
}