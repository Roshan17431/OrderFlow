local existing = redis.call('HEXISTS', KEYS[1], ARGV[1])
if existing == 0 then
  return 0
end
local version = redis.call('HINCRBY', KEYS[1], '__version', 1)
redis.call('HDEL', KEYS[1], ARGV[1])
local itemCount = tonumber(redis.call('HINCRBY', KEYS[1], '__itemCount', -1))
if itemCount <= 0 then
  redis.call('DEL', KEYS[1])
  return version
end
redis.call('HSET', KEYS[1], '__updatedAt', ARGV[2])
redis.call('HSET', KEYS[1], '__expiresAt', ARGV[3])
redis.call('EXPIRE', KEYS[1], tonumber(ARGV[4]))
return version
