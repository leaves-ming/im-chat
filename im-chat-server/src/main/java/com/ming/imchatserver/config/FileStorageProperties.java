package com.ming.imchatserver.config;

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
    /** 本地存储根目录。 */
    private String localBaseDir = "data/uploads";
    /** 对外访问 URL 前缀。 */
    private String publicUrlPrefix = "/files";
    /** 最大文件大小，单位字节。 */
    private long maxFileSizeBytes = 10 * 1024 * 1024L;
    /** 上传凭证有效期（秒）。 */
    private long uploadTokenExpireSeconds = 900L;
    /** 下载签名密钥。 */
    private String downloadSignSecret = "";
    /** 下载签名有效期（秒）。 */
    private long downloadSignExpireSeconds = 300L;
    /** 下载签名是否仅允许成功消费一次。 */
    private boolean downloadSignOneTime = false;
    /** 是否允许 owner 不依赖消息关系直接下载。 */
    private boolean allowOwnerDownloadWithoutMessage = true;
    /** 允许的 content type 列表，空表示不限制。 */
    private List<String> allowedContentTypes = new ArrayList<>();
    /** 允许的扩展名列表，空表示不限制。 */
    private List<String> allowedExtensions = new ArrayList<>();
}
