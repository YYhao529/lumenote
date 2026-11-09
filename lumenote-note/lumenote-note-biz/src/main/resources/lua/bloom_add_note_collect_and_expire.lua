local key = KEYS[1] -- 操作的 Key
local noteId = ARGV[1]  -- 笔记ID
local expireSeconds = ARGV[2]    -- 过期时间（秒）

-- 显式预留容量并开启扩容，避免默认容量 100 被打满
if redis.call("EXISTS", key) == 0 then
    redis.call("BF.RESERVE", key, 0.01, 100000, "EXPANSION", 2)
end

redis.call("BF.ADD", key, noteId)

-- 设置过期时间
redis.call("EXPIRE", key, expireSeconds)
return 0
