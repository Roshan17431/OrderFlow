local existingJson = redis.call('HGET', KEYS[1], ARGV[1])
if not existingJson then
  return -4
end
local currentCurrency = redis.call('HGET', KEYS[1], '__currency')
if currentCurrency ~= ARGV[2] then
  return -1
end
local incoming = cjson.decode(ARGV[3])
if incoming.quantity < 1 or incoming.quantity > tonumber(ARGV[4]) then
  return -2
end
local existing = cjson.decode(existingJson)
incoming.addedAt = existing.addedAt
local version = redis.call('HINCRBY', KEYS[1], '__version', 1)
redis.call('HSET', KEYS[1], ARGV[1], cjson.encode(incoming))
redis.call('HSET', KEYS[1], '__updatedAt', ARGV[5])
redis.call('HSET', KEYS[1], '__expiresAt', ARGV[6])
redis.call('EXPIRE', KEYS[1], tonumber(ARGV[7]))
return version
