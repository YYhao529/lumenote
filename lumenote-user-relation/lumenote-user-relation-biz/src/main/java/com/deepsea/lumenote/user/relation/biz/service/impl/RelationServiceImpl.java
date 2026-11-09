package com.deepsea.lumenote.user.relation.biz.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
import com.deepsea.framework.biz.context.holder.LoginUserContextHolder;
import com.deepsea.framework.common.exception.BizException;
import com.deepsea.framework.common.mq.constant.MQConstants;
import com.deepsea.framework.common.response.PageResponse;
import com.deepsea.framework.common.response.Response;
import com.deepsea.framework.common.util.DateUtils;
import com.deepsea.lumenote.user.dto.resp.FindUserByIdRspDTO;
import com.deepsea.lumenote.user.relation.biz.constant.RedisKeyConstants;
import com.deepsea.lumenote.user.relation.biz.domain.dataobject.FansDO;
import com.deepsea.lumenote.user.relation.biz.domain.dataobject.FollowingDO;
import com.deepsea.lumenote.user.relation.biz.domain.mapper.FansDOMapper;
import com.deepsea.lumenote.user.relation.biz.domain.mapper.FollowingDOMapper;
import com.deepsea.lumenote.user.relation.biz.enums.LuaResultEnum;
import com.deepsea.lumenote.user.relation.biz.enums.ResponseCodeEnum;
import com.deepsea.lumenote.user.relation.biz.model.dto.FollowUserMqDTO;
import com.deepsea.lumenote.user.relation.biz.model.dto.UnfollowUserMqDTO;
import com.deepsea.lumenote.user.relation.biz.model.dto.UserCountRspDTO;
import com.deepsea.lumenote.user.relation.biz.model.vo.*;
import com.deepsea.lumenote.user.relation.biz.rpc.CountRpcService;
import com.deepsea.lumenote.user.relation.biz.rpc.UserRpcService;
import com.deepsea.lumenote.user.relation.biz.service.RelationService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
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
import java.util.concurrent.TimeUnit;

/**
 * 用户关系服务
 */
@Slf4j
@Service
public class RelationServiceImpl implements RelationService {

    @Autowired
    private UserRpcService userRpcService;
    @Autowired
    private CountRpcService countRpcService;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private FollowingDOMapper followingDOMapper;
    @Resource(name = "taskExecutor")
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private FansDOMapper fansDOMapper;

