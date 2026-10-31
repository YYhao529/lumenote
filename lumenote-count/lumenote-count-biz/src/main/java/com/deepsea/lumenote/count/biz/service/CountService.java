package com.deepsea.lumenote.count.biz.service;

import com.deepsea.lumenote.count.biz.model.vo.FindNoteCountReqVO;
import com.deepsea.lumenote.count.biz.model.vo.FindUserCountReqVO;
import com.deepsea.lumenote.count.biz.model.vo.NoteCountRspVO;
import com.deepsea.lumenote.count.biz.model.vo.UserCountRspVO;

/**
 * 计数查询业务
 */
public interface CountService {

    /**
     * 查询笔记计数（点赞 / 收藏 / 评论）
     */
    NoteCountRspVO findNoteCount(FindNoteCountReqVO findNoteCountReqVO);

    /**
     * 查询用户计数（粉丝 / 关注 / 笔记数 / 被赞 / 被收藏）
     */
    UserCountRspVO findUserCount(FindUserCountReqVO findUserCountReqVO);
}
