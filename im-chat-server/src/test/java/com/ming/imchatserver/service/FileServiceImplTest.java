package com.ming.imchatserver.service;

import com.ming.im.apicontract.common.ApiResponse;
import com.ming.imapicontract.message.CheckFileAccessResponse;
import com.ming.imchatserver.config.FileStorageProperties;
import com.ming.imchatserver.dao.FileRecordDO;
import com.ming.imchatserver.file.FileAccessDeniedException;
import com.ming.imchatserver.file.StoredFileResource;
import com.ming.imchatserver.file.FileStorageService;
import com.ming.imchatserver.mapper.FileRecordMapper;
import com.ming.imchatserver.mapper.UploadTokenMapper;
import com.ming.imchatserver.remote.message.MessageServiceClient;
import com.ming.imchatserver.redis.RedisKeyFactory;
import com.ming.imchatserver.service.impl.IdempotencyServiceImpl;
import com.ming.imchatserver.service.impl.FileServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileServiceImplTest {

    @TempDir
    Path tempDir;

    @Test
    void createDownloadUrlShouldReturnSignedUrlAfterPermissionCheck() {
        FileRecordMapper fileRecordMapper = mock(FileRecordMapper.class);
        UploadTokenMapper uploadTokenMapper = mock(UploadTokenMapper.class);
        MessageServiceClient messageServiceClient = mock(MessageServiceClient.class);
        FileStorageService fileStorageService = mock(FileStorageService.class);
        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        IdempotencyService idempotencyService = new IdempotencyServiceImpl(stringRedisTemplate, new RedisKeyFactory());
        FileStorageProperties properties = new FileStorageProperties();
        properties.setDownloadSignSecret("secret-1");
        properties.setDownloadSignExpireSeconds(300L);

        FileRecordDO record = record("f_1", 2L);
        when(fileRecordMapper.findByFileId("f_1")).thenReturn(record);
        when(messageServiceClient.checkFileAccess(any()))
                .thenReturn(ApiResponse.success(new CheckFileAccessResponse(true)));

        FileServiceImpl service = new FileServiceImpl(
                fileRecordMapper, uploadTokenMapper, messageServiceClient, fileStorageService, properties, idempotencyService);

        FileService.DownloadUrlResult result = service.createDownloadUrl(9L, "f_1");

        assertTrue(result.downloadUrl().startsWith("/files/download?fileId=f_1&exp="));
        assertTrue(result.downloadUrl().contains("&sig="));
        assertTrue(result.expireAt() > System.currentTimeMillis() / 1000L);
    }

    @Test
    void loadBySignedDownloadUrlShouldRejectExpiredSignature() {
        FileRecordMapper fileRecordMapper = mock(FileRecordMapper.class);
        UploadTokenMapper uploadTokenMapper = mock(UploadTokenMapper.class);
        MessageServiceClient messageServiceClient = mock(MessageServiceClient.class);
        FileStorageService fileStorageService = mock(FileStorageService.class);
        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        IdempotencyService idempotencyService = new IdempotencyServiceImpl(stringRedisTemplate, new RedisKeyFactory());
        FileStorageProperties properties = new FileStorageProperties();
        properties.setDownloadSignSecret("secret-1");
        properties.setDownloadSignExpireSeconds(-1L);
        when(fileRecordMapper.findByFileId("f_1")).thenReturn(record("f_1", 2L));
        when(messageServiceClient.checkFileAccess(any()))
                .thenReturn(ApiResponse.success(new CheckFileAccessResponse(true)));

        FileServiceImpl service = new FileServiceImpl(
                fileRecordMapper, uploadTokenMapper, messageServiceClient, fileStorageService, properties, idempotencyService);

        FileService.DownloadUrlResult result = service.createDownloadUrl(9L, "f_1");

        assertThrows(FileAccessDeniedException.class,
                () -> service.loadBySignedDownloadUrl("f_1", queryLong(result.downloadUrl(), "exp"), queryValue(result.downloadUrl(), "sig")));
    }

    @Test
    void loadBySignedDownloadUrlShouldRejectSecondConsumeWhenOneTimeEnabled() throws Exception {
        FileRecordMapper fileRecordMapper = mock(FileRecordMapper.class);
        UploadTokenMapper uploadTokenMapper = mock(UploadTokenMapper.class);
        MessageServiceClient messageServiceClient = mock(MessageServiceClient.class);
        FileStorageService fileStorageService = mock(FileStorageService.class);
        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(true, false);
        IdempotencyService idempotencyService = new IdempotencyServiceImpl(stringRedisTemplate, new RedisKeyFactory());

        FileStorageProperties properties = new FileStorageProperties();
        properties.setDownloadSignSecret("secret-1");
        properties.setDownloadSignExpireSeconds(300L);
        properties.setDownloadSignOneTime(true);

        FileRecordDO record = record("f_1", 2L);
        when(fileRecordMapper.findByFileId("f_1")).thenReturn(record);
        when(messageServiceClient.checkFileAccess(any()))
                .thenReturn(ApiResponse.success(new CheckFileAccessResponse(true)));

        Path file = tempDir.resolve("note.txt");
        Files.writeString(file, "hello");
        StoredFileResource storedFileResource = new StoredFileResource(file, "note.txt", "text/plain", Files.size(file));
        when(fileStorageService.load("storage-key", "note.txt", "text/plain")).thenReturn(storedFileResource);

        FileServiceImpl service = new FileServiceImpl(
                fileRecordMapper, uploadTokenMapper, messageServiceClient, fileStorageService, properties, idempotencyService);

        FileService.DownloadUrlResult result = service.createDownloadUrl(9L, "f_1");
        long exp = queryLong(result.downloadUrl(), "exp");
        String sig = queryValue(result.downloadUrl(), "sig");

        StoredFileResource first = service.loadBySignedDownloadUrl("f_1", exp, sig);
        assertEquals("note.txt", first.fileName());
        assertThrows(FileAccessDeniedException.class, () -> service.loadBySignedDownloadUrl("f_1", exp, sig));
    }

    private FileRecordDO record(String fileId, Long ownerUserId) {
        FileRecordDO record = new FileRecordDO();
        record.setFileId(fileId);
        record.setOwnerUserId(ownerUserId);
        record.setStorageKey("storage-key");
        record.setOriginalFileName("note.txt");
        record.setContentType("text/plain");
        record.setSize(5L);
        return record;
    }

    private String queryValue(String url, String key) {
        String query = url.substring(url.indexOf('?') + 1);
        for (String part : query.split("&")) {
            String[] segments = part.split("=", 2);
            if (segments.length == 2 && key.equals(segments[0])) {
                return segments[1];
            }
        }
        return null;
    }

    private long queryLong(String url, String key) {
        return Long.parseLong(queryValue(url, key));
    }
}
