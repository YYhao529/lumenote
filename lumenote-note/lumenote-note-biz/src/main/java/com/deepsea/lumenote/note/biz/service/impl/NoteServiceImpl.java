package com.deepsea.lumenote.note.biz.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.deepsea.framework.biz.context.holder.LoginUserContextHolder;
import com.deepsea.framework.common.exception.BizException;
import com.deepsea.framework.common.mq.constant.MQConstants;
import com.deepsea.framework.common.response.PageResponse;
import com.deepsea.framework.common.response.Response;
import com.deepsea.framework.common.util.DateUtils;
import com.deepsea.framework.common.util.JsonUtils;
import com.deepsea.lumenote.note.biz.constant.RedisKeyConstants;
import com.deepsea.lumenote.note.biz.domain.dataobject.NoteCollectionDO;
import com.deepsea.lumenote.note.biz.domain.dataobject.NoteDO;
import com.deepsea.lumenote.note.biz.domain.dataobject.NoteLikeDO;
import com.deepsea.lumenote.note.biz.domain.dataobject.TopicDO;
import com.deepsea.lumenote.note.biz.domain.mapper.NoteCollectionDOMapper;
import com.deepsea.lumenote.note.biz.domain.mapper.NoteDOMapper;
import com.deepsea.lumenote.note.biz.domain.mapper.NoteLikeDOMapper;
import com.deepsea.lumenote.note.biz.domain.mapper.TopicDOMapper;
import com.deepsea.lumenote.note.biz.enums.*;
import com.deepsea.lumenote.note.biz.model.dto.CollectUnCollectNoteMqDTO;
import com.deepsea.lumenote.note.biz.model.dto.LikeUnlikeNoteMqDTO;
import com.deepsea.lumenote.note.biz.model.dto.NoteOperateMqDTO;
import com.deepsea.lumenote.note.biz.model.vo.*;
import com.deepsea.lumenote.note.biz.rpc.DistributedIdGeneratorRpcService;
import com.deepsea.lumenote.note.biz.rpc.KeyValueRpcService;
import com.deepsea.lumenote.note.biz.rpc.UserRpcService;
import com.deepsea.lumenote.note.biz.service.NoteService;
import com.deepsea.lumenote.user.dto.resp.FindUserByIdRspDTO;
import jakarta.annotation.Resource;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Service
@Slf4j
public class NoteServiceImpl implements NoteService {

    @Autowired
    private DistributedIdGeneratorRpcService distributedIdGeneratorRpcService;
    @Autowired
    private KeyValueRpcService keyValueRpcService;
    @Autowired
    private TopicDOMapper topicDOMapper;
    @Autowired
    private NoteDOMapper noteDOMapper;
    @Autowired
    private UserRpcService userRpcService;
    @Resource(name = "taskExecutor")
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private NoteCollectionDOMapper noteCollectionDOMapper;

    /**
     * 笔记详情本地缓存
     */
    private static final Cache<Long, String> LOCAL_CACHE = Caffeine.newBuilder()
            .initialCapacity(10000) // 设置初始容量为 10000 个条目
            .maximumSize(10000) // 设置缓存的最大容量为 10000 个条目
            .expireAfterWrite(1, TimeUnit.HOURS) // 设置缓存条目在写入后 1 小时过期
            .build();
    @Autowired
    private NoteLikeDOMapper noteLikeDOMapper;

    /**
     * 发布笔记
     */
    @Override
    public Response<?> publishNote(PublishNoteReqVO publishNoteReqVO) {
        // 笔记类型
        Integer type = publishNoteReqVO.getType();

        // 获取对应类型的枚举
        NoteTypeEnum noteTypeEnum = NoteTypeEnum.valueOf(type);

        // 若非图文、视频，抛出业务业务异常
        if (Objects.isNull(noteTypeEnum)) {
            throw new BizException(ResponseCodeEnum.NOTE_TYPE_ERROR);
        }

        // 笔记内容是否为空，默认值为 true，即空
        Boolean isContentEmpty = true;
        String imgUris = null;
        String videoUri = null;
        switch (noteTypeEnum) {
            case IMAGE_TEXT:    // 图文笔记
                List<String> imgUriList = publishNoteReqVO.getImgUris();
                // 检验图片是否为空
                Preconditions.checkArgument(CollUtil.isNotEmpty(imgUriList), "笔记图片不能为空");
                // 检验图片数量
                Preconditions.checkArgument(imgUriList.size() <= 8, "笔记图片不能多于 8 张");
                // 将图片链接拼接，以逗号分隔
                imgUris = StringUtils.join(imgUriList, ",");
                break;
            case VIDEO: // 视频笔记
                videoUri = publishNoteReqVO.getVideoUri();
                // 检验视频是否为空
                Preconditions.checkArgument(StringUtils.isNotBlank(videoUri), "笔记视频不能为空");
                break;
            default:
                break;
        }

        // 调用分布式 ID 生成服务，生成笔记 ID
        String snowflakeIdId = distributedIdGeneratorRpcService.getSnowflakeId();
        // 话题（支持按话题名称关联，话题不存在时自动创建）
        // 先解析话题：话题非法时直接失败，避免先写正文后留下孤儿内容
        TopicDO topicDO = resolveTopic(publishNoteReqVO.getTopicId(), publishNoteReqVO.getTopicName());

        // 笔记内容 UUID
        String contentUuid = null;

        // 笔记内容
        String content = publishNoteReqVO.getContent();

        // 判断笔记内容是否为空
        if (StringUtils.isNotBlank(content)) {
            // 内容是否为空，置为 false，即不为空
            isContentEmpty = false;
            // 生成笔记内容的 UUID
            contentUuid = UUID.randomUUID().toString();
            // RPC: 调用 KV 键值服务，存储短文本
            boolean isSavedSuccess = keyValueRpcService.saveNoteContent(contentUuid, content);

            // 若存储失败，抛出业务异常，提示用户发布笔记失败
            if (!isSavedSuccess) {
                throw new BizException(ResponseCodeEnum.NOTE_PUBLISH_FAIL);
            }
        }

        // 发布者用户 ID
        Long creatorId = LoginUserContextHolder.getUserId();

        // 构建笔记 DO 对象
        NoteDO noteDO = NoteDO.builder()
                .id(Long.valueOf(snowflakeIdId))
                .isContentEmpty(isContentEmpty)
                .creatorId(creatorId)
                .imgUris(imgUris)
                .title(publishNoteReqVO.getTitle())
                .topicId(Objects.nonNull(topicDO) ? topicDO.getId() : null)
                .topicName(Objects.nonNull(topicDO) ? topicDO.getName() : null)
                .type(type)
                .visible(NoteVisibleEnum.PUBLIC.getCode())
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .status(NoteStatusEnum.NORMAL.getCode())
                .isTop(Boolean.FALSE)
                .videoUri(videoUri)
                .contentUuid(contentUuid)
                .build();

        try {
            // 笔记入库存储
            noteDOMapper.insert(noteDO);
        } catch (Exception e) {
            log.error("==> 笔记存储失败, noteId: {}", snowflakeIdId, e);

            // RPC: 笔记保存失败，则删除已写入的笔记内容，避免产生孤儿正文
            if (StringUtils.isNotBlank(contentUuid)) {
                keyValueRpcService.deleteNoteContent(contentUuid);
            }

            // 入库失败必须让调用方感知，不能吞掉异常后返回成功
            throw new BizException(ResponseCodeEnum.NOTE_PUBLISH_FAIL);
        }

        // 构建消息体 DTO
        NoteOperateMqDTO noteOperateMqDTO = NoteOperateMqDTO.builder()
                .noteId(Long.valueOf(snowflakeIdId))
                .creatorId(creatorId)
                .type(NoteOperateEnum.PUBLISH.getCode())    // 发布笔记
                .build();

        // 发货成功后再异步通知计数服务，MQ 失败不影响发布结果（可由数据对齐任务修正）
        threadPoolTaskExecutor.submit(() -> {
            try {
                rabbitTemplate.convertAndSend(MQConstants.COUNT_PUBLISH_NOTE_EXCHANGE,
                        MQConstants.COUNT_PUBLISH_NOTE_ROUTING_KEY, JsonUtils.toJsonString(noteOperateMqDTO));
                log.info("==> 【笔记发布】MQ 发送成功");
            } catch (Exception e) {
                log.error("==> 【笔记发布】MQ 发送异常: ", e);
            }
        });

        // 返回笔记 ID：前端发布后要跳详情页，且后续点赞/收藏都依赖它
        return Response.success(Long.valueOf(snowflakeIdId));
    }

