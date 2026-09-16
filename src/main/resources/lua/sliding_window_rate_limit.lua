local key = KEYS[1]
local window_ms = tonumber(ARGV[1]) * 1000
local limit = tonumber(ARGV[2])
local nonce = ARGV[3]
local redis_time = redis.call('TIME')
local now = tonumber(redis_time[1]) * 1000 + math.floor(tonumber(redis_time[2]) / 1000)

redis.call('ZREMRANGEBYSCORE', key, 0, now - window_ms)
local current = redis.call('ZCARD', key)
if current >= limit then
    return -1
end

redis.call('ZADD', key, now, tostring(now) .. '-' .. nonce)
redis.call('PEXPIRE', key, window_ms)
return limit - current - 1

