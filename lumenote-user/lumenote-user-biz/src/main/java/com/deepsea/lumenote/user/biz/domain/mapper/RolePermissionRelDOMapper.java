package com.deepsea.lumenote.user.biz.domain.mapper;

import com.deepsea.lumenote.user.biz.domain.dataobject.RolePermissionRelDO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface RolePermissionRelDOMapper {
    int deleteByPrimaryKey(Long id);

    int insert(RolePermissionRelDO record);

    int insertSelective(RolePermissionRelDO record);

    RolePermissionRelDO selectByPrimaryKey(Long id);

    int updateByPrimaryKeySelective(RolePermissionRelDO record);

    int updateByPrimaryKey(RolePermissionRelDO record);

    /**
     * 根据角色 ID 集合批量查询
     */
    List<RolePermissionRelDO> selectByRoleIds(@Param("roleIds") List<Long> roleIdList);
}