    /**
     * 查询笔记详情
     */
    @Override
    @SneakyThrows
    public Response<FindNoteDetailRspVO> findNoteDetail(FindNoteDetailReqVO findNoteDetailReqVO) {
        // 查询的笔记 ID
        Long noteId = findNoteDetailReqVO.getId();

        // 当前登录用户
        Long userId = LoginUserContextHolder.getUserId();

        // 先从本地缓存中获取
        String findNoteDetailRspVOStrLocalCache = LOCAL_CACHE.getIfPresent(noteId);
        if (StringUtils.isNotBlank(findNoteDetailRspVOStrLocalCache)) {
            FindNoteDetailRspVO findNoteDetailRspVO = JsonUtils.parseObject(findNoteDetailRspVOStrLocalCache, FindNoteDetailRspVO.class);
            if (Objects.nonNull(findNoteDetailRspVO)) {
                log.info("==> 命中了本地缓存；{}", findNoteDetailRspVO);
                // 可见性校验
                checkNoteVisibleFromVO(userId, findNoteDetailRspVO);
                return Response.success(findNoteDetailRspVO);
            }
            // 本地缓存里是 "null" 空值标记，清掉后继续走 Redis / DB，避免把空值当成正常数据返回
            LOCAL_CACHE.invalidate(noteId);
        }

        // 从 Redis 缓存中获取
        String noteDetailRedisKey = RedisKeyConstants.buildNoteDetailKey(noteId);
        String noteDetailJson = (String) redisTemplate.opsForValue().get(noteDetailRedisKey);

        // 若缓存中有该笔记的数据，则直接返回
        if (StringUtils.isNotBlank(noteDetailJson)) {
            FindNoteDetailRspVO findNoteDetailRspVO = JsonUtils.parseObject(noteDetailJson, FindNoteDetailRspVO.class);

            // 命中空值缓存（"null"）时，说明该笔记此前确实不存在，保持与数据库一致的语义返回业务异常，
            // 而不是 Response.success(null)
            if (Objects.isNull(findNoteDetailRspVO)) {
                throw new BizException(ResponseCodeEnum.NOTE_NOT_FOUND);
            }

            // 异步线程中将用户信息存入本地缓存
            threadPoolTaskExecutor.submit(() -> {
                // 写入本地缓存
                LOCAL_CACHE.put(noteId, noteDetailJson);
            });
            // 可见性校验
            checkNoteVisibleFromVO(userId, findNoteDetailRspVO);

            return Response.success(findNoteDetailRspVO);
        }

        // 若 Redis 缓存中获取不到，则走数据库查询
        // 查询笔记
        NoteDO noteDO = noteDOMapper.selectByPrimaryKey(noteId);

        // 若该笔记不存在，则抛出业务异常
        if (Objects.isNull(noteDO)) {
            threadPoolTaskExecutor.submit(() -> {
                // 防止缓存穿透，将空数据存入 Redis 缓存 (过期时间不宜设置过长)
                // 保底1分钟 + 随机秒数
                long timeToLive = 60 + RandomUtil.randomInt(60);
                redisTemplate.opsForValue().set(noteDetailRedisKey, "null", timeToLive, TimeUnit.SECONDS);
            });
            throw new BizException(ResponseCodeEnum.NOTE_NOT_FOUND);
        }

        // 可见性校验
        Integer visible = noteDO.getVisible();
        Long creatorId = noteDO.getCreatorId();
        checkNoteVisible(visible, userId, creatorId);

        // 并发查询优化
        // RPC: 调用用户服务
        CompletableFuture<FindUserByIdRspDTO> userResultFuture = CompletableFuture
                .supplyAsync(() -> userRpcService.findById(creatorId), threadPoolTaskExecutor);

        // RPC: 调用 K-V 存储服务获取内容
        CompletableFuture<String> contentResultFuture = CompletableFuture.completedFuture(null);
        if (Objects.equals(noteDO.getIsContentEmpty(), Boolean.FALSE)) {
            contentResultFuture = CompletableFuture
                    .supplyAsync(() -> {
                        try {
                            return keyValueRpcService.findNoteContent(noteDO.getContentUuid());
                        } catch (Exception e) {
                            // kv 抖动不该让整个详情接口失败；这里吞掉异常返回 null，
                            // 由下面「正文缺失不写缓存」的逻辑兜底，避免脏缓存
                            log.warn("==> 获取笔记正文失败, noteId: {}, contentUuid: {}, error: {}",
                                    noteId, noteDO.getContentUuid(), e.getMessage());
                            return null;
                        }
                    }, threadPoolTaskExecutor);
        }

        CompletableFuture<String> finalContentResultFuture = contentResultFuture;
        CompletableFuture<FindNoteDetailRspVO> resultFuture = CompletableFuture
                .allOf(userResultFuture, contentResultFuture)
                .thenApply(s -> {
                    // 获取 Future 返回的结果
                    FindUserByIdRspDTO findUserByIdRspDTO = userResultFuture.join();
                    String content = finalContentResultFuture.join();

                    // 笔记类型
                    Integer type = noteDO.getType();
                    // 图文笔记图片链接(字符串)
                    String imgUriStr = noteDO.getImgUris();
                    // 图文笔记图片链接(集合)
                    List<String> imgUris = null;
                    // 如果查询的是图文笔记，需要将图片链接的逗号分隔开，转换成集合
                    if (Objects.equals(type, NoteTypeEnum.IMAGE_TEXT.getCode())
                            && StringUtils.isNotBlank(imgUriStr)) {
                        imgUris = Arrays.asList(imgUriStr.split(","));
                    }

                    // 构建返参 VO 实体类
                    return FindNoteDetailRspVO.builder()
                            .id(noteId)
                            .type(type)
                            .title(noteDO.getTitle())
                            .content(content)
                            .imgUris(imgUris)
                            .topicId(noteDO.getTopicId())
                            .topicName(noteDO.getTopicName())
                            .creatorId(creatorId)
                            .creatorName(Objects.nonNull(findUserByIdRspDTO) ? findUserByIdRspDTO.getNickname() : null)
                            .avatar(Objects.nonNull(findUserByIdRspDTO) ? findUserByIdRspDTO.getAvatar() : null)
                            .videoUri(noteDO.getVideoUri())
                            .updateTime(noteDO.getUpdateTime())
                            .visible(visible)
                            .build();
                });

        // 获取拼装后的 FindNoteDetailRspVO
        FindNoteDetailRspVO findNoteDetailRspVO = resultFuture.get();

        // 笔记本该有正文（有 contentUuid）但这次没取到，说明是 kv 调用抖动。
        // 这种情况不能写缓存，否则 TTL（最长 2 天）内用户一直看不到正文。
        boolean contentMissing = Objects.equals(noteDO.getIsContentEmpty(), Boolean.FALSE)
                && StringUtils.isNotBlank(noteDO.getContentUuid())
                && StringUtils.isBlank(findNoteDetailRspVO.getContent());

        if (contentMissing) {
            log.warn("==> 正文缺失，跳过本次详情缓存, noteId: {}, contentUuid: {}",
                    noteId, noteDO.getContentUuid());
        } else {
            // 异步线程中将笔记详情存入 Redis
            threadPoolTaskExecutor.submit(() -> {
                // 过期时间（保底1天 + 随机秒数，将缓存过期时间打散，防止同一时间大量缓存失效，导致数据库压力太大）
                long timeToLive = 60 * 60 * 24 + RandomUtil.randomInt(60 * 60 * 24);
                redisTemplate.opsForValue().set(noteDetailRedisKey,
                        JsonUtils.toJsonString(findNoteDetailRspVO), timeToLive, TimeUnit.SECONDS);
            });
        }

        return Response.success(findNoteDetailRspVO);
    }

