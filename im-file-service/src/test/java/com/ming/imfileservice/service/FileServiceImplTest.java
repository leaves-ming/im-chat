package com.ming.imfileservice.service;

import com.ming.imapicontract.common.ApiResponse;
import com.ming.imapicontract.message.CheckFileAccessResponse;
import com.ming.imfileservice.config.FileStorageProperties;
import com.ming.imfileservice.dao.FileRecordDO;
import com.ming.imfileservice.dao.UploadTokenDO;
import com.ming.imfileservice.file.FileAccessDeniedException;
import com.ming.imfileservice.file.StoredFileResource;
import com.ming.imfileservice.file.FileStorageService;
import com.ming.imfileservice.mapper.FileRecordMapper;
import com.ming.imfileservice.mapper.UploadTokenMapper;
import com.ming.imfileservice.remote.message.MessageServiceClient;
import com.ming.imfileservice.service.impl.FileServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileServiceImplTest {

    @TempDir
    Path tempDir;

    @Test
    void storeShouldPersistMetadataAndReturnUploadToken() {
        FileRecordMapper fileRecordMapper = mock(FileRecordMapper.class);
        UploadTokenMapper uploadTokenMapper = mock(UploadTokenMapper.class);
        MessageServiceClient messageServiceClient = mock(MessageServiceClient.class);
        FileStorageService fileStorageService = mock(FileStorageService.class);
        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        FileStorageProperties properties = new FileStorageProperties();
        properties.setAllowedContentTypes(java.util.List.of("text/plain"));
        properties.setAllowedExtensions(java.util.List.of("txt"));

        FileServiceImpl service = new FileServiceImpl(
                fileRecordMapper, uploadTokenMapper, messageServiceClient, fileStorageService, properties, stringRedisTemplate);

        var result = service.store(1L, "note.txt", "text/plain", 5L, new ByteArrayInputStream("hello".getBytes()));

        assertEquals("note.txt", result.getFileName());
        assertEquals("text/plain", result.getContentType());
        assertTrue(result.getUploadToken() != null && !result.getUploadToken().isBlank());
        verify(fileRecordMapper).insert(any());
        verify(uploadTokenMapper).insert(any());
    }

    @Test
    void consumeUploadTokenShouldBuildCanonicalFileContent() {
        FileRecordMapper fileRecordMapper = mock(FileRecordMapper.class);
        UploadTokenMapper uploadTokenMapper = mock(UploadTokenMapper.class);
        MessageServiceClient messageServiceClient = mock(MessageServiceClient.class);
        FileStorageService fileStorageService = mock(FileStorageService.class);
        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        FileStorageProperties properties = new FileStorageProperties();

        UploadTokenDO token = new UploadTokenDO();
        token.setUploadToken("up-1");
        token.setOwnerUserId(1L);
        token.setFileId("f_1");
        token.setStatus("UPLOADED");
        token.setExpireAt(new Date(System.currentTimeMillis() + 60_000));
        when(uploadTokenMapper.findByUploadToken("up-1")).thenReturn(token);
        when(uploadTokenMapper.consumeUploadedToken(eq("up-1"), eq(1L), any())).thenReturn(1);
        when(fileRecordMapper.findByFileId("f_1")).thenReturn(record("f_1", 1L));

        FileServiceImpl service = new FileServiceImpl(
                fileRecordMapper, uploadTokenMapper, messageServiceClient, fileStorageService, properties, stringRedisTemplate);

        String canonical = service.consumeUploadTokenAndBuildFileMessageContent("{\"uploadToken\":\"up-1\"}", 1L);

        assertTrue(canonical.contains("\"fileId\":\"f_1\""));
        assertTrue(canonical.contains("\"fileName\":\"note.txt\""));
    }

    @Test
    void consumeUploadTokenShouldRejectAlreadyBoundToken() {
        FileRecordMapper fileRecordMapper = mock(FileRecordMapper.class);
        UploadTokenMapper uploadTokenMapper = mock(UploadTokenMapper.class);
        MessageServiceClient messageServiceClient = mock(MessageServiceClient.class);
        FileStorageService fileStorageService = mock(FileStorageService.class);
        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        FileStorageProperties properties = new FileStorageProperties();

        UploadTokenDO token = new UploadTokenDO();
        token.setUploadToken("up-1");
        token.setOwnerUserId(1L);
        token.setStatus("BOUND");
        token.setExpireAt(new Date(System.currentTimeMillis() + 60_000));
        when(uploadTokenMapper.findByUploadToken("up-1")).thenReturn(token);

        FileServiceImpl service = new FileServiceImpl(
                fileRecordMapper, uploadTokenMapper, messageServiceClient, fileStorageService, properties, stringRedisTemplate);

        FileTokenBizException ex = assertThrows(FileTokenBizException.class,
                () -> service.consumeUploadTokenAndBuildFileMessageContent("{\"uploadToken\":\"up-1\"}", 1L));
        assertEquals("TOKEN_ALREADY_BOUND", ex.getCode());
    }

    @Test
    void createDownloadUrlShouldReturnSignedUrlAfterPermissionCheck() {
        FileRecordMapper fileRecordMapper = mock(FileRecordMapper.class);
        UploadTokenMapper uploadTokenMapper = mock(UploadTokenMapper.class);
        MessageServiceClient messageServiceClient = mock(MessageServiceClient.class);
        FileStorageService fileStorageService = mock(FileStorageService.class);
        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        FileStorageProperties properties = new FileStorageProperties();
        properties.setDownloadSignSecret("secret-1");
        properties.setDownloadSignExpireSeconds(300L);

        when(fileRecordMapper.findByFileId("f_1")).thenReturn(record("f_1", 2L));
        when(messageServiceClient.checkFileAccess(any()))
                .thenReturn(ApiResponse.success(new CheckFileAccessResponse(true)));

        FileServiceImpl service = new FileServiceImpl(
                fileRecordMapper, uploadTokenMapper, messageServiceClient, fileStorageService, properties, stringRedisTemplate);

        FileService.DownloadUrlResult result = service.createDownloadUrl(9L, "f_1");

        assertTrue(result.downloadUrl().startsWith("/files/download?fileId=f_1&exp="));
        assertTrue(result.downloadUrl().contains("&sig="));
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

        FileStorageProperties properties = new FileStorageProperties();
        properties.setDownloadSignSecret("secret-1");
        properties.setDownloadSignExpireSeconds(300L);
        properties.setDownloadSignOneTime(true);

        when(fileRecordMapper.findByFileId("f_1")).thenReturn(record("f_1", 2L));
        when(messageServiceClient.checkFileAccess(any()))
                .thenReturn(ApiResponse.success(new CheckFileAccessResponse(true)));

        Path file = tempDir.resolve("note.txt");
        Files.writeString(file, "hello");
        when(fileStorageService.load("storage-key", "note.txt", "text/plain"))
                .thenReturn(new StoredFileResource(file, "note.txt", "text/plain", Files.size(file)));

        FileServiceImpl service = new FileServiceImpl(
                fileRecordMapper, uploadTokenMapper, messageServiceClient, fileStorageService, properties, stringRedisTemplate);

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