    /**
     * 关注用户
     */
    @Override
    public Response<?> follow(FollowUserReqVO followUserReqVO) {
        // 关注的用户 ID
        Long followUserId = followUserReqVO.getFollowUserId();
        // 当前登录的用户 ID
        Long userId = LoginUserContextHolder.getUserId();

        // 校验关注的用户是否是自己
        if (Objects.equals(userId, followUserId)) {
            throw new BizException(ResponseCodeEnum.CANT_FOLLOW_YOUR_SELF);
        }

        // 校验关注的用户是否存在
        FindUserByIdRspDTO findUserByIdRspDTO = userRpcService.findById(followUserId);
        if (Objects.isNull(findUserByIdRspDTO)) {
            throw new BizException(ResponseCodeEnum.FOLLOW_USER_NOT_EXISTED);
        }

        // 构建当前用户关注列表的 Redis Key
        String followingRedisKey = RedisKeyConstants.buildUserFollowingKey(userId);

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        // Lua 脚本路径
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/follow_check_and_add.lua")));
        // 返回值类型
        script.setResultType(Long.class);

        // 当前时间
        LocalDateTime now = LocalDateTime.now();
        // 当前时间转时间戳
        long timestamp = DateUtils.localDateTime2Timestamp(now);

        // 执行 Lua 脚本，拿到返回结果
        Long result = redisTemplate.execute(script, List.of(followingRedisKey), followUserId, timestamp);

        // 校验 Lua 脚本执行结果
        checkLuaScriptResult(result);

        // ZSET 关注列表不存在
        if (Objects.equals(result, LuaResultEnum.ZSET_NOT_EXIST.getCode())) {
            // 从数据库查询当前用户的关注关系记录
            List<FollowingDO> followingDOS = followingDOMapper.selectByUserId(userId);

            // 随机过期时间
            // 保底1天+随机秒数
            long expireSeconds = 60 * 60 * 24 + RandomUtil.randomInt(60 * 60 * 24);

            // 若记录为空，直接 ZADD 关系数据，并设置过期时间
            if (CollUtil.isEmpty(followingDOS)) {
                DefaultRedisScript<Long> script2 = new DefaultRedisScript<>();
                script2.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/follow_add_and_expire.lua")));
                script2.setResultType(Long.class);

                // TODO: 可以根据用户类型，设置不同的过期时间，若当前用户为大V, 则可以过期时间设置的长些或者不设置过期时间；如不是，则设置的短些
                // 如何判断呢？可以从计数服务获取用户的粉丝数，目前计数服务还没创建，则暂时采用统一的过期策略
                redisTemplate.execute(script2, List.of(followingRedisKey), followUserId, timestamp, expireSeconds);
            } else {  // 若记录不为空，则将关注关系数据全量同步到 Redis 中，并设置过期时间
                // 构建 Lua 脚本参数
                Object[] luaArgs = buildLuaArgs(followingDOS, expireSeconds);

                // 执行 Lua 脚本，批量同步关注关系数据到 Redis 中
                DefaultRedisScript<Long> script3 = new DefaultRedisScript<>();
                script3.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/follow_batch_add_and_expire.lua")));
                script3.setResultType(Long.class);
                redisTemplate.execute(script3, List.of(followingRedisKey), luaArgs);

                // 再次调用上面的 Lua 脚本：follow_check_and_add.lua , 将最新的关注关系添加进去
                result = redisTemplate.execute(script, List.of(followingRedisKey), followUserId, timestamp);
                checkLuaScriptResult(result);
            }
        }

        // 构建 FollowUserMqDTO
        FollowUserMqDTO followUserMqDTO = FollowUserMqDTO.builder()
                .userId(userId)
                .followUserId(followUserId)
                .createTime(now)
                .build();

        log.info("==> 开始发送关注操作 MQ, 消息体: {}", followUserMqDTO);

        // 异步发送 MQ 消息，将关注关系数据同步到其他服务
        threadPoolTaskExecutor.submit(() -> {
            try {
                rabbitTemplate.convertAndSend(MQConstants.FOLLOW_UNFOLLOW_MESSAGE_EXCHANGE,
                        MQConstants.FOLLOW_MESSAGE_ROUTING_KEY, followUserMqDTO);
                log.info("==> MQ：数据库用户关注同步消息发送成功");
            } catch (AmqpException e) {
                log.error("==> MQ：数据库用户关注同步消息发送失败", e);
            }
        });

        return Response.success();
    }