    /**
     * 更新笔记
     */
    @Override
    public Response<?> updateNote(UpdateNoteReqVO updateNoteReqVO) {
        Long noteId = updateNoteReqVO.getId();
        Long userId = LoginUserContextHolder.getUserId();

        // 校验笔记所有者
        NoteDO noteDO = getNoteAndCheckOwner(noteId, userId);

        // 获取笔记的类型
        Integer type = updateNoteReqVO.getType();
        NoteTypeEnum noteTypeEnum = NoteTypeEnum.valueOf(type);
        // 判断笔记类型是否合法
        if (Objects.isNull(noteTypeEnum)) {
            throw new BizException(ResponseCodeEnum.NOTE_TYPE_ERROR);
        }

        /* ------------------------------- 图片链接/视频链接 ------------------------------- */
        // 根据不同的笔记类型，进行不同的校验
        String imgUris = null;
        String videoUri = null;
        switch (noteTypeEnum) {
            case IMAGE_TEXT:    // 图文笔记
                List<String> imgUriList = updateNoteReqVO.getImgUris();
                // 检验图片是否为空
                Preconditions.checkArgument(CollUtil.isNotEmpty(imgUriList), "笔记图片不能为空");
                // 检验图片数量
                Preconditions.checkArgument(imgUriList.size() <= 8, "笔记图片不能多于 8 张");
                // 将图片链接拼接，以逗号分隔
                imgUris = StringUtils.join(imgUriList, ",");
                break;
            case VIDEO: // 视频笔记
                videoUri = updateNoteReqVO.getVideoUri();
                // 检验视频是否为空
                Preconditions.checkArgument(StringUtils.isNotBlank(videoUri), "笔记视频不能为空");
                break;
            default:
                break;
        }

        /* ------------------------------- 话题名称 ------------------------------- */
        // 获取笔记的话题（支持按话题名称关联，话题不存在时自动创建）
        TopicDO topicDO = resolveTopic(updateNoteReqVO.getTopicId(), updateNoteReqVO.getTopicName());
        Long topicId = Objects.nonNull(topicDO) ? topicDO.getId() : null;
        String topicName = Objects.nonNull(topicDO) ? topicDO.getName() : null;

        // 删除 Redis 缓存
        try {
            String noteDetailRedisKey = RedisKeyConstants.buildNoteDetailKey(noteId);
            redisTemplate.delete(noteDetailRedisKey);
        } catch (Exception e) {
            log.error("删除 Redis 缓存失败, noteId: {}", noteId, e);
        }

        /* ------------------------------- 笔记内容 ------------------------------- */
        // 校验新笔记内容是否为空
        String content = updateNoteReqVO.getContent();
        boolean isContentEmpty = !StringUtils.isNotBlank(content);
        String contentUuid = null;
        if (!isContentEmpty) {
            // 生成新的 UUID
            contentUuid = UUID.randomUUID().toString();
            // 调用 Cassandra 服务存储笔记内容
            boolean saveSuccess = keyValueRpcService.saveNoteContent(contentUuid, content);
            if (!saveSuccess) {
                throw new BizException(ResponseCodeEnum.NOTE_CONTENT_SAVE_FAIL);
            }
        }

        // 构建 NoteDO
        NoteDO newNoteDO = NoteDO.builder()
                .id(noteId)
                .type(type)
                .imgUris(imgUris)
                .videoUri(videoUri)
                .title(updateNoteReqVO.getTitle())
                .topicId(topicId)
                .contentUuid(contentUuid)
                .topicName(topicName)
                .isContentEmpty(isContentEmpty)
                .updateTime(LocalDateTime.now())
                .build();

        try {
            // 更新笔记
            noteDOMapper.updateByPrimaryKey(newNoteDO);
        } catch (Exception e) {
            log.error("==> 笔记更新失败", e);

            // RPC: 笔记保存失败，则删除笔记内容
            if (StringUtils.isNotBlank(content)) {
                keyValueRpcService.deleteNoteContent(contentUuid);
            }
            throw new BizException(ResponseCodeEnum.NOTE_UPDATE_FAIL);
        }

        // 异步刷新本地缓存和 Redis 缓存
        threadPoolTaskExecutor.submit(() -> {
            try {
                // 如果原笔记内容不为空，则删除旧内容
                if (!noteDO.getIsContentEmpty()) {
                    keyValueRpcService.deleteNoteContent(noteDO.getContentUuid());
                }
            } catch (Exception e) {
                log.error("## 删除旧笔记内容失败, noteId: {}", noteId, e);
            }
            // 发送缓存失效消息
            sendLocalCacheInvalidateMessage(noteId);
            sendRedisCacheInvalidateMessage(noteId);
        });

        return Response.success();
    }

    /**
     * 删除笔记
     */
    @Override
    public Response<?> deleteNote(DeleteNoteReqVO deleteNoteReqVO) {
        Long noteId = deleteNoteReqVO.getNoteId();
        Long userId = LoginUserContextHolder.getUserId();

        // 校验笔记所有者
        NoteDO noteDO = getNoteAndCheckOwner(noteId, userId);

        // 构建 NoteDO
        NoteDO deletedNoteDO = NoteDO.builder()
                .id(noteId)
                .status(NoteStatusEnum.DELETED.getCode())
                .updateTime(LocalDateTime.now())
                .build();

        // 逻辑删除笔记
        noteDOMapper.updateByPrimaryKeySelective(deletedNoteDO);

        threadPoolTaskExecutor.submit(() -> {
            try {
                // 如果笔记内容不为空，则删除笔记内容
                if (!noteDO.getIsContentEmpty()) {
                    keyValueRpcService.deleteNoteContent(noteDO.getContentUuid());
                }
            } catch (Exception e) {
                log.error("## 删除笔记内容失败, noteId: {}", noteId, e);
            }
            // 发送缓存失效消息
            sendLocalCacheInvalidateMessage(noteId);
            sendRedisCacheInvalidateMessage(noteId);

            try {
                // 构建消息体 DTO
                NoteOperateMqDTO noteOperateMqDTO = NoteOperateMqDTO.builder()
                        .noteId(noteId)
                        .creatorId(noteDO.getCreatorId())
                        .type(NoteOperateEnum.DELETE.getCode())    // 删除笔记
                        .build();

                // 发送消息到 MQ， 通知计数服务更改笔记数统计
                rabbitTemplate.convertAndSend(MQConstants.COUNT_PUBLISH_NOTE_EXCHANGE, MQConstants.COUNT_PUBLISH_NOTE_ROUTING_KEY,
                        JsonUtils.toJsonString(noteOperateMqDTO));
                log.info("==> 【笔记删除】MQ 发送成功");
            } catch (Exception e) {
                log.error("==> 【笔记删除】MQ 发送异常: ", e);
            }
        });

        return Response.success();
    }

    /**
     * 笔记仅对自己可见
     */
    @Override
    public Response<?> visibleOnlyMe(UpdateNoteVisibleOnlyMeReqVO updateNoteVisibleOnlyMeReqVO) {
        Long noteId = updateNoteVisibleOnlyMeReqVO.getId();
        Long userId = LoginUserContextHolder.getUserId();

        // 校验笔记所有者
        getNoteAndCheckOwner(noteId, userId);

        // 构建更新 DO 实体类
        NoteDO noteDO = NoteDO.builder()
                .id(noteId)
                .visible(NoteVisibleEnum.PRIVATE.getCode())
                .updateTime(LocalDateTime.now())
                .build();

        // 执行更新 SQL
        int result = noteDOMapper.updateVisible(noteDO);

        // 如果影响行数为0，说明该笔记无法修改为仅自己可见
        if (result == 0) {
            throw new BizException(ResponseCodeEnum.NOTE_CANT_VISIBLE_ONLY_ME);
        }

        // 异步向 MQ 发送删除缓存的消息
        threadPoolTaskExecutor.submit(() -> {
            sendLocalCacheInvalidateMessage(noteId);
            sendRedisCacheInvalidateMessage(noteId);
        });

        return Response.success();
    }

    /**
     * 笔记设为公开可见（从私密恢复）
     */
    @Override
    public Response<?> visiblePublic(UpdateNoteVisiblePublicReqVO updateNoteVisiblePublicReqVO) {
        Long noteId = updateNoteVisiblePublicReqVO.getId();
        Long userId = LoginUserContextHolder.getUserId();

        // 校验笔记所有者
        getNoteAndCheckOwner(noteId, userId);

        // 构建更新 DO 实体类
        NoteDO noteDO = NoteDO.builder()
                .id(noteId)
                .visible(NoteVisibleEnum.PUBLIC.getCode())
                .updateTime(LocalDateTime.now())
                .build();

        // 执行更新 SQL
        int result = noteDOMapper.updateVisible(noteDO);

        // 如果影响行数为 0，说明该笔记无法恢复为公开
        if (result == 0) {
            throw new BizException(ResponseCodeEnum.NOTE_CANT_VISIBLE_PUBLIC);
        }

        // 异步删除详情缓存（恢复公开后，ES 索引由 Canal 监听 t_note 更新自动重新同步）
        threadPoolTaskExecutor.submit(() -> {
            sendLocalCacheInvalidateMessage(noteId);
            sendRedisCacheInvalidateMessage(noteId);
        });

        return Response.success();
    }

    /**
     * 笔记置顶/取消置顶
     */
    @Override
    public Response<?> topNote(TopNoteReqVO topNoteReqVO) {
        Long noteId = topNoteReqVO.getId();
        Long userId = LoginUserContextHolder.getUserId();

        // 校验笔记所有者
        getNoteAndCheckOwner(noteId, userId);

        // 构建置顶/取消置顶 DO 实体类
        NoteDO noteDO = NoteDO.builder()
                .id(noteId)
                .isTop(topNoteReqVO.getIsTop())
                .updateTime(LocalDateTime.now())
                .build();

        // 执行更新 SQL
        int result = noteDOMapper.updateIsTop(noteDO);

        // 如果影响行数为0，说明该笔记无法置顶/取消置顶
        if (result == 0) {
            throw new BizException(ResponseCodeEnum.NOTE_CANT_TOP);
        }

        // 异步向 MQ 发送删除缓存的消息
        threadPoolTaskExecutor.submit(() -> {
            sendLocalCacheInvalidateMessage(noteId);
            sendRedisCacheInvalidateMessage(noteId);
        });

        return Response.success();
    }

