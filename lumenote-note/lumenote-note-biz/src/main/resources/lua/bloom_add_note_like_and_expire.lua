local key = KEYS[1] -- 操作的 Key
local noteId = ARGV[1]  -- 笔记ID
local expireSeconds = ARGV[2]    -- 过期时间（秒）

-- 显式预留容量并开启扩容：裸 BF.ADD 默认容量只有 100 且不会扩容，数据一多就会写入失败
if redis.call("EXISTS", key) == 0 then
    redis.call("BF.RESERVE", key, 0.01, 100000, "EXPANSION", 2)
end

redis.call("BF.ADD", key, noteId)
-- 设置过期时间
redis.call("EXPIRE", key, expireSeconds)
return 0
