/** UploadStateManager 的 Redis 实现。key=upload:{md5} 存任务元数据，key=upload:{md5}:parts 存分片 ETag，TTL 24h。 */
package com.localrag.storage.impl;

import com.localrag.storage.contract.UploadStateManager;
import com.localrag.storage.model.UploadTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisUploadStateManager implements UploadStateManager {

    private static final String KEY_PREFIX = "upload:";
    private static final String PARTS_SUFFIX = ":parts";
    private static final int TTL_HOURS = 24;

    private final RedisTemplate<String, String> redisTemplate;

    @Override
    public boolean tryCreate(UploadTask task) {
        String key = KEY_PREFIX + task.getMd5();
        Boolean ok = redisTemplate.opsForHash().putIfAbsent(key, "md5", task.getMd5());
        if (Boolean.TRUE.equals(ok)) {
            Map<String, String> fields = new HashMap<>();
            fields.put("md5", task.getMd5());
            fields.put("fileName", task.getFileName());
            fields.put("fileSize", String.valueOf(task.getFileSize()));
            fields.put("bucket", task.getBucket());
            fields.put("objectKey", task.getObjectKey());
            fields.put("totalParts", String.valueOf(task.getTotalParts()));
            fields.put("status", task.getStatus().name());
            redisTemplate.opsForHash().putAll(key, fields);
            redisTemplate.expire(key, Duration.ofHours(TTL_HOURS));
            log.info("upload task created: md5={}", task.getMd5());
            return true;
        }
        log.info("upload task already exists: md5={}", task.getMd5());
        return false;
    }

    @Override
    public UploadTask getByMd5(String md5) {
        String key = KEY_PREFIX + md5;
        Map<Object, Object> fields = redisTemplate.opsForHash().entries(key);
        if (fields.isEmpty()) {
            return null;
        }

        String fileSizeStr = getStr(fields, "fileSize");
        String totalPartsStr = getStr(fields, "totalParts");
        String statusStr = getStr(fields, "status");

        if (fileSizeStr.isEmpty() || totalPartsStr.isEmpty() || statusStr.isEmpty()) {
            log.warn("incomplete upload task in Redis, removing: md5={}", md5);
            remove(md5);
            return null;
        }

        try {
            return UploadTask.builder()
                    .md5(getStr(fields, "md5"))
                    .fileName(getStr(fields, "fileName"))
                    .fileSize(Long.parseLong(fileSizeStr))
                    .uploadId(getStr(fields, "uploadId"))
                    .bucket(getStr(fields, "bucket"))
                    .objectKey(getStr(fields, "objectKey"))
                    .totalParts(Integer.parseInt(totalPartsStr))
                    .status(UploadTask.Status.valueOf(statusStr))
                    .uploadedParts(getPartsMap(md5))
                    .build();
        } catch (Exception e) {
            log.warn("corrupted upload task in Redis, removing: md5={}", md5, e);
            remove(md5);
            return null;
        }
    }

    @Override
    public void savePartEtag(String md5, int partNumber, String etag) {
        String key = KEY_PREFIX + md5 + PARTS_SUFFIX;
        redisTemplate.opsForHash().put(key, String.valueOf(partNumber), etag);
        redisTemplate.expire(key, Duration.ofHours(TTL_HOURS));
    }

    @Override
    public List<Integer> getUploadedParts(String md5) {
        Map<Integer, String> parts = getPartsMap(md5);
        List<Integer> list = new ArrayList<>(parts.keySet());
        Collections.sort(list);
        return list;
    }

    @Override
    public void remove(String md5) {
        redisTemplate.delete(KEY_PREFIX + md5);
        redisTemplate.delete(KEY_PREFIX + md5 + PARTS_SUFFIX);
        log.info("upload task removed: md5={}", md5);
    }

    @Override
    public void cleanExpired(int maxAgeHours) {
        // Redis TTL handles expiration automatically
        log.debug("cleanExpired skipped: Redis TTL handles this");
    }

    private Map<Integer, String> getPartsMap(String md5) {
        String key = KEY_PREFIX + md5 + PARTS_SUFFIX;
        Map<Object, Object> raw = redisTemplate.opsForHash().entries(key);
        return raw.entrySet().stream()
                .collect(Collectors.toMap(
                        e -> Integer.parseInt(e.getKey().toString()),
                        e -> String.valueOf(e.getValue())
                ));
    }

    private String getStr(Map<Object, Object> fields, String key) {
        Object value = fields.get(key);
        return value != null ? value.toString() : "";
    }
}
