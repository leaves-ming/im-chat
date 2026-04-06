package com.ming.imfileservice.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ming.imapicontract.common.ApiResponse;
import com.ming.imapicontract.message.CheckFileAccessRequest;
import com.ming.imapicontract.message.CheckFileAccessResponse;
import com.ming.imapicontract.message.MessageContentCodec;
import com.ming.imfileservice.config.FileStorageProperties;
import com.ming.imfileservice.dao.FileRecordDO;
import com.ming.imfileservice.dao.UploadTokenDO;
import com.ming.imfileservice.file.FileAccessDeniedException;
import com.ming.imfileservice.file.FileMetadata;
import com.ming.imfileservice.file.FileNotFoundBizException;
import com.ming.imfileservice.file.FileStorageService;
import com.ming.imfileservice.file.LocalFileStorageServiceImpl;
import com.ming.imfileservice.file.StoredFileResource;
import com.ming.imfileservice.mapper.FileRecordMapper;
import com.ming.imfileservice.mapper.UploadTokenMapper;
import com.ming.imfileservice.remote.message.MessageServiceClient;
import com.ming.imfileservice.service.FileService;
import com.ming.imfileservice.service.FileTokenBizException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * 文件域服务实现。
 */
@Service
public class FileServiceImpl implements FileService {

    private static final DateTimeFormatter STORAGE_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String TOKEN_STATUS_UPLOADED = "UPLOADED";
    private static final String TOKEN_STATUS_BOUND = "BOUND";

    private final FileRecordMapper fileRecordMapper;
    private final UploadTokenMapper uploadTokenMapper;
    private final MessageServiceClient messageServiceClient;
    private final FileStorageService fileStorageService;
    private final FileStorageProperties fileStorageProperties;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SecureRandom secureRandom = new SecureRandom();

