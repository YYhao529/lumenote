package com.deepsea.lumenote.user.biz.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.deepsea.framework.biz.context.holder.LoginUserContextHolder;
import com.deepsea.framework.common.enums.DeletedEnum;
import com.deepsea.framework.common.enums.StatusEnum;
import com.deepsea.framework.common.exception.BizException;
import com.deepsea.framework.common.response.Response;
import com.deepsea.framework.common.util.JsonUtils;
import com.deepsea.framework.common.util.ParamUtils;
import com.deepsea.lumenote.user.biz.constants.RedisKeyConstants;
import com.deepsea.lumenote.user.biz.constants.RoleConstants;
import com.deepsea.lumenote.user.biz.domain.dataobject.RoleDO;
import com.deepsea.lumenote.user.biz.domain.dataobject.UserDO;
import com.deepsea.lumenote.user.biz.domain.dataobject.UserRoleRelDO;
import com.deepsea.lumenote.user.biz.domain.mapper.RoleDOMapper;
import com.deepsea.lumenote.user.biz.domain.mapper.UserDOMapper;
import com.deepsea.lumenote.user.biz.domain.mapper.UserRoleRelDOMapper;
import com.deepsea.lumenote.user.biz.enums.ResponseCodeEnum;
import com.deepsea.lumenote.user.biz.enums.SexEnum;
import com.deepsea.lumenote.user.biz.model.vo.UpdateUserInfoReqVO;
import com.deepsea.lumenote.user.biz.rpc.DistributedIdGeneratorRpcService;
import com.deepsea.lumenote.user.biz.rpc.OssRpcService;
import com.deepsea.lumenote.user.biz.service.UserService;
import com.deepsea.lumenote.user.dto.req.*;
import com.deepsea.lumenote.user.dto.resp.FindUserByIdRspDTO;
import com.deepsea.lumenote.user.dto.resp.FindUserByPhoneRspDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 用户业务
 */
@Service
@Slf4j
public class UserServiceImpl implements UserService {

    @Autowired
    private UserDOMapper userDOMapper;
    @Autowired
    private OssRpcService ossRpcService;
    @Autowired
    private UserRoleRelDOMapper userRoleRelDOMapper;
    @Autowired
    private RoleDOMapper roleDOMapper;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private DistributedIdGeneratorRpcService distributedIdGeneratorRpcService;
    @Resource(name = "taskExecutor")
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;

    /**
     * 用户信息本地缓存
     */
    private static final Cache<Long, FindUserByIdRspDTO> LOCAL_CACHE = Caffeine.newBuilder()
            .initialCapacity(10000) // 设置初始容量为 10000 个
            .maximumSize(10000) // 设置最大缓存大小为 10000 个
            .expireAfterWrite(1, TimeUnit.HOURS) // 设置缓存过期时间为 1 小时
            .build();

