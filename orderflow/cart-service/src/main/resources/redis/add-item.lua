local currentCurrency = redis.call('HGET', KEYS[1], '__currency')
if currentCurrency and currentCurrency ~= ARGV[2] then
  return -1
end

local existingJson = redis.call('HGET', KEYS[1], ARGV[1])
local incoming = cjson.decode(ARGV[3])
if incoming.quantity < 1 or incoming.quantity > tonumber(ARGV[4]) then
  return -2
end
if existingJson then
  local existing = cjson.decode(existingJson)
  local quantity = existing.quantity + incoming.quantity
  if quantity > tonumber(ARGV[4]) then
    return -2
  end
  incoming.quantity = quantity
  incoming.addedAt = existing.addedAt
else
  local itemCount = tonumber(redis.call('HGET', KEYS[1], '__itemCount') or '0')
  if itemCount >= tonumber(ARGV[5]) then
    return -3
  end
  redis.call('HSET', KEYS[1], '__itemCount', itemCount + 1)
end

if not currentCurrency then
  redis.call('HSET', KEYS[1], '__currency', ARGV[2])
  redis.call('HSET', KEYS[1], '__createdAt', ARGV[6])
end
local version = redis.call('HINCRBY', KEYS[1], '__version', 1)
redis.call('HSET', KEYS[1], ARGV[1], cjson.encode(incoming))
redis.call('HSET', KEYS[1], '__updatedAt', ARGV[6])
redis.call('HSET', KEYS[1], '__expiresAt', ARGV[7])
redis.call('EXPIRE', KEYS[1], tonumber(ARGV[8]))
return version