    /**
     * 点赞笔记
     */
    @Override
    public Response<?> likeNote(LikeNoteReqVO likeNoteReqVO) {
        // 笔记 ID
        Long noteId = likeNoteReqVO.getId();

        // 当前登录用户ID
        Long userId = LoginUserContextHolder.getUserId();

        // 1. 校验被点赞的笔记是否存在且对当前用户可见，若存在，则获取发布者用户 ID
        Long creatorId = checkNoteIsExistAndGetCreatorId(noteId, userId);

        // 2. 判断目标笔记，是否已经点赞过

        // 布隆过滤器 Key
        String bloomUserNoteLikeListKey = RedisKeyConstants.buildBloomUserNoteLikeListKey(userId);

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/bloom_note_like_check.lua")));
        script.setResultType(Long.class);

        // 执行 Lua 脚本，拿到返回结果
        Long result = redisTemplate.execute(script, List.of(bloomUserNoteLikeListKey), noteId);

        NoteLikeLuaResultEnum noteLikeLuaResultEnum = NoteLikeLuaResultEnum.valueOf(result);

        // 用户点赞列表 ZSet Key
        String userNoteLikeZSetKey = RedisKeyConstants.buildUserNoteLikeZSetKey(userId);

        switch (noteLikeLuaResultEnum) {
            // Redis 中布隆过滤器不存在
            case NOT_EXIST:
                // 从数据库中校验笔记是否被点赞，并异步初始化布隆过滤器，设置过期时间
                int count = noteLikeDOMapper.selectCountByUserIdAndNoteId(userId, noteId);

                // 保底1天+随机秒数
                long expireSeconds = 60 * 60 * 24 + RandomUtil.randomInt(60 * 60 * 24);

                // 目标笔记已经被点赞
                if (count > 0) {
                    // 异步初始化布隆过滤器
                    threadPoolTaskExecutor.submit(() -> {
                        batchAddNoteLike2BloomAndExpire(userId, expireSeconds, bloomUserNoteLikeListKey);
                    });
                    throw new BizException(ResponseCodeEnum.NOTE_ALREADY_LIKED);
                }

                // 若目标笔记未被点赞，查询当前用户是否有点赞其他笔记，有则同步初始化布隆过滤器
                batchAddNoteLike2BloomAndExpire(userId, expireSeconds, bloomUserNoteLikeListKey);

                // 添加当前点赞笔记 ID 到布隆过滤器中
                script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/bloom_add_note_like_and_expire.lua")));
                script.setResultType(Long.class);
                redisTemplate.execute(script, List.of(bloomUserNoteLikeListKey), noteId, expireSeconds);
                break;
            // 目标笔记已经被点赞(可能存在误判，需要进一步确认)
            case NOTE_LIKED:
                // 校验 ZSet 列表中是否包含被点赞的笔记ID
                Double score = redisTemplate.opsForZSet().score(userNoteLikeZSetKey, noteId);

                if (score != null && score != 0.0) {
                    throw new BizException(ResponseCodeEnum.NOTE_ALREADY_LIKED);
                }

                // 若 Score 为空，则表示 ZSet 点赞列表中不存在，查询数据库校验
                int isLiked = noteLikeDOMapper.selectNoteIsLiked(userId, noteId);

                if (isLiked > 0) {
                    // 数据库里面有点赞记录，而 Redis 中 ZSet 不存在，需要重新异步初始化 ZSet
                    asyncInitUserNoteLikesZSet(userId, userNoteLikeZSetKey);

                    throw new BizException(ResponseCodeEnum.NOTE_ALREADY_LIKED);
                }
        }

        // 3. 更新用户 ZSET 点赞列表
        LocalDateTime now = LocalDateTime.now();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/note_like_check_and_update_zset.lua")));
        script.setResultType(Long.class);

        // 执行 Lua 脚本，拿到返回结果
        result = redisTemplate.execute(script, List.of(userNoteLikeZSetKey), noteId,
                DateUtils.localDateTime2Timestamp(now));

        // 若 ZSet 列表不存在，需要重新初始化
        if (Objects.equals(result, NoteLikeLuaResultEnum.NOT_EXIST.getCode())) {
            // 从数据库查询用户最新点赞的 100 条笔记
            List<NoteLikeDO> noteLikeDOS = noteLikeDOMapper.selectLikedByUserIdAndLimit(userId, 100);

            // 保底1天+随机秒数
            long expireSeconds = 60 * 60 * 24 + RandomUtil.randomInt(60 * 60 * 24);

            DefaultRedisScript<Long> script2 = new DefaultRedisScript<>();
            script2.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/batch_add_note_like_zset_and_expire.lua")));
            script2.setResultType(Long.class);

            // 若数据库中存在点赞记录，需要批量同步
            if (CollUtil.isNotEmpty(noteLikeDOS)) {
                // 构建 Lua 脚本参数
                Object[] luaArgs = buildZSetLuaArgs(noteLikeDOS, NoteLikeDO::getCreateTime,
                        NoteLikeDO::getNoteId, expireSeconds);

                redisTemplate.execute(script2, List.of(userNoteLikeZSetKey), luaArgs);

                // 再次调用 note_like_check_and_update_zset.lua 脚本，将点赞的笔记添加到 zset 中
                redisTemplate.opsForZSet().add(userNoteLikeZSetKey, noteId, DateUtils.localDateTime2Timestamp(now));
            } else {  // 若数据库中，无点赞过的笔记记录，则直接将当前点赞的笔记 ID 添加到 ZSet 中，随机过期时间
                List<Object> luaArgs = Lists.newArrayList();
                luaArgs.add(DateUtils.localDateTime2Timestamp(now)); // score ：点赞时间戳
                luaArgs.add(noteId); // 当前点赞的笔记 ID
                luaArgs.add(expireSeconds); // 随机过期时间

                redisTemplate.execute(script2, List.of(userNoteLikeZSetKey), luaArgs.toArray());
            }
        }

        // 4. 发送 MQ, 将点赞数据落库
        // 构建消息体 DTO
        LikeUnlikeNoteMqDTO likeUnlikeNoteMqDTO = LikeUnlikeNoteMqDTO.builder()
                .userId(userId)
                .noteId(noteId)
                .type(LikeUnlikeNoteTypeEnum.LIKE.getCode())  // 点赞笔记
                .createTime(now)
                .noteCreatorId(creatorId)   // 笔记发布者 ID
                .build();

        // 异步发送 MQ 消息，提升接口响应速度
        threadPoolTaskExecutor.submit(() -> {
            try {
                rabbitTemplate.convertAndSend(MQConstants.LIKE_UNLIKE_NOTE_TO_DB_EXCHANGE,
                        MQConstants.LIKE_UNLIKE_NOTE_TO_DB_ROUTING_KEY, likeUnlikeNoteMqDTO);
                log.info("==> 【笔记点赞】MQ 发送成功");
            } catch (AmqpException e) {
                log.error("==> 【笔记点赞】MQ 发送异常: ", e);
            }
        });

        return Response.success();
    }

    /**
     * 取消点赞笔记
     */
    @Override
    public Response<?> unlikeNote(UnlikeNoteReqVO unlikeNoteReqVO) {
        Long noteId = unlikeNoteReqVO.getId();

        // 当前登录用户 ID
        Long userId = LoginUserContextHolder.getUserId();

        // 1. 校验笔记是否真实存在且对当前用户可见，若存在，则获取发布者用户 ID
        Long creatorId = checkNoteIsExistAndGetCreatorId(noteId, userId);

        // 2. 校验笔记是否被点赞过

        // 布隆过滤器 Key
        String bloomUserNoteLikeListKey = RedisKeyConstants.buildBloomUserNoteLikeListKey(userId);

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        // Lua 脚本路径
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/bloom_note_unlike_check.lua")));
        // 返回值类型
        script.setResultType(Long.class);

        // 执行 Lua 脚本，拿到返回结果
        Long result = redisTemplate.execute(script, List.of(bloomUserNoteLikeListKey), noteId);

        NoteUnlikeLuaResultEnum noteUnlikeLuaResultEnum = NoteUnlikeLuaResultEnum.valueOf(result);

        // 判断返回结果
        switch (noteUnlikeLuaResultEnum) {
            // 布隆过滤器不存在
            case NOT_EXIST:
                // 异步初始化布隆过滤器
                threadPoolTaskExecutor.submit(() -> {
                    // 保底1天+随机秒数
                    long expireSeconds = 60 * 60 * 24 + RandomUtil.randomInt(60 * 60 * 24);
                    batchAddNoteLike2BloomAndExpire(userId, expireSeconds, bloomUserNoteLikeListKey);
                });

                // 查询该笔记是否被点赞过
                int count = noteLikeDOMapper.selectCountByUserIdAndNoteId(userId, noteId);

                // 若数据库中不存在点赞记录，说明用户没有点赞过该笔记，直接抛出异常
                if (count == 0) {
                    throw new BizException(ResponseCodeEnum.NOTE_NOT_LIKED);
                }

                break;
            // 布隆过滤器校验目标笔记未被点赞（判断绝对正确）
            case NOTE_NOT_LIKED:
                throw new BizException(ResponseCodeEnum.NOTE_NOT_LIKED);
        }

        // 3. 删除 ZSET 中已点赞的笔记 ID
        String userNoteLikeZSetKey = RedisKeyConstants.buildUserNoteLikeZSetKey(userId);
        redisTemplate.opsForZSet().remove(userNoteLikeZSetKey, noteId);

        // 4. 发送 MQ, 数据更新落库
        // 构建消息体 DTO
        LikeUnlikeNoteMqDTO likeUnlikeNoteMqDTO = LikeUnlikeNoteMqDTO.builder()
                .userId(userId)
                .noteId(noteId)
                .type(LikeUnlikeNoteTypeEnum.UNLIKE.getCode())  // 取消点赞笔记
                .createTime(LocalDateTime.now())
                .noteCreatorId(creatorId)   // 笔记发布者 ID
                .build();

        // 异步发送 MQ 消息，提升接口响应速度
        threadPoolTaskExecutor.submit(() -> {
            try {
                rabbitTemplate.convertAndSend(MQConstants.LIKE_UNLIKE_NOTE_TO_DB_EXCHANGE,
                        MQConstants.LIKE_UNLIKE_NOTE_TO_DB_ROUTING_KEY, likeUnlikeNoteMqDTO);
                log.info("==> 【笔记取消点赞】MQ 发送成功");
            } catch (AmqpException e) {
                log.error("==> 【笔记取消点赞】MQ 发送异常: ", e);
            }
        });

        return Response.success();
    }

