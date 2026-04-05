package com.ming.imchatserver.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ming.imchatserver.config.FileStorageProperties;
import com.ming.imchatserver.dao.FileRecordDO;
import com.ming.imchatserver.dao.UploadTokenDO;
import com.ming.imchatserver.file.FileAccessDeniedException;
import com.ming.imchatserver.file.FileMetadata;
import com.ming.imchatserver.file.FileNotFoundBizException;
import com.ming.imchatserver.file.FileStorageService;
import com.ming.imchatserver.file.LocalFileStorageServiceImpl;
import com.ming.imchatserver.file.StoredFileResource;
import com.ming.imchatserver.mapper.FileRecordMapper;
import com.ming.imchatserver.mapper.UploadTokenMapper;
import com.ming.imchatserver.message.MessageContentCodec;
import com.ming.imchatserver.remote.message.MessageServiceClient;
import com.ming.imchatserver.service.FileService;
import com.ming.imchatserver.service.FileTokenBizException;
import com.ming.imchatserver.service.IdempotencyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.security.SecureRandom;
import java.util.UUID;

/**
 * 文件服务实现。
 * @author ming
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
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SecureRandom secureRandom = new SecureRandom();

    public FileServiceImpl(FileRecordMapper fileRecordMapper,
                           UploadTokenMapper uploadTokenMapper,
                           MessageServiceClient messageServiceClient,
                           FileStorageService fileStorageService,
                           FileStorageProperties fileStorageProperties,
                           IdempotencyService idempotencyService) {
        this.fileRecordMapper = fileRecordMapper;
        this.uploadTokenMapper = uploadTokenMapper;
        this.messageServiceClient = messageServiceClient;
        this.fileStorageService = fileStorageService;
        this.fileStorageProperties = fileStorageProperties;
        this.idempotencyService = idempotencyService;
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
        String sanitizedName = LocalFileStorageServiceImpl.sanitizeFileName(originalFileName);
        String normalizedContentType = normalizeContentType(contentType);
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
        token.setBoundAt(null);
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
    public StoredFileResource loadAuthorizedFile(Long requesterUserId, String fileId) {
        if (requesterUserId == null || requesterUserId <= 0L) {
            throw new FileAccessDeniedException("requester not authenticated");
        }
        FileRecordDO record = fileRecordMapper.findByFileId(fileId);
        if (record == null) {
            throw new FileNotFoundBizException("file not found");
        }
        if (!hasDownloadPermission(requesterUserId, record)) {
            throw new FileAccessDeniedException("file access forbidden");
        }
        StoredFileResource resource = fileStorageService.load(record.getStorageKey(), record.getOriginalFileName(), normalizeContentType(record.getContentType()));
        if (resource == null) {
            throw new FileNotFoundBizException("file not found");
        }
        return resource;
    }

    @Override
    public DownloadUrlResult createDownloadUrl(Long requesterUserId, String fileId) {
        if (requesterUserId == null || requesterUserId <= 0L) {
            throw new FileAccessDeniedException("requester not authenticated");
        }
        FileRecordDO record = fileRecordMapper.findByFileId(fileId);
        if (record == null) {
            throw new FileNotFoundBizException("file not found");
        }
        if (!hasDownloadPermission(requesterUserId, record)) {
            throw new FileAccessDeniedException("file access forbidden");
        }
        long expireAt = currentEpochSecond() + fileStorageProperties.getDownloadSignExpireSeconds();
        String signature = sign(fileId, expireAt);
        String downloadUrl = buildDownloadUrl(fileId, expireAt, signature);
        return new DownloadUrlResult(downloadUrl, expireAt);
    }

    @Override
    public StoredFileResource loadBySignedDownloadUrl(String fileId, long expireAt, String signature) {
        validateSignedDownloadRequest(fileId, expireAt, signature);

        FileRecordDO record = fileRecordMapper.findByFileId(fileId);
        if (record == null) {
            throw new FileNotFoundBizException("file not found");
        }
        StoredFileResource resource = fileStorageService.load(record.getStorageKey(), record.getOriginalFileName(), normalizeContentType(record.getContentType()));
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

    private boolean hasDownloadPermission(Long requesterUserId, FileRecordDO record) {
        if (fileStorageProperties.isAllowOwnerDownloadWithoutMessage()
                && Objects.equals(requesterUserId, record.getOwnerUserId())) {
            return true;
        }
        var response = messageServiceClient.checkFileAccess(
                new com.ming.imapicontract.message.CheckFileAccessRequest(record.getFileId(), requesterUserId));
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

    private String buildPublicUrl(String fileId) {
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
        return prefix + "/" + fileId;
    }

    private String buildDownloadUrl(String fileId, long expireAt, String signature) {
        return normalizedFilePrefix() + "/download?fileId=" + fileId + "&exp=" + expireAt + "&sig=" + signature;
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

    private String generateUploadToken() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private Date toDate(LocalDateTime localDateTime) {
        return java.sql.Timestamp.valueOf(localDateTime);
    }

    private void validateSignedDownloadRequest(String fileId, long expireAt, String signature) {
        if (fileId == null || fileId.isBlank() || expireAt <= 0L || signature == null || signature.isBlank()) {
            throw new FileAccessDeniedException("invalid download signature");
        }
        long now = currentEpochSecond();
        if (expireAt <= now) {
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
        long ttlSeconds = Math.max(1L, expireAt - currentEpochSecond());
        if (idempotencyService == null) {
            throw new IllegalStateException("idempotency service unavailable for one-time download signature");
        }
        if (!idempotencyService.consumeOnce("download_sig", sha256Base64Url(signature), java.time.Duration.ofSeconds(ttlSeconds))) {
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
}
