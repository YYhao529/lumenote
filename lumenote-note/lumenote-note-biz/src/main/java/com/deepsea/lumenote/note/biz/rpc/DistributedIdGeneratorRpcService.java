package com.deepsea.lumenote.note.biz.rpc;

import com.deepsea.lumenote.distributed.id.generator.api.DistributedIdGeneratorFeignApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 分布式 ID 生成器 RPC 服务
 */
@Component
public class DistributedIdGeneratorRpcService {

    @Autowired
    private DistributedIdGeneratorFeignApi distributedIdGeneratorFeignApi;

    /**
     * 生成雪花算法 ID
     */
    public String getSnowflakeId() {
        return distributedIdGeneratorFeignApi.getSnowflakeId("test");
    }
}