    /**
     * 收藏笔记
     */
    @Override
    public Response<?> collectNote(CollectNoteReqVO collectNoteReqVO) {
        // 笔记 ID
        Long noteId = collectNoteReqVO.getId();
        // 当前登录用户 ID
        Long userId = LoginUserContextHolder.getUserId();

        // 1. 校验笔记是否真实存在且对当前用户可见，若存在，则获取发布者用户 ID
        Long creatorId = checkNoteIsExistAndGetCreatorId(noteId, userId);

        // 2. 判断目标笔记，是否已经收藏过
        // 构建布隆过滤器 Key
        String bloomUserNoteCollectListKey = RedisKeyConstants.buildBloomUserNoteCollectListKey(userId);
        // 构建用户收藏笔记 ZSET Key
        String userNoteCollectZSetKey = RedisKeyConstants.buildUserNoteCollectZSetKey(userId);

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        // Lua 脚本路径
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/bloom_note_collect_check.lua")));
        // 返回值类型
        script.setResultType(Long.class);

        // 执行 Lua 脚本，拿到返回结果
        Long result = redisTemplate.execute(script, List.of(bloomUserNoteCollectListKey), noteId);

        // 判断返回结果
        NoteCollectLuaResultEnum noteCollectLuaResultEnum = NoteCollectLuaResultEnum.valueOf(result);

        switch (noteCollectLuaResultEnum) {
            // 布隆过滤器不存在
            case NOT_EXIST -> {
                // 从数据库中校验笔记是否被收藏，并异步初始化布隆过滤器，设置过期时间
                int count = noteCollectionDOMapper.selectNoteIsCollected(userId, noteId);

                // 保底1天+随机秒数
                long expireSeconds = 60 * 60 * 24 + RandomUtil.randomInt(60 * 60 * 24);

                // 目标笔记已经被收藏
                if (count > 0) {
                    // 异步初始化布隆过滤器
                    threadPoolTaskExecutor.submit(() -> {
                        batchAddNoteCollect2BloomAndExpire(userId, expireSeconds, bloomUserNoteCollectListKey);
                    });
                    throw new BizException(ResponseCodeEnum.NOTE_ALREADY_COLLECTED);
                }

                // 若目标笔记未被收藏，查询当前用户是否有收藏其他笔记，有则同步初始化布隆过滤器
                batchAddNoteCollect2BloomAndExpire(userId, expireSeconds, bloomUserNoteCollectListKey);

                // 添加当前收藏笔记 ID 到布隆过滤器中
                script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/bloom_add_note_collect_and_expire.lua")));
                script.setResultType(Long.class);
                redisTemplate.execute(script, List.of(bloomUserNoteCollectListKey), noteId, expireSeconds);
            }
            // 目标笔记已经被收藏（可能存在误判，需要进一步确认）
            case NOTE_COLLECTED -> {
                // 校验 ZSet 列表中是否包含被收藏的笔记ID
                Double score = redisTemplate.opsForZSet().score(userNoteCollectZSetKey, noteId);

                // 如果 ZSET 中包含该笔记，说明用户已经收藏过该笔记，直接抛出异常
                if (score != null && score != 0.0) {
                    throw new BizException(ResponseCodeEnum.NOTE_ALREADY_COLLECTED);
                }

                // 若 Score 为空，则表示 ZSet 收藏列表中不存在，查询数据库校验
                int count = noteCollectionDOMapper.selectNoteIsCollected(userId, noteId);

                // 如果 count 大于 0，说明用户已经收藏过该笔记，直接抛出异常
                if (count > 0) {
                    // 如果数据库中存在记录，而 ZSET 中没有，说明 ZSET 过期了，需要异步初始化 ZSET
                    asyncInitUserNoteCollectZSet(userId, userNoteCollectZSetKey);

                    throw new BizException(ResponseCodeEnum.NOTE_ALREADY_COLLECTED);
                }
            }
        }

        // 3. 更新用户 ZSET 收藏列表
        LocalDateTime now = LocalDateTime.now();
        long timestamp = DateUtils.localDateTime2Timestamp(now);

        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/note_collect_check_and_update_zset.lua")));
        script.setResultType(Long.class);

        // 执行 Lua 脚本，拿到返回结果
        result = redisTemplate.execute(script, List.of(userNoteCollectZSetKey), noteId, timestamp);

        // 如果 ZSET 不存在，则初始化 ZSET
        if (Objects.equals(result, NoteCollectLuaResultEnum.NOT_EXIST.getCode())) {
            // 查询用户最新收藏的 300 条笔记
            List<NoteCollectionDO> noteCollectionDOS = noteCollectionDOMapper.selectCollectedByUserIdAndLimit(userId, 300);

            // 保底1天+随机秒数
            long expireSeconds = 60 * 60 * 24 + RandomUtil.randomInt(60 * 60 * 24);

            DefaultRedisScript<Long> script2 = new DefaultRedisScript<>();
            script2.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/batch_add_note_collect_zset_and_expire.lua")));
            script2.setResultType(Long.class);

            // 若数据库中存在历史收藏笔记，需要批量同步
            if (CollUtil.isNotEmpty(noteCollectionDOS)) {
                // 构造 Lua 参数
                Object[] luaArgs = buildZSetLuaArgs(noteCollectionDOS, NoteCollectionDO::getCreateTime,
                        NoteCollectionDO::getNoteId, expireSeconds);

                redisTemplate.execute(script2, List.of(userNoteCollectZSetKey), luaArgs);

                // ZSET 同步完成后，将当前笔记 ID 也添加到 ZSET 中
                redisTemplate.execute(script, List.of(userNoteCollectZSetKey), noteId, timestamp);
            } else {  // 若无历史收藏的笔记，则直接将当前收藏的笔记 ID 添加到 ZSet 中，随机过期时间
                List<Object> luaArgs = Lists.newArrayList();
                luaArgs.add(DateUtils.localDateTime2Timestamp(LocalDateTime.now())); // score：收藏时间戳
                luaArgs.add(noteId); // 当前收藏的笔记 ID
                luaArgs.add(expireSeconds); // 随机过期时间

                redisTemplate.execute(script2, List.of(userNoteCollectZSetKey), luaArgs.toArray());
            }
        }

        // 4. 发送 MQ, 数据更新落库
        // 构建 MQ 消息
        CollectUnCollectNoteMqDTO collectUnCollectNoteMqDTO = CollectUnCollectNoteMqDTO.builder()
                .userId(userId)
                .noteId(noteId)
                .createTime(now)
                .type(CollectUnCollectNoteTypeEnum.COLLECT.getCode())   // 收藏笔记，提升接口响应速度
                .noteCreatorId(creatorId)   // 笔记发布者 ID
                .build();

        // 异步发送 MQ 消息
        threadPoolTaskExecutor.submit(() -> {
            try {
                rabbitTemplate.convertAndSend(MQConstants.COLLECT_UNCOLLECTED_NOTE_TO_DB_EXCHANGE,
                        MQConstants.COLLECT_UNCOLLECTED_NOTE_TO_DB_ROUTING_KEY, collectUnCollectNoteMqDTO);
                log.info("==> 【笔记收藏】MQ 发送成功");
            } catch (AmqpException e) {
                log.error("==> 【笔记收藏】MQ 发送异常: ", e);
            }
        });

        return Response.success();
    }

