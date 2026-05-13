/** 文件上传 API：init/part/complete(分片) + direct(直传) + download/delete + knowledge-base list。 */
package com.localrag.controller;

import com.localrag.common.Result;
import com.localrag.common.dto.FileUploadedPayload;
import com.localrag.messaging.contract.MessageProducer;
import com.localrag.storage.config.MinioConfig;
import com.localrag.storage.contract.FileMetadataRepository;
import com.localrag.storage.contract.StorageService;
import com.localrag.storage.contract.UploadStateManager;
import com.localrag.storage.dto.*;
import com.localrag.storage.exception.StorageException;
import com.localrag.storage.model.FileMetadata;
import com.localrag.storage.model.UploadTask;
import com.localrag.vectorstore.contract.VectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/storage")
@RequiredArgsConstructor
public class StorageController {

    private final StorageService storageService;
    private final UploadStateManager uploadStateManager;
    private final FileMetadataRepository fileMetadataRepository;
    private final MinioConfig minioConfig;
    private final MessageProducer messageProducer;
    private final VectorStoreService vectorStoreService;

    @PostMapping("/upload/init")
    public Result<InitUploadResponse> initUpload(@RequestBody InitUploadRequest request) {
        String md5 = request.getFileMd5();

        // 上传链路: 秒传检查 → 断点续传检查 → 新上传(创建Redis任务)
        FileMetadata existing = fileMetadataRepository.findByMd5(md5);
        if (existing != null && existing.getStatus() != FileMetadata.Status.DELETED) {
            if (existing.getStatus() == FileMetadata.Status.EMBEDDED
                    && existing.getFileName().equals(request.getFileName())) {
                log.info("file already exists and fully processed (秒传): md5={}", md5);
                return Result.ok(InitUploadResponse.builder()
                        .md5(md5)
                        .fileName(existing.getFileName())
                        .fileSize(existing.getFileSize())
                        .status("EXIST")
                        .url("/api/storage/download/" + md5)
                        .build());
            }
            if (!existing.getFileName().equals(request.getFileName())) {
                log.info("different filename, overwriting: old={}, new={}",
                        existing.getFileName(), request.getFileName());
            } else {
                log.info("file needs re-processing: md5={}, status={}", md5, existing.getStatus());
            }
            storageService.removeObject(existing.getBucket(), existing.getObjectKey());
            fileMetadataRepository.delete(md5);
        }

        UploadTask existingTask = uploadStateManager.getByMd5(md5);
        if (existingTask != null) {
            log.info("resume upload: md5={}, uploaded={}", md5, existingTask.getUploadedParts().size());
            return Result.ok(InitUploadResponse.builder()
                    .md5(md5)
                    .partSize(existingTask.getPartSize())
                    .totalParts(existingTask.getTotalParts())
                    .uploadedParts(uploadStateManager.getUploadedParts(md5))
                    .status("RESUME")
                    .build());
        }

        String objectKey = md5 + "/" + request.getFileName();
        long partSize = Math.max(5 * 1024 * 1024L,
                (request.getFileSize() + 9999) / 10000);
        int totalParts = (int) ((request.getFileSize() + partSize - 1) / partSize);

        String bucket = minioConfig.getBucket();
        ensureBucket(bucket);

        UploadTask task = UploadTask.builder()
                .md5(md5)
                .fileName(request.getFileName())
                .fileSize(request.getFileSize())
                .bucket(bucket)
                .objectKey(objectKey)
                .totalParts(totalParts)
                .partSize((int) partSize)
                .status(UploadTask.Status.UPLOADING)
                .createdAt(LocalDateTime.now())
                .build();

        uploadStateManager.tryCreate(task);

        log.info("new upload: md5={}, totalParts={}, partSize={}MB", md5, totalParts, partSize / 1024 / 1024);
        return Result.ok(InitUploadResponse.builder()
                .md5(md5)
                .partSize((int) partSize)
                .totalParts(totalParts)
                .status("NEW")
                .build());
    }

