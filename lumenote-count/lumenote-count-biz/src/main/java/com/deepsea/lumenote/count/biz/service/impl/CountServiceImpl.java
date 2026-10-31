package com.deepsea.lumenote.count.biz.service.impl;

import com.deepsea.lumenote.count.biz.constant.RedisKeyConstants;
import com.deepsea.lumenote.count.biz.domain.dataobject.NoteCountDO;
import com.deepsea.lumenote.count.biz.domain.dataobject.UserCountDO;
import com.deepsea.lumenote.count.biz.domain.mapper.NoteCountDOMapper;
import com.deepsea.lumenote.count.biz.domain.mapper.UserCountDOMapper;
import com.deepsea.lumenote.count.biz.model.vo.FindNoteCountReqVO;
import com.deepsea.lumenote.count.biz.model.vo.FindUserCountReqVO;
import com.deepsea.lumenote.count.biz.model.vo.NoteCountRspVO;
import com.deepsea.lumenote.count.biz.model.vo.UserCountRspVO;
import com.deepsea.lumenote.count.biz.service.CountService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 计数查询业务实现
 *
 * 说明：数据库是计数的最终数据源，Redis 只是缓存。
 * 消费者只更新「已存在」的计数 Hash（避免缓存雪崩），因此这里做**懒初始化**：
 * 查询时若 Hash 不存在或字段缺失，从数据库读取并回填缓存（带过期时间）。
 */