    public FileServiceImpl(FileRecordMapper fileRecordMapper,
                           UploadTokenMapper uploadTokenMapper,
                           MessageServiceClient messageServiceClient,
                           FileStorageService fileStorageService,
                           FileStorageProperties fileStorageProperties,
                           StringRedisTemplate stringRedisTemplate) {
        this.fileRecordMapper = fileRecordMapper;
        this.uploadTokenMapper = uploadTokenMapper;
        this.messageServiceClient = messageServiceClient;
        this.fileStorageService = fileStorageService;
        this.fileStorageProperties = fileStorageProperties;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileMetadata store(Long ownerUserId,
                              String originalFileName,
                              String contentType,
                              long size,
                              InputStream inputStream) {
        if (ownerUserId == null || ownerUserId <= 0L) {
            throw new IllegalArgumentException("ownerUserId must be greater than 0");
        }
        if (size <= 0L) {
            throw new IllegalArgumentException("size must be greater than 0");
        }
        if (size > fileStorageProperties.getMaxFileSizeBytes()) {
            throw new IllegalArgumentException("file too large");
        }
        String sanitizedName = LocalFileStorageServiceImpl.sanitizeFileName(originalFileName);
        String normalizedContentType = normalizeContentType(contentType);
        validateUploadConstraints(sanitizedName, normalizedContentType);
        String fileId = UUID.randomUUID().toString().replace("-", "");
        String storageKey = buildStorageKey(sanitizedName);

        fileStorageService.store(storageKey, inputStream);

        FileRecordDO record = new FileRecordDO();
        record.setFileId(fileId);
        record.setOwnerUserId(ownerUserId);
        record.setContentType(normalizedContentType);
        record.setSize(size);
        record.setStorageKey(storageKey);
        record.setOriginalFileName(sanitizedName);
        fileRecordMapper.insert(record);

        String uploadToken = generateUploadToken();
        UploadTokenDO token = new UploadTokenDO();
        token.setUploadToken(uploadToken);
        token.setFileId(fileId);
        token.setOwnerUserId(ownerUserId);
        token.setStatus(TOKEN_STATUS_UPLOADED);
        token.setExpireAt(toDate(LocalDateTime.now().plusSeconds(fileStorageProperties.getUploadTokenExpireSeconds())));
        uploadTokenMapper.insert(token);

        return new FileMetadata(uploadToken, fileId, sanitizedName, normalizedContentType, size, buildPublicUrl(fileId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String consumeUploadTokenAndBuildFileMessageContent(String rawIncomingContent, Long senderUserId) {
        if (senderUserId == null || senderUserId <= 0L) {
            throw new FileAccessDeniedException("sender has no permission to reference file");
        }
        String uploadToken = MessageContentCodec.extractIncomingUploadToken(rawIncomingContent);
        UploadTokenDO token = uploadTokenMapper.findByUploadToken(uploadToken);
        validateTokenPrecondition(token, senderUserId);

        Date now = new Date();
        int updated = uploadTokenMapper.consumeUploadedToken(uploadToken, senderUserId, now);
        if (updated != 1) {
            UploadTokenDO refreshed = uploadTokenMapper.findByUploadToken(uploadToken);
            throw toTokenConsumeError(refreshed, senderUserId, now);
        }

        FileRecordDO record = fileRecordMapper.findByFileId(token.getFileId());
        if (record == null) {
            throw new FileTokenBizException("INVALID_PARAM", "file not found for uploadToken");
        }
        try {
            ObjectNode canonical = objectMapper.createObjectNode();
            canonical.put("fileId", record.getFileId());
            canonical.put("fileName", record.getOriginalFileName());
            canonical.put("size", record.getSize() == null ? 0L : record.getSize());
            canonical.put("contentType", normalizeContentType(record.getContentType()));
            canonical.put("url", buildPublicUrl(record.getFileId()));
            return objectMapper.writeValueAsString(canonical);
        } catch (Exception ex) {
            throw new IllegalStateException("serialize canonical file message failed", ex);
        }
    }

    @Override
    public DownloadUrlResult createDownloadUrl(Long requesterUserId, String fileId) {
        if (requesterUserId == null || requesterUserId <= 0L) {
            throw new FileAccessDeniedException("requester not authenticated");
        }
        FileRecordDO record = requiredRecord(fileId);
        if (!hasDownloadPermission(requesterUserId, record)) {
            throw new FileAccessDeniedException("file access forbidden");
        }
        long expireAt = currentEpochSecond() + fileStorageProperties.getDownloadSignExpireSeconds();
        String signature = sign(fileId, expireAt);
        return new DownloadUrlResult(buildDownloadUrl(fileId, expireAt, signature), expireAt);
    }

    @Override
    public StoredFileResource loadBySignedDownloadUrl(String fileId, long expireAt, String signature) {
        validateSignedDownloadRequest(fileId, expireAt, signature);
        FileRecordDO record = requiredRecord(fileId);
        StoredFileResource resource = fileStorageService.load(
                record.getStorageKey(),
                record.getOriginalFileName(),
                normalizeContentType(record.getContentType()));
        if (resource == null) {
            throw new FileNotFoundBizException("file not found");
        }
        consumeSignedDownloadOnceIfNeeded(signature, expireAt);
        return resource;
    }

    @Override
    public FileRecordDO findByFileId(String fileId) {
        if (fileId == null || fileId.isBlank()) {
            return null;
        }
        return fileRecordMapper.findByFileId(fileId);
    }

    private FileRecordDO requiredRecord(String fileId) {
        FileRecordDO record = fileRecordMapper.findByFileId(fileId);
        if (record == null) {
            throw new FileNotFoundBizException("file not found");
        }
        return record;
    }

    private boolean hasDownloadPermission(Long requesterUserId, FileRecordDO record) {
        if (fileStorageProperties.isAllowOwnerDownloadWithoutMessage()
                && Objects.equals(requesterUserId, record.getOwnerUserId())) {
            return true;
        }
        ApiResponse<CheckFileAccessResponse> response = messageServiceClient.checkFileAccess(
                new CheckFileAccessRequest(record.getFileId(), requesterUserId));
        return response != null && response.isSuccess() && response.getData() != null && response.getData().allowed();
    }

    private String normalizeContentType(String contentType) {
        return (contentType == null || contentType.isBlank()) ? "application/octet-stream" : contentType;
    }

    private String buildStorageKey(String fileName) {
        String ext = LocalFileStorageServiceImpl.extractExtension(fileName);
        String datePath = LocalDate.now().format(STORAGE_DATE);
        String suffix = ext.isBlank() ? "" : "." + ext.toLowerCase(Locale.ROOT);
        return datePath + "/" + UUID.randomUUID().toString().replace("-", "") + suffix;
    }

    private void validateUploadConstraints(String fileName, String contentType) {
        if (!fileStorageProperties.getAllowedContentTypes().isEmpty()
                && fileStorageProperties.getAllowedContentTypes().stream().noneMatch(item -> item.equalsIgnoreCase(contentType))) {
            throw new IllegalArgumentException("file type not allowed");
        }
        String ext = LocalFileStorageServiceImpl.extractExtension(fileName);
        if (!fileStorageProperties.getAllowedExtensions().isEmpty()
                && fileStorageProperties.getAllowedExtensions().stream().noneMatch(item -> item.equalsIgnoreCase(ext))) {
            throw new IllegalArgumentException("file type not allowed");
        }
    }

    private String buildPublicUrl(String fileId) {
        String prefix = normalizedFilePrefix();
        return prefix + "/" + fileId;
    }

    private String buildDownloadUrl(String fileId, long expireAt, String signature) {
        return FileApiPrefixHolder.signedDownloadPath(normalizedFilePrefix()) + "?fileId=" + fileId + "&exp=" + expireAt + "&sig=" + signature;
    }

    private void validateTokenPrecondition(UploadTokenDO token, Long senderUserId) {
        if (token == null) {
            throw new FileTokenBizException("INVALID_PARAM", "uploadToken not found");
        }
        if (!Objects.equals(token.getOwnerUserId(), senderUserId)) {
            throw new FileAccessDeniedException("sender has no permission to consume uploadToken");
        }
        Date now = new Date();
        if (token.getExpireAt() == null || !token.getExpireAt().after(now)) {
            throw new FileTokenBizException("INVALID_PARAM", "uploadToken expired");
        }
        if (TOKEN_STATUS_BOUND.equalsIgnoreCase(token.getStatus())) {
            throw new FileTokenBizException("TOKEN_ALREADY_BOUND", "uploadToken already bound");
        }
        if (!TOKEN_STATUS_UPLOADED.equalsIgnoreCase(token.getStatus())) {
            throw new FileTokenBizException("INVALID_PARAM", "uploadToken status invalid");
        }
    }

    private RuntimeException toTokenConsumeError(UploadTokenDO token, Long senderUserId, Date now) {
        if (token == null) {
            return new FileTokenBizException("INVALID_PARAM", "uploadToken not found");
        }
        if (!Objects.equals(token.getOwnerUserId(), senderUserId)) {
            return new FileAccessDeniedException("sender has no permission to consume uploadToken");
        }
        if (token.getExpireAt() == null || !token.getExpireAt().after(now)) {
            return new FileTokenBizException("INVALID_PARAM", "uploadToken expired");
        }
        if (TOKEN_STATUS_BOUND.equalsIgnoreCase(token.getStatus())) {
            return new FileTokenBizException("TOKEN_ALREADY_BOUND", "uploadToken already bound");
        }
        return new FileTokenBizException("INVALID_PARAM", "uploadToken invalid");
    }

    private void validateSignedDownloadRequest(String fileId, long expireAt, String signature) {
        if (fileId == null || fileId.isBlank() || expireAt <= 0L || signature == null || signature.isBlank()) {
            throw new FileAccessDeniedException("invalid download signature");
        }
        if (expireAt <= currentEpochSecond()) {
            throw new FileAccessDeniedException("download signature expired");
        }
        String expectedSignature = sign(fileId, expireAt);
        if (!constantTimeEquals(expectedSignature, signature)) {
            throw new FileAccessDeniedException("invalid download signature");
        }
    }

    private void consumeSignedDownloadOnceIfNeeded(String signature, long expireAt) {
        if (!fileStorageProperties.isDownloadSignOneTime()) {
            return;
        }
        if (stringRedisTemplate == null) {
            throw new IllegalStateException("redis unavailable for one-time download signature");
        }
        long ttlSeconds = Math.max(1L, expireAt - currentEpochSecond());
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(
                "im:file:download_sig:" + sha256Base64Url(signature),
                "1",
                Duration.ofSeconds(ttlSeconds));
        if (!Boolean.TRUE.equals(success)) {
            throw new FileAccessDeniedException("download signature already consumed");
        }
    }

    private String sign(String fileId, long expireAt) {
        String secret = fileStorageProperties.getDownloadSignSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("im.file.download-sign-secret must not be blank");
        }
        String payload = fileId + "\n" + expireAt;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("sign download url failed", ex);
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    private String sha256Base64Url(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("hash signature failed", ex);
        }
    }

    private String generateUploadToken() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private Date toDate(LocalDateTime localDateTime) {
        return java.sql.Timestamp.valueOf(localDateTime);
    }

    private long currentEpochSecond() {
        return System.currentTimeMillis() / 1000L;
    }

    private String normalizedFilePrefix() {
        String prefix = fileStorageProperties.getPublicUrlPrefix();
        if (prefix == null || prefix.isBlank()) {
            prefix = "/files";
        }
        if (!prefix.startsWith("/")) {
            prefix = "/" + prefix;
        }
        if (prefix.endsWith("/")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        return prefix;
    }

    private static final class FileApiPrefixHolder {

        private static String signedDownloadPath(String prefix) {
            return prefix + "/download";
        }
    }
}
