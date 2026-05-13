/** 上传进度追踪接口，基于 Redis BitMap 记录分片上传状态，支持断点续传。 */
package com.localrag.storage.contract;

import com.localrag.storage.model.UploadTask;

import java.util.List;

public interface UploadStateManager {

    boolean tryCreate(UploadTask task);

    UploadTask getByMd5(String md5);

    void markPartComplete(String md5, int partNumber);

    List<Integer> getUploadedParts(String md5);

    void remove(String md5);

    void cleanExpired(int maxAgeHours);
}
