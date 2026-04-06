package com.ming.imchatserver.service.impl;

import com.ming.imapicontract.common.ApiResponse;
import com.ming.imapicontract.file.CreateDownloadUrlRequest;
import com.ming.imapicontract.file.CreateDownloadUrlResponse;
import com.ming.imapicontract.file.FileRecordDTO;
import com.ming.imapicontract.file.GetFileMetadataRequest;
import com.ming.imapicontract.file.StoreFileRequest;
import com.ming.imapicontract.file.StoreFileResponse;
import com.ming.imchatserver.dao.FileRecordDO;
import com.ming.imchatserver.file.FileAccessDeniedException;
import com.ming.imchatserver.file.FileMetadata;
import com.ming.imchatserver.file.FileNotFoundBizException;
import com.ming.imchatserver.file.StoredFileResource;
import com.ming.imchatserver.remote.file.FileServiceClient;
import com.ming.imchatserver.service.FileService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;

/**
 * 文件服务远程代理实现。
 */
@Service
public class FileServiceImpl implements FileService {

    private final FileServiceClient fileServiceClient;
    private final RestClient restClient;

    public FileServiceImpl(FileServiceClient fileServiceClient, RestClient.Builder loadBalancedRestClientBuilder) {
        this.fileServiceClient = fileServiceClient;
        this.restClient = loadBalancedRestClientBuilder.build();
    }

    @Override
    public FileMetadata store(Long ownerUserId,
                              String originalFileName,
                              String contentType,
                              long size,
                              InputStream inputStream) {
        try {
            byte[] bytes = StreamUtils.copyToByteArray(inputStream);
            StoreFileResponse response = unwrap(fileServiceClient.upload(
                    new StoreFileRequest(ownerUserId, originalFileName, contentType, size, bytes)));
            return new FileMetadata(
                    response.uploadToken(),
                    response.fileId(),
                    response.fileName(),
                    response.contentType(),
                    response.size(),
                    response.url());
        } catch (IOException ex) {
            throw new IllegalStateException("read upload stream failed", ex);
        }
    }

    @Override
    public String consumeUploadTokenAndBuildFileMessageContent(String rawIncomingContent, Long senderUserId) {
        throw new UnsupportedOperationException("uploadToken consume moved to im-message-service");
    }

    @Override
    public StoredFileResource loadAuthorizedFile(Long requesterUserId, String fileId) {
        DownloadUrlResult result = createDownloadUrl(requesterUserId, fileId);
        return loadBySignedDownloadUrl(fileId, result.expireAt(), queryValue(result.downloadUrl(), "sig"));
    }

    @Override
    public DownloadUrlResult createDownloadUrl(Long requesterUserId, String fileId) {
        CreateDownloadUrlResponse response = unwrap(fileServiceClient.createDownloadUrl(
                new CreateDownloadUrlRequest(requesterUserId, fileId)));
        return new DownloadUrlResult(response.downloadUrl(), response.expireAt());
    }

    @Override
    public StoredFileResource loadBySignedDownloadUrl(String fileId, long expireAt, String signature) {
        try {
            ResponseEntity<byte[]> response = restClient.get()
                    .uri("http://im-file-service/files/download?fileId={fileId}&exp={exp}&sig={sig}", fileId, expireAt, signature)
                    .accept(MediaType.ALL)
                    .retrieve()
                    .toEntity(byte[].class);
            byte[] body = response.getBody();
            if (body == null) {
                throw new FileNotFoundBizException("file not found");
            }
            String fileName = response.getHeaders().getContentDisposition().getFilename();
            if (fileName == null || fileName.isBlank()) {
                fileName = fileId;
            }
            String contentType = response.getHeaders().getContentType() == null
                    ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                    : response.getHeaders().getContentType().toString();
            Path tempFile = Files.createTempFile("im-file-download-", "-" + fileName);
            Files.write(tempFile, body);
            return new StoredFileResource(tempFile, fileName, contentType, body.length);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 403) {
                throw new FileAccessDeniedException("forbidden");
            }
            if (ex.getStatusCode().value() == 404) {
                throw new FileNotFoundBizException("not found");
            }
            throw ex;
        } catch (IOException ex) {
            throw new IllegalStateException("download file proxy failed", ex);
        }
    }

    @Override
    public FileRecordDO findByFileId(String fileId) {
        FileRecordDTO response = unwrap(fileServiceClient.getMetadata(new GetFileMetadataRequest(fileId)));
        if (response == null) {
            return null;
        }
        FileRecordDO record = new FileRecordDO();
        record.setId(response.id());
        record.setFileId(response.fileId());
        record.setOwnerUserId(response.ownerUserId());
        record.setContentType(response.contentType());
        record.setSize(response.size());
        record.setStorageKey(response.storageKey());
        record.setOriginalFileName(response.originalFileName());
        record.setCreatedAt(response.createdAt());
        return record;
    }

    private <T> T unwrap(ApiResponse<T> response) {
        if (response == null) {
            throw new IllegalStateException("file service response is null");
        }
        if (response.isSuccess()) {
            return response.getData();
        }
        String code = response.getCode();
        String message = response.getMessage();
        if ("FORBIDDEN".equals(code)) {
            throw new FileAccessDeniedException(message);
        }
        if ("NOT_FOUND".equals(code)) {
            throw new FileNotFoundBizException(message);
        }
        if ("INVALID_PARAM".equals(code) || "TOKEN_ALREADY_BOUND".equals(code)) {
            throw new IllegalArgumentException(message);
        }
        throw new IllegalStateException(message == null ? "file service unavailable" : message);
    }

    private String queryValue(String url, String key) {
        if (url == null || !url.contains("?")) {
            return null;
        }
        String query = url.substring(url.indexOf('?') + 1);
        for (String part : query.split("&")) {
            String[] segments = part.split("=", 2);
            if (segments.length == 2 && key.equals(segments[0])) {
                return segments[1];
            }
        }
        return null;
    }
}
