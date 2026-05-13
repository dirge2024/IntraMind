/** UploadStateManager 的 Redis BitMap 实现。upload:{md5} Hash 存元数据，upload:{md5}:parts BitMap 存分片状态。 */
package com.localrag.storage.impl;

import com.localrag.storage.contract.UploadStateManager;
import com.localrag.storage.model.UploadTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;

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
            int totalParts = Integer.parseInt(totalPartsStr);
            Set<Integer> uploadedParts = readBitMapParts(md5, totalParts);
            return UploadTask.builder()
                    .md5(getStr(fields, "md5"))
                    .fileName(getStr(fields, "fileName"))
                    .fileSize(Long.parseLong(fileSizeStr))
                    .uploadId(getStr(fields, "uploadId"))
                    .bucket(getStr(fields, "bucket"))
                    .objectKey(getStr(fields, "objectKey"))
                    .totalParts(totalParts)
                    .status(UploadTask.Status.valueOf(statusStr))
                    .uploadedParts(uploadedParts)
                    .build();
        } catch (Exception e) {
            log.warn("corrupted upload task in Redis, removing: md5={}", md5, e);
            remove(md5);
            return null;
        }
    }

    @Override
    public void markPartComplete(String md5, int partNumber) {
        String key = KEY_PREFIX + md5 + PARTS_SUFFIX;
        redisTemplate.opsForValue().setBit(key, partNumber - 1, true);
        redisTemplate.expire(key, Duration.ofHours(TTL_HOURS));
    }

    @Override
    public List<Integer> getUploadedParts(String md5) {
        UploadTask task = getByMd5(md5);
        if (task == null) {
            return Collections.emptyList();
        }
        List<Integer> list = new ArrayList<>(readBitMapParts(md5, task.getTotalParts()));
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
        log.debug("cleanExpired skipped: Redis TTL handles this");
    }

    private Set<Integer> readBitMapParts(String md5, int totalParts) {
        String key = KEY_PREFIX + md5 + PARTS_SUFFIX;
        Set<Integer> parts = new HashSet<>();
        for (int i = 0; i < totalParts; i++) {
            Boolean bit = redisTemplate.opsForValue().getBit(key, i);
            if (Boolean.TRUE.equals(bit)) {
                parts.add(i + 1);
            }
        }
        return parts;
    }

    private String getStr(Map<Object, Object> fields, String key) {
        Object value = fields.get(key);
        return value != null ? value.toString() : "";
    }
}
