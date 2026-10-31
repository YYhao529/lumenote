package com.deepsea.lumenote.user.biz.domain.dataobject;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RolePermissionRelDO {
    private Long id;

    private Long roleId;

    private Long permissionId;

    private Date createTime;

    private Date updateTime;

    private Boolean isDeleted;
}