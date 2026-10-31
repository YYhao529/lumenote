-- LUA 脚本：自增量用户点赞数变更数据布隆过滤器

local key = KEYS[1]                    -- 操作的 Redis Key
local userId = ARGV[1] -- Redis Value   -- 用户 ID

-- 校验布隆过滤器是否存在
local exists = redis.call('EXISTS', key)
if exists == 0 then
    -- 创建布隆过滤器
    redis.call('BF.ADD', key, '')
    -- 设置过期时间，一天后过期
    redis.call("EXPIRE", key, 24 * 60 * 60)
end

-- 判断当前用户获得点赞数是否已经记录（1 为已经记录，0 为没有记录）
return redis.call('BF.EXISTS', key, userId)
