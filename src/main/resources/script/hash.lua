-- KEYS[1]: Hash key，如 "court:A:20260101"
-- ARGV[1]: 起始时间片ID (startSlotId)
-- ARGV[2]: 结束时间片ID (endSlotId)
-- ARGV[3]: 资源最大容量（从应用层传入，避免 Redis 反向查库）
-- ARGV[4]: 扣减数量（通常为 1）
-- ARGV[5]: TTL 秒数（当天剩余秒数）

local key = KEYS[1]
local startSlot = tonumber(ARGV[1])
local endSlot = tonumber(ARGV[2])
local maxCapacity = tonumber(ARGV[3])
local delta = tonumber(ARGV[4]) or 1
local ttl = tonumber(ARGV[5])

-- 1. 检查区间内所有时间片是否容量充足
for id = startSlot, endSlot do
    local field = "slot_" .. id
    local remain = redis.call('HGET', key, field)

    if remain == false then
        -- 首次访问：初始化为最大容量
        remain = maxCapacity
        redis.call('HSET', key, field, remain)
    else
        remain = tonumber(remain)
    end

    if remain < delta then
        -- 容量不足，整体失败（已做的初始化不会回滚，但无副作用）
        return {0, id}  -- 返回失败及第一个不足的时间片ID
    end
end

-- 2. 全部充足，执行扣减
for id = startSlot, endSlot do
    local field = "slot_" .. id
    redis.call('HINCRBY', key, field, -delta)
end

-- 3. 刷新 TTL（每次成功预扣都续期至当天结束）
redis.call('EXPIRE', key, ttl)

return {1}  -- 成功