    /**
     * 取消收藏笔记
     */
    @Override
    public Response<?> unCollectNote(UnCollectNoteReqVO unCollectNoteReqVO) {
        // 笔记 ID
        Long noteId = unCollectNoteReqVO.getId();
        // 当前登录用户 ID
        Long userId = LoginUserContextHolder.getUserId();

        // 1. 校验笔记是否真实存在且对当前用户可见，若存在，则获取发布者用户 ID
        Long creatorId = checkNoteIsExistAndGetCreatorId(noteId, userId);

        // 2. 校验笔记是否被收藏过
        // 构建用户笔记收藏布隆过滤器 Key
        String bloomUserNoteCollectListKey = RedisKeyConstants.buildBloomUserNoteCollectListKey(userId);

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/bloom_note_uncollect_check.lua")));
        script.setResultType(Long.class);

        // 执行 Lua 脚本，拿到返回结果
        long result = redisTemplate.execute(script, List.of(bloomUserNoteCollectListKey), noteId);

        NoteUnCollectLuaResultEnum noteUnCollectLuaResultEnum = NoteUnCollectLuaResultEnum.valueOf(result);
        // 对结果进行判断
        switch (noteUnCollectLuaResultEnum) {
            // 布隆过滤器不存在
            case NOT_EXIST -> {
                // 异步初始化布隆过滤器
                threadPoolTaskExecutor.submit(() -> {
                    // 保底1天+随机秒数
                    long expireSeconds = 60 * 60 * 24 + RandomUtil.randomInt(60 * 60 * 24);
                    batchAddNoteCollect2BloomAndExpire(userId, expireSeconds, bloomUserNoteCollectListKey);
                });

                // 从数据库中校验笔记是否被收藏
                int count = noteCollectionDOMapper.selectNoteIsCollected(userId, noteId);

                // 未收藏，无法取消收藏操作，抛出业务异常
                if (count == 0) throw new BizException(ResponseCodeEnum.NOTE_NOT_COLLECTED);
            }
            // 笔记未收藏
            case NOTE_NOT_COLLECTED -> {  // 布隆过滤器对于否定的判断，不存在误判
                throw new BizException(ResponseCodeEnum.NOTE_NOT_COLLECTED);
            }
        }

        // 3. 删除 ZSET 中已收藏的笔记 ID
        // 构建用户笔记收藏 ZSET Key
        String userNoteCollectZSetKey = RedisKeyConstants.buildUserNoteCollectZSetKey(userId);
        redisTemplate.opsForZSet().remove(userNoteCollectZSetKey, noteId);

        // 4. 发送 MQ, 数据更新落库
        LocalDateTime now = LocalDateTime.now();

        // 构建消息体 DTO
        CollectUnCollectNoteMqDTO collectUnCollectNoteMqDTO = CollectUnCollectNoteMqDTO.builder()
                .userId(userId)
                .noteId(noteId)
                .createTime(now)
                .type(CollectUnCollectNoteTypeEnum.UN_COLLECT.getCode())   // 取消收藏笔记
                .noteCreatorId(creatorId)   // 笔记发布者 ID
                .build();

        // 异步发送顺序 MQ 消息，提升接口响应速度
        threadPoolTaskExecutor.submit(() -> {
            try {
                rabbitTemplate.convertAndSend(MQConstants.COLLECT_UNCOLLECTED_NOTE_TO_DB_EXCHANGE,
                        MQConstants.COLLECT_UNCOLLECTED_NOTE_TO_DB_ROUTING_KEY, collectUnCollectNoteMqDTO);
                log.info("==> 【笔记取消收藏】MQ 发送成功");
            } catch (AmqpException e) {
                log.error("==> 【笔记取消收藏】MQ 发送异常: ", e);
            }
        });

        return Response.success();
    }

    /**
     * 异步初始化用户收藏笔记 ZSet
     */
    private void asyncInitUserNoteCollectZSet(Long userId, String userNoteCollectZSetKey) {
        threadPoolTaskExecutor.submit(() -> {
            // 判断用户笔记收藏 ZSET 是否存在
            boolean hasKey = redisTemplate.hasKey(userNoteCollectZSetKey);

            // 不存在，则重新初始化
            if (!hasKey) {
                // 查询用户最新收藏的 300 条笔记
                List<NoteCollectionDO> noteCollectionDOS = noteCollectionDOMapper.selectCollectedByUserIdAndLimit(userId, 300);
                if (CollUtil.isEmpty(noteCollectionDOS)) {
                    return;
                }
                // 保底1天+随机秒数
                long expireSeconds = 60 * 60 * 24 + RandomUtil.randomInt(60 * 60 * 24);

                // 构造 Lua 参数
                Object[] luaArgs = buildZSetLuaArgs(noteCollectionDOS, NoteCollectionDO::getCreateTime,
                        NoteCollectionDO::getNoteId, expireSeconds);

                DefaultRedisScript<Long> script2 = new DefaultRedisScript<>();
                script2.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/batch_add_note_collect_zset_and_expire.lua")));
                script2.setResultType(Long.class);

                redisTemplate.execute(script2, List.of(userNoteCollectZSetKey), luaArgs);
            }
        });
    }

    /**
     * 异步初始化用户点赞笔记 ZSet
     */
    private void asyncInitUserNoteLikesZSet(Long userId, String userNoteLikeZSetKey) {
        // 异步初始化 ZSET
        threadPoolTaskExecutor.submit(() -> {
            // 判断用户笔记点赞 ZSET 是否存在
            boolean hasKey = redisTemplate.hasKey(userNoteLikeZSetKey);

            // 不存在，则重新初始化
            if (!hasKey) {
                // 查询数据库中该用户最新点赞的 100 条笔记
                List<NoteLikeDO> noteLikeDOS = noteLikeDOMapper.selectLikedByUserIdAndLimit(userId, 100);
                if (CollUtil.isNotEmpty(noteLikeDOS)) {
                    // 保底1天+随机秒数
                    long expireSeconds = 60 * 60 * 24 + RandomUtil.randomInt(60 * 60 * 24);
                    // 构建 Lua 参数
                    Object[] luaArgs = buildZSetLuaArgs(noteLikeDOS, NoteLikeDO::getCreateTime,
                            NoteLikeDO::getNoteId, expireSeconds);

                    DefaultRedisScript<Long> script = new DefaultRedisScript<>();
                    script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/batch_add_note_like_zset_and_expire.lua")));
                    script.setResultType(Long.class);

                    redisTemplate.execute(script, List.of(userNoteLikeZSetKey), luaArgs);
                }
            }
        });
    }

    /**
     * 构建 Lua 脚本参数
     */
    private static <T> Object[] buildZSetLuaArgs(List<T> list,
                                                 Function<T, LocalDateTime> timeExtractor,
                                                 Function<T, Long> noteIdExtractor,
                                                 long expireSeconds) {
        int argsLength = list.size() * 2 + 1; // 每个笔记点赞/收藏关系有 2 个参数（score 和 value），最后再跟一个过期时间
        Object[] luaArgs = new Object[argsLength];

        int i = 0;
        for (T t : list) {
            luaArgs[i] = DateUtils.localDateTime2Timestamp(timeExtractor.apply(t)); // 点赞/收藏时间作为 score
            luaArgs[i + 1] = noteIdExtractor.apply(t);     // 笔记ID 作为 ZSet value
            i += 2;
        }

        luaArgs[argsLength - 1] = expireSeconds;     // 最后一个参数是 ZSet 的过期时间
        return luaArgs;
    }

    /**
     * 初始化用户点赞笔记布隆过滤器
     */
    private void batchAddNoteLike2BloomAndExpire(Long userId, long expireSeconds, String bloomUserNoteLikeListKey) {
        try {
            // 全量同步，并设置过期时间
            List<NoteLikeDO> noteLikeDOS = noteLikeDOMapper.selectByUserId(userId);

            if (CollUtil.isNotEmpty(noteLikeDOS)) {
                DefaultRedisScript<Long> script = new DefaultRedisScript<>();
                script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/bloom_batch_add_note_like_and_expire.lua")));
                script.setResultType(Long.class);

                // 构建 Lua 参数
                List<Object> luaArgs = Lists.newArrayList();
                noteLikeDOS.forEach(noteLikeDO -> luaArgs.add(noteLikeDO.getNoteId()));   // 将每个点赞的笔记 ID 传入
                luaArgs.add(expireSeconds); // 最后一个参数是过期时间（秒）
                redisTemplate.execute(script, List.of(bloomUserNoteLikeListKey), luaArgs.toArray());
            }
        } catch (Exception e) {
            log.error("## 异步初始化【笔记点赞】布隆过滤器异常: ", e);
        }
    }

