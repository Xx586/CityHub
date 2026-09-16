local stock_key = KEYS[1]
local order_set_key = KEYS[2]
local order_status_key = KEYS[3]
local user_id = ARGV[1]
local target_status = ARGV[2]

if redis.call('SISMEMBER', order_set_key, user_id) == 1 then
    redis.call('INCR', stock_key)
    redis.call('SREM', order_set_key, user_id)
end
redis.call('SET', order_status_key, target_status .. ':' .. user_id, 'EX', 86400)
return 1