    /**
     * 更新用户信息
     */
    @Override
    public Response<?> updateUserInfo(UpdateUserInfoReqVO updateUserInfoReqVO) {
        UserDO userDO = new UserDO();
        // 设置当前需要更新的用户 ID
        userDO.setId(LoginUserContextHolder.getUserId());
        // 标识位：是否需要更新
        boolean needUpdate = false;

        // 头像
        MultipartFile avatarFile = updateUserInfoReqVO.getAvatar();
        if (Objects.nonNull(avatarFile)) {
            String avatar = ossRpcService.uploadFile(avatarFile);
            log.info("==> 调用 oss 服务成功，上传头像，url：{}", avatar);

            // 若上传头像失败，则抛出业务异常
            if (StringUtils.isBlank(avatar)) {
                throw new BizException(ResponseCodeEnum.UPLOAD_AVATAR_FAIL);
            }

            userDO.setAvatar(avatar);
            needUpdate = true;
        }

        // 昵称
        String nickname = updateUserInfoReqVO.getNickname();
        if (StringUtils.isNotBlank(nickname)) {
            Preconditions.checkArgument(ParamUtils.checkNickname(nickname), ResponseCodeEnum.NICK_NAME_VALID_FAIL.getErrorMessage());
            userDO.setNickname(nickname);
            needUpdate = true;
        }

        // 时光笔记号
        String lumenoteId = updateUserInfoReqVO.getLumenoteId();
        if (StringUtils.isNotBlank(lumenoteId)) {
            Preconditions.checkArgument(ParamUtils.checkLumenoteId(lumenoteId), ResponseCodeEnum.LUMENOTE_ID_VALID_FAIL.getErrorMessage());
            userDO.setLumenoteId(lumenoteId);
            needUpdate = true;
        }

        // 性别
        Integer sex = updateUserInfoReqVO.getSex();
        if (Objects.nonNull(sex)) {
            Preconditions.checkArgument(SexEnum.isValid(sex), ResponseCodeEnum.SEX_VALID_FAIL.getErrorMessage());
            userDO.setSex(sex);
            needUpdate = true;
        }

        // 生日
        LocalDate birthday = updateUserInfoReqVO.getBirthday();
        if (Objects.nonNull(birthday)) {
            userDO.setBirthday(birthday);
            needUpdate = true;
        }

        // 个人简介
        String introduction = updateUserInfoReqVO.getIntroduction();
        if (StringUtils.isNotBlank(introduction)) {
            Preconditions.checkArgument(ParamUtils.checkLength(introduction, 100), ResponseCodeEnum.INTRODUCTION_VALID_FAIL.getErrorMessage());
            userDO.setIntroduction(introduction);
            needUpdate = true;
        }

        // 背景图
        MultipartFile backgroundImgFile = updateUserInfoReqVO.getBackgroundImg();
        if (Objects.nonNull(backgroundImgFile)) {
            String backgroundImg = ossRpcService.uploadFile(backgroundImgFile);
            log.info("==> 调用 oss 服务成功，上传背景图，url：{}", backgroundImg);

            // 若上传背景图失败，则抛出业务异常
            if (StringUtils.isBlank(backgroundImg)) {
                throw new BizException(ResponseCodeEnum.UPLOAD_BACKGROUND_IMG_FAIL);
            }

            userDO.setBackgroundImg(backgroundImg);
            needUpdate = true;
        }

        if (needUpdate) {
            // 更新用户信息
            userDO.setUpdateTime(LocalDateTime.now());
            userDOMapper.updateByPrimaryKeySelective(userDO);

            // 清理用户信息缓存（本地 Caffeine + Redis）
            // 注意：不清缓存的话，findById / findByIds 会一直返回更新前的旧数据
            Long userId = userDO.getId();
            LOCAL_CACHE.invalidate(userId);
            redisTemplate.delete(RedisKeyConstants.buildUserInfoKey(userId));
            log.info("==> 用户信息已更新，清理缓存，userId: {}", userId);
        }
        return Response.success();
    }


    /**
     * 用户注册
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Response<Long> register(RegisterUserReqDTO registerUserReqDTO) {
        String phone = registerUserReqDTO.getPhone();

        // 先判断该手机号是否已被注册
        UserDO userDO1 = userDOMapper.selectByPhone(phone);

        // 若已注册，则直接返回用户 ID
        if (Objects.nonNull(userDO1)) {
            return Response.success(userDO1.getId());
        }

        // 否则注册新用户
        // RPC: 调用分布式 ID 生成服务生成时光笔记 ID
        String lumenoteId = distributedIdGeneratorRpcService.getLumenoteId();

        // RPC: 调用分布式 ID 生成服务生成用户 ID
        String userIdStr = distributedIdGeneratorRpcService.getUserId();
        Long userId = Long.valueOf(userIdStr);

        UserDO userDO = UserDO.builder()
                .id(userId)
                .phone(phone)
                .lumenoteId(lumenoteId)   // 自动生成时光笔记号 ID
                .nickname("小红薯" + lumenoteId)  // 自动生成昵称，如：小红薯10000
                .status(StatusEnum.ENABLE.getValue())   // 状态为启用
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .isDeleted(DeletedEnum.NO.getValue())   // 逻辑删除
                .build();

        // 添加入库
        userDOMapper.insert(userDO);

        // 给该用户分配一个默认角色
        UserRoleRelDO userRoleRelDO = UserRoleRelDO.builder()
                .userId(userId)
                .roleId(RoleConstants.COMMON_USER_ROLE_ID)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .isDeleted(DeletedEnum.NO.getValue())
                .build();
        userRoleRelDOMapper.insert(userRoleRelDO);

        RoleDO roleDO = roleDOMapper.selectByPrimaryKey(RoleConstants.COMMON_USER_ROLE_ID);

        // 将该用户的角色 ID 存入 Redis 中，指定初始容量为 1，这样可以减少在扩容时的性能开销
        ArrayList<String> roles = new ArrayList<>(1);
        roles.add(roleDO.getRoleKey());

        String userRolesKey = RedisKeyConstants.buildUserRoleKey(userId);
        redisTemplate.opsForValue().set(userRolesKey, JsonUtils.toJsonString(roles));

        return Response.success(userId);
    }

    /**
     * 根据手机号查询用户信息
     */
    @Override
    public Response<FindUserByPhoneRspDTO> findByPhone(FindUserByPhoneReqDTO findUserByPhoneReqDTO) {
        String phone = findUserByPhoneReqDTO.getPhone();

        // 根据手机号查询用户信息
        UserDO userDO = userDOMapper.selectByPhone(phone);

        // 判空
        if (Objects.isNull(userDO)) {
            throw new BizException(ResponseCodeEnum.USER_NOT_FOUND);
        }

        // 构建返参
        FindUserByPhoneRspDTO findUserByPhoneRspDTO = FindUserByPhoneRspDTO.builder()
                .id(userDO.getId())
                .password(userDO.getPassword())
                .build();

        return Response.success(findUserByPhoneRspDTO);
    }