    /**
     * 初始化用户收藏笔记布隆过滤器
     */
    private void batchAddNoteCollect2BloomAndExpire(Long userId, long expireSeconds,
                                                    String bloomUserNoteCollectListKey) {
        try {
            // 异步全量同步一下，并设置过期时间
            List<NoteCollectionDO> noteCollectionDOS = noteCollectionDOMapper.selectByUserId(userId);

            // 该用户没有收藏过任何笔记，直接返回
            if (CollUtil.isEmpty(noteCollectionDOS)) return;

            // 构建 Lua 脚本参数
            List<Object> luaArgs = new ArrayList<>();
            noteCollectionDOS.forEach(noteCollectionDO -> luaArgs.add(noteCollectionDO.getNoteId()));
            luaArgs.add(expireSeconds);

            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/bloom_batch_add_note_collect_and_expire.lua")));
            script.setResultType(Long.class);
            redisTemplate.execute(script, List.of(bloomUserNoteCollectListKey), luaArgs.toArray());
        } catch (Exception e) {
            log.error("## 异步初始化【笔记收藏】布隆过滤器异常: ", e);
        }
    }

    /**
     * 校验笔记是否存在且对当前用户可见，若可访问则返回笔记发布者用户 ID
     */
    private Long checkNoteIsExistAndGetCreatorId(Long noteId, Long userId) {
        // 先从本地缓存中查询
        String findNoteDetailRspVOStrLocalCache = LOCAL_CACHE.getIfPresent(noteId);
        // 解析 Json 字符串为 VO 对象
        FindNoteDetailRspVO findNoteDetailRspVO = JsonUtils.parseObject(findNoteDetailRspVOStrLocalCache, FindNoteDetailRspVO.class);

        // 如果本地缓存没有，则从 Redis 缓存中查询
        if (Objects.isNull(findNoteDetailRspVO)) {
            String noteDetailRedisKey = RedisKeyConstants.buildNoteDetailKey(noteId);

            String noteDetailJson = (String) redisTemplate.opsForValue().get(noteDetailRedisKey);

            // 解析 Json 字符串为 VO 对象
            findNoteDetailRspVO = JsonUtils.parseObject(noteDetailJson, FindNoteDetailRspVO.class);
        }

        if (Objects.nonNull(findNoteDetailRspVO)) {
            // 点赞/收藏同样要遵守笔记可见性：私密笔记只有作者本人能互动
            checkNoteVisible(findNoteDetailRspVO.getVisible(), userId, findNoteDetailRspVO.getCreatorId());
            return findNoteDetailRspVO.getCreatorId();
        }

        // 缓存都失效了，则从数据库中查询（selectByPrimaryKey 已过滤 status=1，可同时校验存在性）
        NoteDO noteDO = noteDOMapper.selectByPrimaryKey(noteId);

        // 若数据库中也不存在，提示用户
        if (Objects.isNull(noteDO)) {
            throw new BizException(ResponseCodeEnum.NOTE_NOT_FOUND);
        }

        // 可见性校验
        checkNoteVisible(noteDO.getVisible(), userId, noteDO.getCreatorId());

        // 如果数据存在，则异步同步一下缓存
        threadPoolTaskExecutor.submit(() -> findNoteDetail(
                FindNoteDetailReqVO.builder().id(noteId).build()));

        return noteDO.getCreatorId();
    }

    /**
     * 根据笔记 ID 查询笔记，并校验笔记是否存在且属于当前用户
     */
    private NoteDO getNoteAndCheckOwner(Long noteId, Long userId) {
        // 根据笔记 ID 查询笔记
        NoteDO noteDO = noteDOMapper.selectByPrimaryKey(noteId);
        // 判断笔记是否存在
        if (Objects.isNull(noteDO)) {
            throw new BizException(ResponseCodeEnum.NOTE_NOT_FOUND);
        }
        // 校验当前登录用户是否是该笔记创作人
        if (!Objects.equals(userId, noteDO.getCreatorId())) {
            throw new BizException(ResponseCodeEnum.NOTE_PERMISSION_DENIED);
        }
        return noteDO;
    }

    /**
     * 发送本地缓存失效消息
     */
    private void sendLocalCacheInvalidateMessage(Long noteId) {
        try {
            rabbitTemplate.convertAndSend(MQConstants.NOTE_CACHE_INVALIDATE_EXCHANGE,
                    MQConstants.DELETE_NOTE_LOCAL_CACHE_ROUTING_KEY, noteId);
            log.info("==> MQ：本地笔记缓存失效消息发送成功, 笔记 ID: {}", noteId);
        } catch (Exception e) {
            log.error("==> MQ：本地笔记缓存失效消息发送失败, 笔记 ID: {}", noteId, e);
        }
    }

    /**
     * 发送延迟删除 Redis 缓存失效消息
     *
     * 消息先进入带 1 秒 TTL 的延迟队列，过期后死信到真正的 Redis 缓存删除队列，
     * 这样不依赖 RabbitMQ 延迟插件，也能避开「删除缓存后并发读又把旧值写回」的竞态。
     */
    private void sendRedisCacheInvalidateMessage(Long noteId) {
        try {
            rabbitTemplate.convertAndSend(MQConstants.NOTE_CACHE_INVALIDATE_EXCHANGE,
                    MQConstants.DELETE_NOTE_REDIS_CACHE_DELAY_ROUTING_KEY, noteId);
            log.info("==> MQ：延迟删除 Redis 笔记缓存消息发送成功, 笔记 ID: {}", noteId);
        } catch (Exception e) {
            log.error("==> MQ：延迟删除 Redis 笔记缓存消息发送失败, 笔记 ID: {}", noteId, e);
        }
    }

    /**
     * 解析笔记话题：优先按话题名称关联（不存在则自动创建），其次按话题 ID 关联
     */
    private TopicDO resolveTopic(Long topicId, String topicName) {
        // 按话题名称关联
        if (StringUtils.isNotBlank(topicName)) {
            String name = topicName.trim();

            // 查询话题是否已存在
            TopicDO topicDO = topicDOMapper.selectByName(name);
            if (Objects.nonNull(topicDO)) {
                return topicDO;
            }

            // 话题不存在，则自动创建
            topicDO = TopicDO.builder()
                    .id(Long.valueOf(distributedIdGeneratorRpcService.getSnowflakeId()))
                    .name(name)
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .isDeleted(Boolean.FALSE)
                    .build();
            topicDOMapper.insertSelective(topicDO);
            log.info("==> 话题不存在，自动创建话题，id: {}, name: {}", topicDO.getId(), name);
            return topicDO;
        }

        // 按话题 ID 关联
        if (Objects.nonNull(topicId)) {
            // 根据话题 ID 获取话题名称
            String name = topicDOMapper.selectNameByPrimaryKey(topicId);
            // 校验话题是否存在
            if (StringUtils.isBlank(name)) {
                throw new BizException(ResponseCodeEnum.TOPIC_NOT_FOUND);
            }
            return TopicDO.builder().id(topicId).name(name).build();
        }

        return null;
    }

    /**
     * 校验笔记的可见性
     */
    private void checkNoteVisible(Integer visible, Long currUserId, Long creatorId) {
        if (Objects.equals(visible, NoteVisibleEnum.PRIVATE.getCode())
                && !Objects.equals(currUserId, creatorId)) {  // 仅自己可见, 并且访问用户为笔记创建者才能访问，非本人则抛出异常
            throw new BizException(ResponseCodeEnum.NOTE_PRIVATE);
        }
    }

    /**
     * 校验笔记的可见性（针对 VO 实体类）
     */
    private void checkNoteVisibleFromVO(Long userId, FindNoteDetailRspVO findNoteDetailRspVO) {
        if (Objects.nonNull(findNoteDetailRspVO)) {
            Integer visible = findNoteDetailRspVO.getVisible();
            checkNoteVisible(visible, userId, findNoteDetailRspVO.getCreatorId());
        }
    }

    /**
     * 删除本地笔记缓存
     */
    public void deleteNoteLocalCache(Long noteId) {
        LOCAL_CACHE.invalidate(noteId);
    }

    /**
     * 批量查询当前用户已点赞的笔记 ID（返回字符串，避免前端 Long 精度丢失）
     */
    @Override
    public Response<List<String>> findLikedNoteIds(FindNoteLikedStatusReqVO findNoteLikedStatusReqVO) {
        // 未登录直接返回空集合
        Long userId = LoginUserContextHolder.getUserId();
        if (Objects.isNull(userId)) {
            return Response.success(Collections.emptyList());
        }

        List<Long> noteIds = findNoteLikedStatusReqVO.getNoteIds();
        if (CollUtil.isEmpty(noteIds)) {
            return Response.success(Collections.emptyList());
        }

        List<String> likedNoteIds = noteLikeDOMapper.selectLikedNoteIds(userId, noteIds)
                .stream()
                .map(String::valueOf)
                .toList();

        return Response.success(likedNoteIds);
    }

