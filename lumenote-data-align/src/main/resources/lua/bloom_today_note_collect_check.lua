-- LUA 脚本：自增量笔记收藏、取消收藏变更数据布隆过滤器

local key = KEYS[1]                    -- 操作的 Redis Key
local noteId = ARGV[1] -- Redis Value   -- 笔记ID

-- 校验布隆过滤器是否存在
local exists = redis.call('EXISTS', key)
if exists == 0 then
    -- 显式预留容量并开启扩容，避免默认容量 100 被打满
    redis.call('BF.RESERVE', key, 0.01, 1000000, 'EXPANSION', 2)
    -- 设置过期时间，一天后过期
    redis.call("EXPIRE", key, 24 * 60 * 60)
end

-- 判断当前笔记收藏状态是否已经记录（1 为已经记录，0 为没有记录）
return redis.call('BF.EXISTS', key, noteId)
