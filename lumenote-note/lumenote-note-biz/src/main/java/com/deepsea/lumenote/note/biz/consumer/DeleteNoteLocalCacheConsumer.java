package com.deepsea.lumenote.note.biz.consumer;

import com.deepsea.lumenote.note.biz.service.NoteService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 本地笔记缓存失效消费者
 */
@Component
@Slf4j
public class DeleteNoteLocalCacheConsumer {

    @Autowired
    private NoteService noteService;

    @RabbitListener(queues = "#{deleteNoteLocalCacheQueue.name}")
    public void deleteNoteLocalCache(Long noteId) {
        // 判空
        if (Objects.isNull(noteId)) {
            log.error("## 拒绝消费：消息体缺失笔记 ID，转入错误队列");
            throw new AmqpRejectAndDontRequeueException("消息内容为空，无法提取笔记 ID！");
        };

        log.info("## 即将删除本地笔记缓存, 笔记 ID: {}", noteId);
        // 删除本地缓存中的笔记数据
        noteService.deleteNoteLocalCache(noteId);
        log.info("## 成功删除本地笔记缓存, 笔记 ID: {}", noteId);
    }
}