    /**
     * 修改密码
     */
    @Override
    public Response<?> updatePassword(UpdateUserPasswordReqDTO updateUserPasswordReqDTO) {
        // 拿到当前登录用户 ID
        Long userId = LoginUserContextHolder.getUserId();

        UserDO userDO = UserDO.builder()
                .id(userId)
                .password(updateUserPasswordReqDTO.getEncodePassword())
                .updateTime(LocalDateTime.now())
                .build();
        // 更新密码
        userDOMapper.updateByPrimaryKeySelective(userDO);

        return Response.success();
    }

    /**
     * 根据用户 ID 查询用户信息
     */
    @Override
    public Response<FindUserByIdRspDTO> findById(FindUserByIdReqDTO findUserByIdReqDTO) {
        Long userId = findUserByIdReqDTO.getId();

        // 先从本地缓存中查询
        FindUserByIdRspDTO findUserByIdRspDTOLocalCache = LOCAL_CACHE.getIfPresent(userId);
        if (Objects.nonNull(findUserByIdRspDTOLocalCache)) {
            log.info("==> 命中了缓存：{}", findUserByIdRspDTOLocalCache);
            return Response.success(findUserByIdRspDTOLocalCache);
        }

        // 用户缓存 Redis Key
        String userInfoRedisKey = RedisKeyConstants.buildUserInfoKey(userId);

        // 从 Redis 缓存中查询
        String userInfoRedisValue = (String) redisTemplate.opsForValue().get(userInfoRedisKey);

        // 若 Redis 缓存中存在该用户信息
        if (StringUtils.isNotBlank(userInfoRedisValue)) {
            // 将存储的 Json 字符串转换成对象，并返回
            FindUserByIdRspDTO findUserByIdRspDTO = JsonUtils.parseObject(userInfoRedisValue, FindUserByIdRspDTO.class);
            // 异步线程将用户信息存入本地缓存
            threadPoolTaskExecutor.submit(() -> {
                // 只有非空值才写入本地缓存
                if (Objects.nonNull(findUserByIdRspDTO)) {
                    LOCAL_CACHE.put(userId, findUserByIdRspDTO);
                }
            });
            return Response.success(findUserByIdRspDTO);
        }

        // 否则, 从数据库中查询
        // 根据用户 ID 查询用户信息
        UserDO userDO = userDOMapper.selectByPrimaryKey(userId);

        // 判空
        if (Objects.isNull(userDO)) {
            threadPoolTaskExecutor.submit(() -> {
                // 往 Redis 中写入一个空值，防止缓存穿透
                //为了防止空值挤占缓存空间，过期时间设置为 1 分钟 + 随机秒数，将缓存过期时间打散，防止同一时间大量缓存失效，导致数据库压力太大
                long timeToLive = 60L + RandomUtil.randomInt(60);
                redisTemplate.opsForValue().set(userInfoRedisKey, "null", timeToLive, TimeUnit.SECONDS);
            });
            throw new BizException(ResponseCodeEnum.USER_NOT_FOUND);
        }

        // 构建返参
        FindUserByIdRspDTO findUserByIdRspDTO = FindUserByIdRspDTO.builder()
                .id(userDO.getId())
                .nickname(userDO.getNickname())
                .avatar(userDO.getAvatar())
                .introduction(userDO.getIntroduction())
                .backgroundImg(userDO.getBackgroundImg())
                .build();

        // 异步将用户信息存入 Redis 缓存，提升响应速度
        threadPoolTaskExecutor.submit(() -> {
            // 过期时间（保底1天 + 随机秒数，将缓存过期时间打散，防止同一时间大量缓存失效，导致数据库压力太大）
            long timeToLive = 60 * 60 * 24 + RandomUtil.randomInt(60 * 60 * 24);
            redisTemplate.opsForValue()
                    .set(userInfoRedisKey, JsonUtils.toJsonString(findUserByIdRspDTO), timeToLive, TimeUnit.SECONDS);
        });

        return Response.success(findUserByIdRspDTO);
    }