    /**
     * 取关用户
     */
    @Override
    public Response<?> unfollow(UnfollowUserReqVO unfollowUserReqVO) {
        // 被取关用户 ID
        Long unfollowUserId = unfollowUserReqVO.getUnfollowUserId();
        // 当前登录用户 ID
        Long userId = LoginUserContextHolder.getUserId();

        // 校验：用户无法对自己进行取关操作
        if (Objects.equals(userId, unfollowUserId)) {
            throw new BizException(ResponseCodeEnum.CANT_UNFOLLOW_YOUR_SELF);
        }

        // 校验：被取关用户是否真实存在
        FindUserByIdRspDTO findUserByIdRspDTO = userRpcService.findById(unfollowUserId);
        if (Objects.isNull(findUserByIdRspDTO)) {
            throw new BizException(ResponseCodeEnum.UNFOLLOW_USER_NOT_EXISTED);
        }

        // 当前用户的关注列表 Redis Key
        String followingRedisKey = RedisKeyConstants.buildUserFollowingKey(userId);

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        // Lua 脚本路径
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/unfollow_check_and_delete.lua")));
        // 返回值类型
        script.setResultType(Long.class);

        // 执行 Lua 脚本，拿到返回结果
        Long result = redisTemplate.execute(script, List.of(followingRedisKey), unfollowUserId);

        // 校验 Lua 脚本执行结果
        checkLuaScriptResult(result);

        // 此时 result 可能为 0，也可能为 -1
        if (Objects.equals(result, LuaResultEnum.ZSET_NOT_EXIST.getCode())) {
            // 如果 result 为-1，说明 ZSET 过期，需要查询数据库，将该用户的关注关系全量更新到 redis 缓存
            List<FollowingDO> followingDOS = followingDOMapper.selectByUserId(userId);

            // 查询结果为空，说明用户没有关注任何人
            if (CollUtil.isEmpty(followingDOS)) {
                throw new BizException(ResponseCodeEnum.NOT_FOLLOWED);
            }

            // 随机过期时间
            // 保底1天+随机秒数
            long expireSeconds = 60 * 60 * 24 + RandomUtil.randomInt(60 * 60 * 24);
            Object[] luaArgs = buildLuaArgs(followingDOS, expireSeconds);

            // 执行 Lua 脚本，批量同步关系数据到 Redis 中
            DefaultRedisScript<Long> script2 = new DefaultRedisScript<>();
            script2.setScriptSource(new ResourceScriptSource(new ClassPathResource("/lua/follow_batch_add_and_expire.lua")));
            script2.setResultType(Long.class);
            redisTemplate.execute(script2, List.of(followingRedisKey), luaArgs);

            // 同步完成后，再调用上面的脚本
            result = redisTemplate.execute(script, List.of(followingRedisKey), unfollowUserId);
            // 校验 Lua 脚本执行结果
            checkLuaScriptResult(result);
        }
        // 到这里，已经删除了当前用户与被取关用户在 redis 中的关注关系记录，接下来就是删除数据库以及粉丝表中的相关记录
        // 构建消息体 DTO
        UnfollowUserMqDTO unfollowUserMqDTO = UnfollowUserMqDTO.builder()
                .userId(userId)
                .unfollowUserId(unfollowUserId)
                .createTime(LocalDateTime.now())
                .build();

        log.info("==> 开始发送取关操作 MQ, 消息体: {}", unfollowUserMqDTO);

        // 异步发送 MQ 消息，将取关关系数据同步到数据库、redis 粉丝表
        threadPoolTaskExecutor.submit(() -> {
            try {
                rabbitTemplate.convertAndSend(MQConstants.FOLLOW_UNFOLLOW_MESSAGE_EXCHANGE,
                        MQConstants.UNFOLLOW_MESSAGE_ROUTING_KEY, unfollowUserMqDTO);
                log.info("==> MQ：数据库用户取关同步消息发送成功");
            } catch (AmqpException e) {
                log.error("==> MQ：数据库用户取关同步消息发送失败", e);
            }
        });

        return Response.success();
    }