    /**
     * 分页查询用户发布的笔记
     */
    @Override
    public PageResponse<FindNoteListItemRspVO> findPublishNoteList(FindUserNoteListReqVO findUserNoteListReqVO) {
        Long userId = findUserNoteListReqVO.getUserId();
        long pageNo = Objects.isNull(findUserNoteListReqVO.getPageNo()) ? 1L : findUserNoteListReqVO.getPageNo();
        long limit = 10L;

        // 只有查自己时，才返回「仅自己可见」的笔记
        Long currentUserId = LoginUserContextHolder.getUserId();
        boolean includePrivate = Objects.nonNull(currentUserId) && currentUserId.equals(userId);

        // 查询总数
        int total = noteDOMapper.selectCountByCreatorId(userId, includePrivate);
        if (total <= 0) {
            return PageResponse.success(Collections.emptyList(), pageNo, total, limit);
        }

        // 计算分页
        long start = PageResponse.getOffset(pageNo, limit);
        if (pageNo > PageResponse.getTotalPage(total, limit)) {
            return PageResponse.success(Collections.emptyList(), pageNo, total, limit);
        }

        // 查询当前页笔记
        List<NoteDO> noteDOS = noteDOMapper.selectPageByCreatorId(userId, includePrivate, start, limit);

        // 组装返参
        List<FindNoteListItemRspVO> items = buildNoteListItems(noteDOS, Collections.emptyMap());
        return PageResponse.success(items, pageNo, total, limit);
    }

    /**
     * 分页查询用户收藏的笔记
     */
    @Override
    public PageResponse<FindNoteListItemRspVO> findCollectNoteList(FindUserNoteListReqVO findUserNoteListReqVO) {
        Long userId = findUserNoteListReqVO.getUserId();
        long pageNo = Objects.isNull(findUserNoteListReqVO.getPageNo()) ? 1L : findUserNoteListReqVO.getPageNo();
        long limit = 10L;
        Long currentUserId = LoginUserContextHolder.getUserId();

        // 查询总数（不含已删除、已取消收藏、他人私密的笔记）
        int total = noteCollectionDOMapper.selectCountByUserId(userId, currentUserId);
        if (total <= 0) {
            return PageResponse.success(Collections.emptyList(), pageNo, total, limit);
        }

        // 计算分页
        long start = PageResponse.getOffset(pageNo, limit);
        if (pageNo > PageResponse.getTotalPage(total, limit)) {
            return PageResponse.success(Collections.emptyList(), pageNo, total, limit);
        }

        // 查询当前页收藏记录
        List<NoteCollectionDO> noteCollectionDOS = noteCollectionDOMapper.selectPageByUserId(userId, currentUserId, start, limit);
        if (CollUtil.isEmpty(noteCollectionDOS)) {
            return PageResponse.success(Collections.emptyList(), pageNo, total, limit);
        }

        // 保持收藏时间顺序，并记录收藏时间
        List<Long> noteIds = new ArrayList<>();
        Map<Long, LocalDateTime> actionTimeMap = new HashMap<>();
        for (NoteCollectionDO noteCollectionDO : noteCollectionDOS) {
            noteIds.add(noteCollectionDO.getNoteId());
            actionTimeMap.put(noteCollectionDO.getNoteId(), noteCollectionDO.getCreateTime());
        }

        List<NoteDO> noteDOS = selectNotesByIdsInOrder(noteIds);
        List<FindNoteListItemRspVO> items = buildNoteListItems(noteDOS, actionTimeMap);
        return PageResponse.success(items, pageNo, total, limit);
    }

    /**
     * 分页查询用户点赞的笔记
     */
    @Override
    public PageResponse<FindNoteListItemRspVO> findLikeNoteList(FindUserNoteListReqVO findUserNoteListReqVO) {
        Long userId = findUserNoteListReqVO.getUserId();
        long pageNo = Objects.isNull(findUserNoteListReqVO.getPageNo()) ? 1L : findUserNoteListReqVO.getPageNo();
        long limit = 10L;
        Long currentUserId = LoginUserContextHolder.getUserId();

        // 查询总数（不含已删除、已取消点赞、他人私密的笔记）
        int total = noteLikeDOMapper.selectCountByUserId(userId, currentUserId);
        if (total <= 0) {
            return PageResponse.success(Collections.emptyList(), pageNo, total, limit);
        }

        // 计算分页
        long start = PageResponse.getOffset(pageNo, limit);
        if (pageNo > PageResponse.getTotalPage(total, limit)) {
            return PageResponse.success(Collections.emptyList(), pageNo, total, limit);
        }

        // 查询当前页点赞记录
        List<NoteLikeDO> noteLikeDOS = noteLikeDOMapper.selectPageByUserId(userId, currentUserId, start, limit);
        if (CollUtil.isEmpty(noteLikeDOS)) {
            return PageResponse.success(Collections.emptyList(), pageNo, total, limit);
        }

        // 保持点赞时间顺序，并记录点赞时间
        List<Long> noteIds = new ArrayList<>();
        Map<Long, LocalDateTime> actionTimeMap = new HashMap<>();
        for (NoteLikeDO noteLikeDO : noteLikeDOS) {
            noteIds.add(noteLikeDO.getNoteId());
            actionTimeMap.put(noteLikeDO.getNoteId(), noteLikeDO.getCreateTime());
        }

        List<NoteDO> noteDOS = selectNotesByIdsInOrder(noteIds);
        List<FindNoteListItemRspVO> items = buildNoteListItems(noteDOS, actionTimeMap);
        return PageResponse.success(items, pageNo, total, limit);
    }

    /**
     * 根据笔记 ID 批量查询笔记，并保持传入的顺序
     */
    private List<NoteDO> selectNotesByIdsInOrder(List<Long> noteIds) {
        if (CollUtil.isEmpty(noteIds)) {
            return Collections.emptyList();
        }

        List<NoteDO> noteDOS = noteDOMapper.selectByIds(noteIds);
        if (CollUtil.isEmpty(noteDOS)) {
            return Collections.emptyList();
        }

        Map<Long, NoteDO> noteMap = new HashMap<>();
        for (NoteDO noteDO : noteDOS) {
            noteMap.put(noteDO.getId(), noteDO);
        }

        List<NoteDO> orderedNoteDOS = new ArrayList<>();
        for (Long noteId : noteIds) {
            NoteDO noteDO = noteMap.get(noteId);
            if (Objects.nonNull(noteDO)) {
                orderedNoteDOS.add(noteDO);
            }
        }
        return orderedNoteDOS;
    }

    /**
     * 组装笔记列表条目（含作者昵称、头像、封面）
     */
    private List<FindNoteListItemRspVO> buildNoteListItems(List<NoteDO> noteDOS, Map<Long, LocalDateTime> actionTimeMap) {
        if (CollUtil.isEmpty(noteDOS)) {
            return Collections.emptyList();
        }

        // 批量查询作者昵称、头像
        List<Long> creatorIds = new ArrayList<>();
        for (NoteDO noteDO : noteDOS) {
            if (Objects.nonNull(noteDO.getCreatorId()) && !creatorIds.contains(noteDO.getCreatorId())) {
                creatorIds.add(noteDO.getCreatorId());
            }
        }
        Map<Long, FindUserByIdRspDTO> creatorMap = new HashMap<>();
        for (FindUserByIdRspDTO creator : userRpcService.findByIds(creatorIds)) {
            creatorMap.put(creator.getId(), creator);
        }

        List<FindNoteListItemRspVO> items = new ArrayList<>();
        for (NoteDO noteDO : noteDOS) {
            FindUserByIdRspDTO creator = creatorMap.get(noteDO.getCreatorId());

            // 封面取第一张图
            String cover = null;
            if (StringUtils.isNotBlank(noteDO.getImgUris())) {
                String[] imgUris = noteDO.getImgUris().split(",");
                if (imgUris.length > 0 && StringUtils.isNotBlank(imgUris[0])) {
                    cover = imgUris[0];
                }
            }

            items.add(FindNoteListItemRspVO.builder()
                    .noteId(noteDO.getId())
                    .type(noteDO.getType())
                    .title(noteDO.getTitle())
                    .cover(cover)
                    .videoUri(noteDO.getVideoUri())
                    .visible(noteDO.getVisible())
                    .isTop(noteDO.getIsTop())
                    .createTime(noteDO.getCreateTime())
                    .updateTime(noteDO.getUpdateTime())
                    .actionTime(actionTimeMap.get(noteDO.getId()))
                    .creatorId(noteDO.getCreatorId())
                    .creatorName(Objects.nonNull(creator) ? creator.getNickname() : null)
                    .avatar(Objects.nonNull(creator) ? creator.getAvatar() : null)
                    .likeTotal(Objects.isNull(noteDO.getLikeTotal()) ? 0L : noteDO.getLikeTotal())
                    .collectTotal(Objects.isNull(noteDO.getCollectTotal()) ? 0L : noteDO.getCollectTotal())
                    .build());
        }
        return items;
    }
}