    @PostMapping("/upload/part")
    public Result<UploadPartResponse> uploadPart(
            @RequestParam("md5") String md5,
            @RequestParam("partNumber") int partNumber,
            @RequestParam("file") MultipartFile file) {

        UploadTask task = uploadStateManager.getByMd5(md5);
        if (task == null) {
            throw new StorageException(404, "上传任务不存在，请先调用 init");
        }

        String etag;
        try (InputStream stream = file.getInputStream()) {
            etag = storageService.uploadPart(
                    task.getBucket(), task.getObjectKey(), partNumber, stream, file.getSize());
        } catch (Exception e) {
            throw new StorageException("分片上传失败");
        }

        uploadStateManager.markPartComplete(md5, partNumber);

        int uploadedCount = uploadStateManager.getUploadedParts(md5).size();
        log.debug("part uploaded: md5={}, part={}/{}, total={}",
                md5, partNumber, uploadedCount, task.getTotalParts());

        return Result.ok(UploadPartResponse.builder()
                .partNumber(partNumber)
                .etag(etag)
                .uploadedCount(uploadedCount)
                .totalParts(task.getTotalParts())
                .build());
    }

    @PostMapping("/upload/complete")
    public Result<InitUploadResponse> completeUpload(@RequestBody CompleteUploadRequest request) {
        String md5 = request.getMd5();

        UploadTask task = uploadStateManager.getByMd5(md5);
        if (task == null) {
            throw new StorageException(404, "上传任务不存在");
        }

        int uploadedCount = uploadStateManager.getUploadedParts(md5).size();
        if (uploadedCount != task.getTotalParts()) {
            throw new StorageException(400,
                    String.format("分片未全部上传: %d/%d", uploadedCount, task.getTotalParts()));
        }

        storageService.completeMultipartUpload(
                task.getBucket(), task.getObjectKey(), task.getTotalParts());

        uploadStateManager.remove(md5);

        FileMetadata metadata = FileMetadata.builder()
                .md5(md5)
                .fileName(task.getFileName())
                .fileSize(task.getFileSize())
                .objectKey(task.getObjectKey())
                .bucket(task.getBucket())
                .status(FileMetadata.Status.READY)
                .createdAt(LocalDateTime.now())
                .build();
        fileMetadataRepository.save(metadata);

        var userId = getUserId();
        messageProducer.send("document.uploaded", FileUploadedPayload.builder()
                .md5(md5)
                .fileName(task.getFileName())
                .fileSize(task.getFileSize())
                .objectKey(task.getObjectKey())
                .userId(userId)
                .build());

        log.info("upload complete: md5={}, fileName={}, size={}", md5, task.getFileName(), task.getFileSize());
        return Result.ok(InitUploadResponse.builder()
                .md5(md5)
                .fileName(task.getFileName())
                .fileSize(task.getFileSize())
                .status("COMPLETE")
                .url("/api/storage/download/" + md5)
                .build());
    }

    @GetMapping("/upload/{md5}/progress")
    public Result<UploadProgressResponse> getProgress(@PathVariable String md5) {
        UploadTask task = uploadStateManager.getByMd5(md5);
        if (task == null) {
            throw new StorageException(404, "上传任务不存在");
        }

        var uploadedParts = uploadStateManager.getUploadedParts(md5);
        long uploadedSize = (long) uploadedParts.size() * task.getPartSize();
        double percentage = (double) uploadedParts.size() / task.getTotalParts() * 100;

        return Result.ok(UploadProgressResponse.builder()
                .md5(md5)
                .totalParts(task.getTotalParts())
                .uploadedParts(uploadedParts)
                .uploadedSize(uploadedSize)
                .percentage(Math.round(percentage * 100.0) / 100.0)
                .build());
    }

    @DeleteMapping("/upload/{md5}")
    public Result<Void> abortUpload(@PathVariable String md5) {
        UploadTask task = uploadStateManager.getByMd5(md5);
        if (task != null) {
            storageService.deleteChunks(task.getBucket(), task.getObjectKey(), task.getTotalParts());
            uploadStateManager.remove(md5);
            log.info("upload aborted, chunks deleted: md5={}", md5);
        }
        return Result.ok();
    }