    /**
     * 查询用户关注列表
     */
    @Override
    public PageResponse<FindFollowingUserRspVO> findFollowingList(FindFollowingListReqVO findFollowingListReqVO) {
        // 想要查询的用户 ID
        Long userId = findFollowingListReqVO.getUserId();
        // 页码
        Integer pageNo = findFollowingListReqVO.getPageNo();
        // 每页展示 10 条数据
        long limit = 10;
        // 计算起始索引
        long start = PageResponse.getOffset(pageNo, limit);

        // 先从 Redis 中查询
        String followingListRedisKey = RedisKeyConstants.buildUserFollowingKey(userId);
        // 查询目标用户关注列表 ZSet 的总大小
        long total = redisTemplate.opsForZSet().zCard(followingListRedisKey);

        Set<Object> followingListCache = Collections.emptySet();
        List<Long> followingUserIds;
        // 如果 total > 0，说明缓存不为空，才从 Redis 缓存中获取该用户的关注列表
        if (total > 0) {
            // 计算结束索引
            long end = start + limit - 1;
            // 计算总页数
            long totalPage = PageResponse.getTotalPage(total, limit);

            if (pageNo > totalPage) {
                // 页码超出范围，返回空列表
                return PageResponse.success(Collections.emptyList(), pageNo, total, limit);
            }

            followingListCache = redisTemplate.opsForZSet().reverseRange(followingListRedisKey, start, end);

            if (CollUtil.isEmpty(followingListCache)) {
                return PageResponse.success(Collections.emptyList(), pageNo, total, limit);
            }

            // 如果缓存不为空，则取出关注的用户 ID 列表
            followingUserIds = followingListCache.stream()
                    .map(o -> Long.valueOf(o.toString()))
                    .toList();
        } else {
            // 缓存为空，则从数据库中查询关注列表
            // 查询关注列表的记录数
            total = followingDOMapper.selectCountByUserId(userId);
            // 计算总页数
            long totalPage = PageResponse.getTotalPage(total, limit);
            if (total == 0 || pageNo > totalPage) {
                // 如果该用户没有关注任何人，或者页码超出范围直接返回空列表
                return PageResponse.success(Collections.emptyList(), pageNo, total, limit);
            }

            // 分页查询
            List<FollowingDO> followingDOS = followingDOMapper.selectPageListByUserId(userId, start, limit);

            // 提取出关注的用户 ID 列表
            followingUserIds = followingDOS.stream()
                    .map(FollowingDO::getFollowingUserId)
                    .toList();

            // 异步将关注列表全量同步到 Redis
            threadPoolTaskExecutor.submit(() -> {
                syncFollowingList2Redis(userId);
            });
        }

        // RPC: 调用用户服务，并将 DTO 转换为 VO
        List<FindFollowingUserRspVO> findFollowingUserRspVOS = rpcUserServiceAndDTO2VO(followingUserIds);
        return PageResponse.success(findFollowingUserRspVOS, pageNo, total, limit);
    }

    /**
     * 查询粉丝列表
     */
    @Override
    public PageResponse<FindFansUserRspVO> findFansList(FindFansListReqVO findFansListReqVO) {
        // 查询的用户 ID
        Long userId = findFansListReqVO.getUserId();
        // 页码
        Integer pageNo = findFansListReqVO.getPageNo();
        // 每页展示的记录数
        long limit = 10;
        // 计算开始索引
        long start = PageResponse.getOffset(pageNo, limit);

        // 先从 Redis 缓存中获取该用户的粉丝列表
        String fansListRedisKey = RedisKeyConstants.buildUserFansKey(userId);
        long total = redisTemplate.opsForZSet().zCard(fansListRedisKey);
        List<Long> fansUserIds;
        if (total > 0) {
            // 计算结束索引
            long end = start + limit - 1;
            // 计算总页数
            long totalPage = PageResponse.getTotalPage(total, limit);

            // 判断页码是否超出范围
            if (pageNo > totalPage) {
                return PageResponse.success(Collections.emptyList(), pageNo, total, limit);
            }

            // 缓存中有粉丝 id 数据
            Set<Object> range = redisTemplate.opsForZSet().reverseRange(fansListRedisKey, start, end);

            if (CollUtil.isEmpty(range)) {
                return PageResponse.success(Collections.emptyList(), pageNo, total, limit);
            }

            // 将粉丝 id 放到集合里
            fansUserIds = range.stream()
                    .map(o -> Long.valueOf(o.toString()))
                    .toList();

        } else {
            // 情况一：该用户没有粉丝；情况二：缓存过期
            // 从数据库中查询粉丝列表的记录数
            total = fansDOMapper.selectCountByUserId(userId);
            // 计算总页数
            long totalPage = PageResponse.getTotalPage(total, limit);
            // 如果没有粉丝\页码超出范围\页码超过500，直接返回空列表
            if (total == 0 || pageNo > totalPage || pageNo > 500) {
                return PageResponse.success(Collections.emptyList(), pageNo, total, limit);
            }

            // 分页查询
            List<FansDO> fansDOS = fansDOMapper.selectPageListByUserId(userId, start, limit);

            if (CollUtil.isEmpty(fansDOS)) {
                return PageResponse.success(Collections.emptyList(), pageNo, total, limit);
            }

            // 将粉丝 id 放到集合里
            fansUserIds = fansDOS.stream()
                    .map(FansDO::getFansUserId)
                    .toList();

            // TODO: 同步粉丝列表至 Redis 中
            threadPoolTaskExecutor.submit(() -> syncFansList2Redis(userId));
        }

        // RPC: 调用用户服务、计数服务，并将 DTO 转换为 VO
        List<FindFansUserRspVO> findFansUserRspVOList =
                rpcUserServiceAndCountServiceAndDTO2VO(fansUserIds);

        return PageResponse.success(findFansUserRspVOList, pageNo, total, limit);
    }

