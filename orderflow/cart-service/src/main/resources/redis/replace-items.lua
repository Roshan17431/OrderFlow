if redis.call('EXISTS', KEYS[1]) == 0 then
  return 0
end
local count = tonumber(ARGV[1])
local index = 2
for i = 1, count do
  redis.call('HSET', KEYS[1], ARGV[index], ARGV[index + 1])
  index = index + 2
end
local version = redis.call('HINCRBY', KEYS[1], '__version', 1)
redis.call('HSET', KEYS[1], '__updatedAt', ARGV[index])
redis.call('HSET', KEYS[1], '__expiresAt', ARGV[index + 1])
redis.call('EXPIRE', KEYS[1], tonumber(ARGV[index + 2]))
return version
