-- 操作的 Key
local key = KEYS[1]

-- 显式预留容量并开启扩容，避免默认容量 100 被打满
if redis.call("EXISTS", key) == 0 then
    redis.call("BF.RESERVE", key, 0.01, 100000, "EXPANSION", 2)
end

for i = 1, #ARGV - 1 do
    redis.call("BF.ADD", key, ARGV[i])
end

-- 最后一个参数为过期时间
local expireTime = ARGV[#ARGV]
-- 设置过期时间
redis.call("EXPIRE", key, expireTime)
return 0