    /**
     * RPC: 调用用户服务，并将 DTO 转换为 VO
     */
    private List<FindFollowingUserRspVO> rpcUserServiceAndDTO2VO(List<Long> userIds) {
        // 通过远程调用从用户服务获取关注用户信息
        List<FindUserByIdRspDTO> findUserByIdRspDTOS = userRpcService.findByIds(userIds);

        List<FindFollowingUserRspVO> findFollowingUserRspVOS = Collections.emptyList();

        // 若不为空，DTO 转为 VO
        if (CollUtil.isNotEmpty(findUserByIdRspDTOS)) {
            findFollowingUserRspVOS = findUserByIdRspDTOS.stream()
                    .map(findUserByIdRspDTO -> FindFollowingUserRspVO.builder()
                            .userId(findUserByIdRspDTO.getId())
                            .avatar(findUserByIdRspDTO.getAvatar())
                            .nickname(findUserByIdRspDTO.getNickname())
                            .introduction(findUserByIdRspDTO.getIntroduction())
                            .build())
                    .toList();
        }
        return findFollowingUserRspVOS;
    }

    /**
     * RPC: 调用用户服务、计数服务，并将 DTO 转换为 VO 粉丝列表
     */
    private List<FindFansUserRspVO> rpcUserServiceAndCountServiceAndDTO2VO(List<Long> fansUserIds) {
        // RPC: 批量查询用户信息
        List<FindUserByIdRspDTO> findUserByIdRspDTOS = userRpcService.findByIds(fansUserIds);
        if (CollUtil.isEmpty(findUserByIdRspDTOS)) {
            return Collections.emptyList();
        }

        // RPC: 批量查询用户的计数数据（笔记总数、粉丝总数）
        Map<Long, UserCountRspDTO> countMap = countRpcService.findUserCounts(fansUserIds);

        // 填充信息并返回
        return findUserByIdRspDTOS.stream()
                .map(dto -> {
                    UserCountRspDTO count = countMap.get(dto.getId());
                    return FindFansUserRspVO.builder()
                            .userId(dto.getId())
                            .avatar(dto.getAvatar())
                            .nickname(dto.getNickname())
                            .fansTotal(Objects.isNull(count) ? 0L : count.getFansTotal())
                            .noteTotal(Objects.isNull(count) ? 0L : count.getNoteTotal())
                            .build();
                })
                .toList();
    }

    /**
     * 校验 Lua 脚本结果，根据状态码抛出对应的业务异常
     */
    private void checkLuaScriptResult(Long result) {
        LuaResultEnum luaResultEnum = LuaResultEnum.valueOf(result);
        if (Objects.isNull(luaResultEnum)) {
            throw new RuntimeException("Lua 返回结果错误");
        }

        // 校验 Lua 脚本执行结果
        switch (luaResultEnum) {
            // 关注数达到上限
            case FOLLOW_LIMIT -> throw new BizException(ResponseCodeEnum.FOLLOWING_COUNT_LIMIT);
            // 已经关注了该用户
            case ALREADY_FOLLOWED -> throw new BizException(ResponseCodeEnum.ALREADY_FOLLOWED);
            // 未关注该用户
            case NOT_FOLLOWED -> throw new BizException(ResponseCodeEnum.NOT_FOLLOWED);
        }
    }

