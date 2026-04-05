package com.ming.imfileservice.file;

import com.ming.imfileservice.config.FileStorageProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/**
 * 本地文件存储实现。
 */
@Service
public class LocalFileStorageServiceImpl implements FileStorageService {

    private static final String UNIX_PATH_SEPARATOR = "/";
    private static final String WINDOWS_PATH_SEPARATOR = "\\";

    private final FileStorageProperties properties;

    public LocalFileStorageServiceImpl(FileStorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public void store(String storageKey, InputStream inputStream) {
        Path filePath = resolveStoragePath(storageKey);
        try {
            Files.createDirectories(filePath.getParent());
            Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new IllegalStateException("store file failed: " + storageKey, ex);
        }
    }

    @Override
    public StoredFileResource load(String storageKey, String fileName, String contentType) {
        Path filePath = resolveStoragePath(storageKey);
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            return null;
        }
        try {
            return new StoredFileResource(filePath, sanitizeFileName(fileName), normalizeContentType(contentType), Files.size(filePath));
        } catch (IOException ex) {
            throw new IllegalStateException("load file failed: " + storageKey, ex);
        }
    }

    public static String sanitizeFileName(String originalFileName) {
        String candidate = originalFileName == null ? "file" : Paths.get(originalFileName).getFileName().toString();
        candidate = candidate.replace("\\", "_").replace("/", "_");
        candidate = candidate.replaceAll("\\p{Cntrl}", "");
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

    private Path resolveStoragePath(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("storageKey must not be blank");
        }
        String normalized = storageKey.replace(WINDOWS_PATH_SEPARATOR, UNIX_PATH_SEPARATOR);
        while (normalized.startsWith(UNIX_PATH_SEPARATOR)) {
            normalized = normalized.substring(1);
        }
        Path base = Path.of(properties.getLocalBaseDir()).toAbsolutePath().normalize();
        Path resolved = base.resolve(normalized).normalize();
        if (!resolved.startsWith(base)) {
            throw new IllegalArgumentException("invalid storageKey");
        }
        return resolved;
    }
}