@Service
@Slf4j
public class CountServiceImpl implements CountService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private NoteCountDOMapper noteCountDOMapper;
    @Autowired
    private UserCountDOMapper userCountDOMapper;

    /**
     * 缓存基础过期时间：1 天
     */
    private static final long BASE_EXPIRE_SECONDS = 60L * 60 * 24;

    @Override
    public NoteCountRspVO findNoteCount(FindNoteCountReqVO findNoteCountReqVO) {
        Long noteId = findNoteCountReqVO.getNoteId();
        String countNoteKey = RedisKeyConstants.buildCountNoteKey(noteId);

        // 先查 Redis 缓存
        Map<Object, Object> cache = redisTemplate.opsForHash().entries(countNoteKey);

        // 有字段缺失（或整个 Hash 不存在）时，从数据库补齐
        boolean needInit = !cache.containsKey(RedisKeyConstants.FIELD_LIKE_TOTAL)
                || !cache.containsKey(RedisKeyConstants.FIELD_COLLECT_TOTAL)
                || !cache.containsKey(RedisKeyConstants.FIELD_COMMENT_TOTAL);

        if (needInit) {
            NoteCountDO noteCountDO = noteCountDOMapper.selectByNoteId(noteId);

            Map<String, Object> initMap = new HashMap<>();
            if (!cache.containsKey(RedisKeyConstants.FIELD_LIKE_TOTAL)) {
                initMap.put(RedisKeyConstants.FIELD_LIKE_TOTAL,
                        Objects.isNull(noteCountDO) ? 0L : nullSafe(noteCountDO.getLikeTotal()));
            }
            if (!cache.containsKey(RedisKeyConstants.FIELD_COLLECT_TOTAL)) {
                initMap.put(RedisKeyConstants.FIELD_COLLECT_TOTAL,
                        Objects.isNull(noteCountDO) ? 0L : nullSafe(noteCountDO.getCollectTotal()));
            }
            if (!cache.containsKey(RedisKeyConstants.FIELD_COMMENT_TOTAL)) {
                initMap.put(RedisKeyConstants.FIELD_COMMENT_TOTAL,
                        Objects.isNull(noteCountDO) ? 0L : nullSafe(noteCountDO.getCommentTotal()));
            }

            if (!initMap.isEmpty()) {
                redisTemplate.opsForHash().putAll(countNoteKey, initMap);
            }

            // 缓存没有过期时间时补上（打散过期时间，防止同一时间大量缓存失效）
            setExpireIfAbsent(countNoteKey);

            cache = redisTemplate.opsForHash().entries(countNoteKey);
        }

        return NoteCountRspVO.builder()
                .noteId(noteId)
                .likeTotal(toLong(cache.get(RedisKeyConstants.FIELD_LIKE_TOTAL)))
                .collectTotal(toLong(cache.get(RedisKeyConstants.FIELD_COLLECT_TOTAL)))
                .commentTotal(toLong(cache.get(RedisKeyConstants.FIELD_COMMENT_TOTAL)))
                .build();
    }

    @Override
    public UserCountRspVO findUserCount(FindUserCountReqVO findUserCountReqVO) {
        Long userId = findUserCountReqVO.getUserId();
        String countUserKey = RedisKeyConstants.buildCountUserKey(userId);

        Map<Object, Object> cache = redisTemplate.opsForHash().entries(countUserKey);

        boolean needInit = !cache.containsKey(RedisKeyConstants.FIELD_FANS_TOTAL)
                || !cache.containsKey(RedisKeyConstants.FIELD_FOLLOWING_TOTAL)
                || !cache.containsKey(RedisKeyConstants.FIELD_NOTE_TOTAL)
                || !cache.containsKey(RedisKeyConstants.FIELD_LIKE_TOTAL)
                || !cache.containsKey(RedisKeyConstants.FIELD_COLLECT_TOTAL);

        if (needInit) {
            UserCountDO userCountDO = userCountDOMapper.selectByUserId(userId);

            Map<String, Object> initMap = new HashMap<>();
            if (!cache.containsKey(RedisKeyConstants.FIELD_FANS_TOTAL)) {
                initMap.put(RedisKeyConstants.FIELD_FANS_TOTAL,
                        Objects.isNull(userCountDO) ? 0L : nullSafe(userCountDO.getFansTotal()));
            }
            if (!cache.containsKey(RedisKeyConstants.FIELD_FOLLOWING_TOTAL)) {
                initMap.put(RedisKeyConstants.FIELD_FOLLOWING_TOTAL,
                        Objects.isNull(userCountDO) ? 0L : nullSafe(userCountDO.getFollowingTotal()));
            }
            if (!cache.containsKey(RedisKeyConstants.FIELD_NOTE_TOTAL)) {
                initMap.put(RedisKeyConstants.FIELD_NOTE_TOTAL,
                        Objects.isNull(userCountDO) ? 0L : nullSafe(userCountDO.getNoteTotal()));
            }
            if (!cache.containsKey(RedisKeyConstants.FIELD_LIKE_TOTAL)) {
                initMap.put(RedisKeyConstants.FIELD_LIKE_TOTAL,
                        Objects.isNull(userCountDO) ? 0L : nullSafe(userCountDO.getLikeTotal()));
            }
            if (!cache.containsKey(RedisKeyConstants.FIELD_COLLECT_TOTAL)) {
                initMap.put(RedisKeyConstants.FIELD_COLLECT_TOTAL,
                        Objects.isNull(userCountDO) ? 0L : nullSafe(userCountDO.getCollectTotal()));
            }

            if (!initMap.isEmpty()) {
                redisTemplate.opsForHash().putAll(countUserKey, initMap);
            }

            setExpireIfAbsent(countUserKey);

            cache = redisTemplate.opsForHash().entries(countUserKey);
        }

        return UserCountRspVO.builder()
                .userId(userId)
                .fansTotal(toLong(cache.get(RedisKeyConstants.FIELD_FANS_TOTAL)))
                .followingTotal(toLong(cache.get(RedisKeyConstants.FIELD_FOLLOWING_TOTAL)))
                .noteTotal(toLong(cache.get(RedisKeyConstants.FIELD_NOTE_TOTAL)))
                .likeTotal(toLong(cache.get(RedisKeyConstants.FIELD_LIKE_TOTAL)))
                .collectTotal(toLong(cache.get(RedisKeyConstants.FIELD_COLLECT_TOTAL)))
                .build();
    }

    /**
     * 缓存未设置过期时间时补上（1 天 + 随机秒数，打散过期时间）
     */
    private void setExpireIfAbsent(String key) {
        Long expire = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        if (Objects.isNull(expire) || expire < 0) {
            long timeToLive = BASE_EXPIRE_SECONDS + ThreadLocalRandom.current().nextInt(60 * 60 * 24);
            redisTemplate.expire(key, timeToLive, TimeUnit.SECONDS);
        }
    }

    private long nullSafe(Long value) {
        return Objects.isNull(value) ? 0L : value;
    }

    private long toLong(Object value) {
        if (Objects.isNull(value)) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
