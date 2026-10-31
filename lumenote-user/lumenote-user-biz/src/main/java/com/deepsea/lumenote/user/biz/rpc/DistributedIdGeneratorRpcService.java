package com.deepsea.lumenote.user.biz.rpc;

import com.deepsea.lumenote.distributed.id.generator.api.DistributedIdGeneratorFeignApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 分布式 ID 生成服务
 */
@Component
public class DistributedIdGeneratorRpcService {

    @Autowired
    private DistributedIdGeneratorFeignApi distributedIdGeneratorFeignApi;

    /**
     * Leaf 号段模式：时光笔记 ID 业务标识
     */
    private static final String BIZ_TAG_LUMENOTE_ID="leaf-segment-lumenote-id";

    /**
     * Leaf 号段模式：用户 ID 业务标识
     */
    private static final String BIZ_TAG_USER_ID = "leaf-segment-user-id";

    /**
     * 调用分布式 ID 生成服务生成时光笔记 ID
     */
    public String getLumenoteId(){
        return distributedIdGeneratorFeignApi.getSegmentId(BIZ_TAG_LUMENOTE_ID);
    }

    /**
     * 调用分布式 ID 生成服务用户 ID
     */
    public String getUserId() {
        return distributedIdGeneratorFeignApi.getSegmentId(BIZ_TAG_USER_ID);
    }
}
