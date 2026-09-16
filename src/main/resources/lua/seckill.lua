local stock_key = KEYS[1]
local order_set_key = KEYS[2]
local begin_key = KEYS[3]
local end_key = KEYS[4]
local outbox_stream = KEYS[5]
local order_status_key = KEYS[6]

local voucher_id = ARGV[1]
local user_id = ARGV[2]
local order_id = ARGV[3]
local event_id = ARGV[4]
local created_at = ARGV[5]

local redis_time = redis.call('TIME')
local now = tonumber(redis_time[1]) * 1000 + math.floor(tonumber(redis_time[2]) / 1000)
local begin_at = tonumber(redis.call('GET', begin_key) or '0')
local end_at = tonumber(redis.call('GET', end_key) or '0')
local stock = tonumber(redis.call('GET', stock_key) or '-1')

if begin_at == 0 or end_at == 0 or stock < 0 then
    return 5
end
if now < begin_at then
    return 3
end
if now > end_at then
    return 4
end
if stock <= 0 then
    return 1
end
if redis.call('SISMEMBER', order_set_key, user_id) == 1 then
    return 2
end

redis.call('DECR', stock_key)
redis.call('SADD', order_set_key, user_id)
redis.call('SET', order_status_key, 'PENDING:' .. user_id, 'EX', 86400)
redis.call('XADD', outbox_stream, '*',
        'eventId', event_id,
        'eventVersion', '1',
        'orderId', order_id,
        'userId', user_id,
        'voucherId', voucher_id,
        'createdAt', created_at)
return 0
