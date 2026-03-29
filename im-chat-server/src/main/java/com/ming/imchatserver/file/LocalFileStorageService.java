package com.ming.imchatserver.file;

import com.ming.imchatserver.config.FileStorageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.UUID;

/**
 * 本地文件存储实现。
 */
@Service
public class LocalFileStorageService implements FileStorageService {

    private final FileStorageProperties properties;

    public LocalFileStorageService(FileStorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public FileMetadata store(String fileName, String contentType, byte[] bytes) {
        String sanitizedName = sanitizeFileName(fileName);
        String normalizedContentType = normalizeContentType(contentType);
        String fileId = UUID.randomUUID().toString().replace("-", "");
        Path filePath = resolveFilePath(fileId, sanitizedName);
        try {
            Files.createDirectories(filePath.getParent());
            Files.write(filePath, bytes);
        } catch (IOException ex) {
            throw new IllegalStateException("store file failed: " + sanitizedName, ex);
        }
        return new FileMetadata(
                fileId,
                sanitizedName,
                normalizedContentType,
                bytes == null ? 0L : bytes.length,
                buildPublicUrl(fileId, sanitizedName)
        );
    }

    @Override
    public StoredFileResource load(String fileId, String fileName) {
        String sanitizedName = sanitizeFileName(fileName);
        Path filePath = resolveFilePath(fileId, sanitizedName);
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            return null;
        }
        try {
            String detected = Files.probeContentType(filePath);
            return new StoredFileResource(filePath, sanitizedName, normalizeContentType(detected), Files.size(filePath));
        } catch (IOException ex) {
            throw new IllegalStateException("load file failed: " + fileId, ex);
        }
    }

    public static String sanitizeFileName(String originalFileName) {
        String candidate = originalFileName == null ? "file" : Paths.get(originalFileName).getFileName().toString();
        candidate = candidate.replace("\\", "_").replace("/", "_");
        candidate = candidate.replaceAll("[\\p{Cntrl}]", "");
        candidate = candidate.replaceAll("\\s+", "_");
        candidate = candidate.replaceAll("[^A-Za-z0-9._-]", "_");
        if (candidate.isBlank()) {
            return "file";
        }
        return candidate.length() > 128 ? candidate.substring(candidate.length() - 128) : candidate;
    }

    public static String extractExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        int idx = fileName.lastIndexOf('.');
        if (idx < 0 || idx == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(idx + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizeContentType(String contentType) {
        return (contentType == null || contentType.isBlank()) ? "application/octet-stream" : contentType;
    }

    private String buildPublicUrl(String fileId, String fileName) {
        String prefix = properties.getPublicUrlPrefix();
        if (prefix == null || prefix.isBlank()) {
            prefix = "/files";
        }
        if (!prefix.startsWith("/")) {
            prefix = "/" + prefix;
        }
        if (prefix.endsWith("/")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        return prefix + "/" + fileId + "/" + fileName;
    }

    private Path resolveFilePath(String fileId, String fileName) {
        return Path.of(properties.getLocalBaseDir(), fileId, fileName);
    }
}
