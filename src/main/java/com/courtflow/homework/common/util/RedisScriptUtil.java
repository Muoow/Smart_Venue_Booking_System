package com.courtflow.homework.common.util;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

@Component
@Slf4j
public class RedisScriptUtil {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private RedisScript<List> bitmapScript;
    private RedisScript<List> hashScript;

    @PostConstruct
    public void init() {
        try {
            String bitmapScriptContent = loadScript("bitmap.lua");
            String hashScriptContent = loadScript("hash.lua");

            this.bitmapScript = RedisScript.of(bitmapScriptContent, List.class);
            this.hashScript = RedisScript.of(hashScriptContent, List.class);

            log.info("Redis Lua scripts loaded successfully");
        } catch (IOException e) {
            log.error("Failed to load Redis Lua scripts", e);
            throw new RuntimeException("Failed to load Redis Lua scripts", e);
        }
    }

    /**
     * 为容量为1的资源执行bitmap扣库存脚本
     *
     * @param key Redis key
     * @param startSlotId 起始时间片ID
     * @param endSlotId 结束时间片ID
     * @param ttlSeconds TTL秒数
     * @return [1] 成功, [0, conflictSlotId] 失败
     */
    public List executeForBitmap(String key, Integer startSlotId, Integer endSlotId, Long ttlSeconds) {
        return redisTemplate.execute(
                bitmapScript,
                Collections.singletonList(key),
                startSlotId,
                endSlotId,
                ttlSeconds
        );
    }

    /**
     * 为容量>1的资源执行hash扣库存脚本
     *
     * @param key Redis key
     * @param startSlotId 起始时间片ID
     * @param endSlotId 结束时间片ID
     * @param maxCapacity 资源最大容量
     * @param delta 扣减数量
     * @param ttlSeconds TTL秒数
     * @return [1] 成功, [0, insufficientSlotId] 失败
     */
    public List executeForHash(String key, Integer startSlotId, Integer endSlotId,
                                Integer maxCapacity, Integer delta, Long ttlSeconds) {
        return redisTemplate.execute(
                hashScript,
                Collections.singletonList(key),
                startSlotId,
                endSlotId,
                maxCapacity,
                delta,
                ttlSeconds
        );
    }

    private String loadScript(String filename) throws IOException {
        ClassPathResource resource = new ClassPathResource("script/" + filename);
        byte[] bytes = Files.readAllBytes(Paths.get(resource.getFile().toURI()));
        return new String(bytes);
    }
}
