package com.ming.imfileservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 文件存储配置。
 */
@Component
@ConfigurationProperties(prefix = "im.file")
@Getter
@Setter
public class FileStorageProperties {
    private String localBaseDir = "data/uploads";
    private String publicUrlPrefix = "/files";
    private long maxFileSizeBytes = 10 * 1024 * 1024L;
    private long uploadTokenExpireSeconds = 900L;
    private String downloadSignSecret = "";
    private long downloadSignExpireSeconds = 300L;
    private boolean downloadSignOneTime = false;
    private boolean allowOwnerDownloadWithoutMessage = true;
    private List<String> allowedContentTypes = new ArrayList<>();
    private List<String> allowedExtensions = new ArrayList<>();
}
