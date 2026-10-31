package com.deepsea.lumenote.kv.biz;

import com.deepsea.framework.common.util.JsonUtils;
import com.deepsea.lumenote.kv.biz.domain.domain.dataobject.NoteContentDO;
import com.deepsea.lumenote.kv.biz.domain.domain.repository.NoteContentRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;
import java.util.UUID;

@SpringBootTest
@Slf4j
public class CassandraTests {

    @Autowired
    private NoteContentRepository noteContentRepository;

    /**
     * 测试插入数据1
     */
    @Test
    void testInsert() {
        NoteContentDO nodeContent  = NoteContentDO.builder()
                .id(UUID.randomUUID())
                .content("代码测试笔记内容插入")
                .build();

        noteContentRepository.save(nodeContent);
    }

    /**
     * 测试修改数据
     */
    @Test
    void testUpdate(){
        NoteContentDO nodeContent = NoteContentDO.builder()
                .id(UUID.fromString("0dbcf933-f62a-4f58-a79a-36c00b7e93c0"))
                .content("代码测试笔记内容更新")
                .build();

        noteContentRepository.save(nodeContent);
    }

    /**
     * 测试查询数据
     */
    @Test
    void testQuery(){
        Optional<NoteContentDO> optional = noteContentRepository.findById(UUID.fromString("0dbcf933-f62a-4f58-a79a-36c00b7e93c0"));
        optional.ifPresent(noteContentDO -> log.info("查询结果：{}", JsonUtils.toJsonString(noteContentDO)));
    }

    /**
     * 测试删除数据
     */
    @Test
    void testDelete(){
        noteContentRepository.deleteById(UUID.fromString("0dbcf933-f62a-4f58-a79a-36c00b7e93c0"));
    }
}
