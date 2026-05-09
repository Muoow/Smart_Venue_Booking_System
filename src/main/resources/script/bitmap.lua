-- KEYS[1]: Bitmap key
-- ARGV[1]: 起始时间片ID (startSlotId)
-- ARGV[2]: 结束时间片ID (endSlotId)
-- ARGV[3]: TTL 秒数

local key = KEYS[1]
local startSlot = tonumber(ARGV[1])
local endSlot = tonumber(ARGV[2])
local ttl = tonumber(ARGV[3])

-- 1. 检查区间内是否有已被占用的位
for offset = startSlot, endSlot do
    if redis.call('GETBIT', key, offset) == 1 then
        return {0, offset}  -- 失败，返回冲突的 slotId
    end
end

-- 2. 全部空闲，批量设置为 1
for offset = startSlot, endSlot do
    redis.call('SETBIT', key, offset, 1)
end

-- 3. 刷新 TTL
redis.call('EXPIRE', key, ttl)

return {1}  -- 成功