    /**
     * 批量根据用户 ID 查询用户信息
     */
    @Override
    public Response<List<FindUserByIdRspDTO>> findByIds(FindUsersByIdsReqDTO findUsersByIdsReqDTO) {
        // 需要查询的用户 ID 集合
        List<Long> userIds = findUsersByIdsReqDTO.getIds();

        // 构建 Redis Key 集合
        List<String> redisKeys = userIds.stream()
                .map(RedisKeyConstants::buildUserInfoKey)
                .toList();

        // 先从 Redis 缓存中查, multiGet 批量查询提升性能
        List<Object> redisValues = redisTemplate.opsForValue().multiGet(redisKeys);
        // 如果缓存中不为空
        if (CollUtil.isNotEmpty(redisValues)) {
            // 过滤掉为空的数据
            redisValues = redisValues.stream().filter(Objects::nonNull).toList();
        }

        // 返参
        List<FindUserByIdRspDTO> findUserByIdRspDTOS = Lists.newArrayList();

        // 将过滤后的缓存集合，转换为 DTO 返参实体类
        if (CollUtil.isNotEmpty(redisValues)) {
            findUserByIdRspDTOS = redisValues.stream()
                    .map(value -> JsonUtils.parseObject(String.valueOf(value), FindUserByIdRspDTO.class))
                    .collect(Collectors.toList());
        }

        // 如果被查询的用户信息，都在 Redis 缓存中, 则直接返回
        if (CollUtil.size(userIds) == CollUtil.size(findUserByIdRspDTOS)) {
            return Response.success(findUserByIdRspDTOS);
        }

        // 还有另外两种情况：一种是缓存里没有用户信息数据，还有一种是缓存里数据不全，需要从数据库中补充
        // 筛选出缓存里没有的用户数据，去查数据库
        List<Long> userIdsNeedQuery = null;

        if (CollUtil.isNotEmpty(findUserByIdRspDTOS)) {
            // 将 findUserInfoByIdRspDTOS 集合转 Map
            Map<Long, FindUserByIdRspDTO> map = findUserByIdRspDTOS.stream()
                    .collect(Collectors.toMap(FindUserByIdRspDTO::getId, p -> p));

            // 筛选出需要查 DB 的用户 ID
            userIdsNeedQuery = userIds.stream()
                    .filter(id -> Objects.isNull(map.get(id)))
                    .toList();
        } else { // 缓存中一条用户信息都没查到，则提交的用户 ID 集合都需要查数据库
            userIdsNeedQuery = userIds;
        }

        // 从数据库中批量查询
        List<UserDO> userDOS = userDOMapper.selectByIds(userIdsNeedQuery);

        List<FindUserByIdRspDTO> findUserByIdRspDTOS2 = null;

        // 若数据库查询的记录不为空
        if (CollUtil.isNotEmpty(userDOS)) {
            // DO 转 DTO
            findUserByIdRspDTOS2 = userDOS.stream()
                    .map(userDO -> FindUserByIdRspDTO.builder()
                            .id(userDO.getId())
                            .nickname(userDO.getNickname())
                            .avatar(userDO.getAvatar())
                            .introduction(userDO.getIntroduction())
                            .backgroundImg(userDO.getBackgroundImg())
                            .build())
                    .collect(Collectors.toList());

            // 异步线程将用户信息同步到 Redis 中
            List<FindUserByIdRspDTO> finalFindUserByIdRspDTOS = findUserByIdRspDTOS2;
            threadPoolTaskExecutor.submit(()->{
                // 执行 pipeline 操作
                redisTemplate.executePipelined(new SessionCallback<>() {
                    @Override
                    public Object execute(RedisOperations operations) throws DataAccessException {
                        for (FindUserByIdRspDTO finalFindUserByIdRspDTO : finalFindUserByIdRspDTOS) {
                            Long userId = finalFindUserByIdRspDTO.getId();

                            // 用户信息缓存 Redis Key
                            String userInfoRedisKey = RedisKeyConstants.buildUserInfoKey(userId);
                            String value= JsonUtils.toJsonString(finalFindUserByIdRspDTO);

                            // 过期时间（保底1天 + 随机秒数，将缓存过期时间打散，防止同一时间大量缓存失效，导致数据库压力太大）
                            long timeToLive = 60 * 60 * 24 + RandomUtil.randomInt(60 * 60 * 24);
                            operations.opsForValue().set(userInfoRedisKey , value, timeToLive, TimeUnit.SECONDS);
                        }
                        return null;
                    }
                });
            });
        }

        // 合并数据
        if (CollUtil.isNotEmpty(findUserByIdRspDTOS2)) {
            findUserByIdRspDTOS.addAll(findUserByIdRspDTOS2);
        }

        return Response.success(findUserByIdRspDTOS);
    }
}
