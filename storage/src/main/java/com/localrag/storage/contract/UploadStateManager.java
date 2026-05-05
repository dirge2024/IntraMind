package com.localrag.storage.contract;

import com.localrag.storage.model.UploadTask;

import java.util.List;

public interface UploadStateManager {

    boolean tryCreate(UploadTask task);

    UploadTask getByMd5(String md5);

    void savePartEtag(String md5, int partNumber, String etag);

    List<Integer> getUploadedParts(String md5);

    void remove(String md5);

    void cleanExpired(int maxAgeHours);
}
