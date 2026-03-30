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
import com.ming.imchatserver.file.LocalFileStorageService;
import com.ming.imchatserver.file.StoredFileResource;
import com.ming.imchatserver.mapper.FileRecordMapper;
import com.ming.imchatserver.mapper.GroupMessageMapper;
import com.ming.imchatserver.mapper.MessageMapper;
import com.ming.imchatserver.mapper.UploadTokenMapper;
import com.ming.imchatserver.message.MessageContentCodec;
import com.ming.imchatserver.service.FileService;
import com.ming.imchatserver.service.FileTokenBizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
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
 */
@Service
public class FileServiceImpl implements FileService {

    private static final DateTimeFormatter STORAGE_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String TOKEN_STATUS_UPLOADED = "UPLOADED";
    private static final String TOKEN_STATUS_BOUND = "BOUND";

    private final FileRecordMapper fileRecordMapper;
    private final UploadTokenMapper uploadTokenMapper;
    private final MessageMapper messageMapper;
    private final GroupMessageMapper groupMessageMapper;
    private final FileStorageService fileStorageService;
    private final FileStorageProperties fileStorageProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SecureRandom secureRandom = new SecureRandom();

    public FileServiceImpl(FileRecordMapper fileRecordMapper,
                           UploadTokenMapper uploadTokenMapper,
                           MessageMapper messageMapper,
                           GroupMessageMapper groupMessageMapper,
                           FileStorageService fileStorageService,
                           FileStorageProperties fileStorageProperties) {
        this.fileRecordMapper = fileRecordMapper;
        this.uploadTokenMapper = uploadTokenMapper;
        this.messageMapper = messageMapper;
        this.groupMessageMapper = groupMessageMapper;
        this.fileStorageService = fileStorageService;
        this.fileStorageProperties = fileStorageProperties;
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
        String sanitizedName = LocalFileStorageService.sanitizeFileName(originalFileName);
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
        Integer singleChatAccess = messageMapper.existsFileParticipant(record.getFileId(), requesterUserId);
        if (singleChatAccess != null && singleChatAccess > 0) {
            return true;
        }
        Integer groupAccess = groupMessageMapper.existsFileForActiveMember(record.getFileId(), requesterUserId);
        return groupAccess != null && groupAccess > 0;
    }

    private String normalizeContentType(String contentType) {
        return (contentType == null || contentType.isBlank()) ? "application/octet-stream" : contentType;
    }

    private String buildStorageKey(String fileName) {
        String ext = LocalFileStorageService.extractExtension(fileName);
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
}