    /**
     * 全量同步关注列表至 Redis 中
     */
    private void syncFollowingList2Redis(Long userId) {
        String followingListRedisKey = RedisKeyConstants.buildUserFollowingKey(userId);
        // 先获取锁，避免重复同步
        String lockKey = "sync:following:" + userId;
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 60, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(locked)) {
            return;
        }
        try {
            // 二次检查：可能已经被其他线程同步完了
            Long existing = redisTemplate.opsForZSet().zCard(followingListRedisKey);
            if (existing != null && existing > 0) {
                return;
            }

            // 查询全量关注用户列表（1000位用户）
            List<FollowingDO> followingDOList = followingDOMapper.selectAllByUserId(userId);
            if (CollUtil.isEmpty(followingDOList)) {
                return;
            }
            // 随机过期时间（保底1天+随机秒数）
            long expireSeconds = 60 * 60 * 24 + RandomUtil.randomInt(60 * 60 * 24);
            // 构建 Lua 脚本参数
            Object[] luaArgs = buildLuaArgs(followingDOList, expireSeconds);

            // 执行 Lua 脚本，批量同步关注关系数据到 Redis 中
            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            script.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/follow_batch_add_and_expire.lua")));
            script.setResultType(Long.class);
            redisTemplate.execute(script, List.of(followingListRedisKey), luaArgs);
        } finally {
            // 释放锁
            redisTemplate.delete(lockKey);
        }
    }

    /**
     * 同步粉丝列表至 Redis（最多5000条）
     */
    private void syncFansList2Redis(Long userId) {
        // 先获取锁，避免重复同步
        String lockKey = "sync:fans:" + userId;
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", 60, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(locked)) {
            return;
        }
        // 用户粉丝列表 Redis Key
        String fansListRedisKey = RedisKeyConstants.buildUserFansKey(userId);
        try {
            // 二次检查：可能已经被其他线程同步完了
            Long existing = redisTemplate.opsForZSet().zCard(fansListRedisKey);
            if (existing != null && existing > 0) {
                return;
            }
            // 查询粉丝列表（最多5000位用户）
            List<FansDO> fansDOS1 = fansDOMapper.select5000FansByUserId(userId);
            if (CollUtil.isEmpty(fansDOS1)) {
                return;
            }
            // 随机过期时间（保底1天+随机秒数）
            long expireSeconds = 60 * 60 * 24 + RandomUtil.randomInt(60 * 60 * 24);
            // 构建 Lua 脚本参数
            Object[] luaArgs = buildFansZSetLuaArgs(fansDOS1, expireSeconds);

            // 执行 Lua 脚本，批量同步粉丝关系数据到 Redis 中
            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            script.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/follow_batch_add_and_expire.lua")));
            script.setResultType(Long.class);
            redisTemplate.execute(script, List.of(fansListRedisKey), luaArgs);
        } finally {
            // 释放锁
            redisTemplate.delete(lockKey);
        }
    }

    /**
     * 构建 Lua 脚本参数：粉丝列表
     */
    private Object[] buildFansZSetLuaArgs(List<FansDO> fansDOS, long expireSeconds) {
        int argsLength = fansDOS.size() * 2 + 1;   // 每个粉丝关系有 2 个参数（score 和 value），再加一个过期时间
        Object[] luaArgs = new Object[argsLength];

        int i = 0;
        for (FansDO fansDO : fansDOS) {
            luaArgs[i] = DateUtils.localDateTime2Timestamp(fansDO.getCreateTime());    // 关注时间作为 score
            luaArgs[i + 1] = fansDO.getFansUserId();   // 粉丝的用户 ID 作为 ZSet value
            i += 2;
        }

        luaArgs[argsLength - 1] = expireSeconds;    // 最后一个参数是 ZSet 的过期时间
        return luaArgs;
    }

    /**
     * 构建 Lua 脚本参数
     */
    private Object[] buildLuaArgs(List<FollowingDO> followingDOS, long expireSeconds) {
        int argsLength = followingDOS.size() * 2 + 1;   // 每个关注关系有 2 个参数（score 和 value），再加一个过期时间
        Object[] luaArgs = new Object[argsLength];

        int i = 0;
        for (FollowingDO followingDO : followingDOS) {
            luaArgs[i] = DateUtils.localDateTime2Timestamp(followingDO.getCreateTime());    // 关注时间作为 score
            luaArgs[i + 1] = followingDO.getFollowingUserId();   // 关注的用户 ID 作为 ZSet value
            i += 2;
        }

        luaArgs[argsLength - 1] = expireSeconds;    // 最后一个参数是 ZSet 的过期时间
        return luaArgs;
    }
}