    @PostMapping("/upload/direct")
    public Result<InitUploadResponse> directUpload(@RequestParam("file") MultipartFile file) {
        String md5;
        try (InputStream stream = file.getInputStream()) {
            md5 = computeMd5(stream);
        } catch (Exception e) {
            throw new StorageException("计算文件MD5失败");
        }

        FileMetadata existing = fileMetadataRepository.findByMd5(md5);
        if (existing != null && existing.getStatus() == FileMetadata.Status.READY) {
            return Result.ok(InitUploadResponse.builder()
                    .md5(md5)
                    .fileName(existing.getFileName())
                    .fileSize(existing.getFileSize())
                    .status("EXIST")
                    .url("/api/storage/download/" + md5)
                    .build());
        }

        String objectKey = md5 + "/" + file.getOriginalFilename();
        String bucket = minioConfig.getBucket();
        ensureBucket(bucket);

        try (InputStream stream = file.getInputStream()) {
            storageService.putObject(bucket, objectKey, stream, file.getSize(),
                    file.getContentType() != null ? file.getContentType() : "application/octet-stream");
        } catch (Exception e) {
            throw new StorageException("文件上传失败");
        }

        FileMetadata metadata = FileMetadata.builder()
                .md5(md5)
                .fileName(file.getOriginalFilename())
                .fileSize(file.getSize())
                .objectKey(objectKey)
                .bucket(bucket)
                .contentType(file.getContentType())
                .status(FileMetadata.Status.READY)
                .createdAt(LocalDateTime.now())
                .build();
        fileMetadataRepository.save(metadata);

        var userId = getUserId();
        messageProducer.send("document.uploaded", FileUploadedPayload.builder()
                .md5(md5)
                .fileName(file.getOriginalFilename())
                .fileSize(file.getSize())
                .objectKey(objectKey)
                .userId(userId)
                .build());

        log.info("direct upload complete: md5={}, fileName={}", md5, file.getOriginalFilename());
        return Result.ok(InitUploadResponse.builder()
                .md5(md5)
                .fileName(file.getOriginalFilename())
                .fileSize(file.getSize())
                .status("COMPLETE")
                .url("/api/storage/download/" + md5)
                .build());
    }

    @GetMapping("/files")
    public Result<List<FileMetadata>> listFiles() {
        return Result.ok(fileMetadataRepository.findAll());
    }

    @GetMapping("/download/{md5}")
    public ResponseEntity<Void> download(@PathVariable String md5) {
        FileMetadata metadata = fileMetadataRepository.findByMd5(md5);
        if (metadata == null || metadata.getStatus() == FileMetadata.Status.DELETED) {
            return ResponseEntity.notFound().build();
        }

        String url = storageService.getPresignedUrl(
                metadata.getBucket(), metadata.getObjectKey(),
                minioConfig.getPresignedExpirySeconds());

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, url)
                .build();
    }

    @DeleteMapping("/file/{md5}")
    public Result<Void> deleteFile(@PathVariable String md5) {
        FileMetadata metadata = fileMetadataRepository.findByMd5(md5);
        if (metadata != null) {
            storageService.removeObject(metadata.getBucket(), metadata.getObjectKey());
            vectorStoreService.deleteByMd5(md5);
            metadata.setStatus(FileMetadata.Status.DELETED);
            fileMetadataRepository.save(metadata);
        }
        return Result.ok();
    }

    private void ensureBucket(String bucket) {
        if (!storageService.bucketExists(bucket)) {
            storageService.createBucket(bucket);
        }
    }

    private String getUserId() {
        try {
            var request = ((org.springframework.web.context.request.ServletRequestAttributes)
                    org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes())
                    .getRequest();
            Object userId = request.getAttribute("userId");
            return userId != null ? userId.toString() : "anonymous";
        } catch (Exception ignored) {
            return "anonymous";
        }
    }

    private String computeMd5(InputStream stream) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] buffer = new byte[8192];
        int read;
        while ((read = stream.read(buffer)) != -1) {
            md.update(buffer, 0, read);
        }
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
