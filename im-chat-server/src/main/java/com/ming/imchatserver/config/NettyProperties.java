package com.ming.imchatserver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "im.netty")
public class NettyProperties {
    private int port = 8080;
    private String websocketPath = "/ws";
    private int readerIdleSeconds = 60;
    private int writerIdleSeconds = 0;
    private int allIdleSeconds = 120;
    private int maxContentLength = 65536;

    // JWT token ttl (seconds)
    private long tokenExpireSeconds = 3600;

    // Origin whitelist
    private boolean originCheckEnabled = false;
    private List<String> originWhitelist = new ArrayList<>();

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getWebsocketPath() {
        return websocketPath;
    }

    public void setWebsocketPath(String websocketPath) {
        this.websocketPath = websocketPath;
    }

    public int getReaderIdleSeconds() {
        return readerIdleSeconds;
    }

    public void setReaderIdleSeconds(int readerIdleSeconds) {
        this.readerIdleSeconds = readerIdleSeconds;
    }

    public int getWriterIdleSeconds() {
        return writerIdleSeconds;
    }

    public void setWriterIdleSeconds(int writerIdleSeconds) {
        this.writerIdleSeconds = writerIdleSeconds;
    }

    public int getAllIdleSeconds() {
        return allIdleSeconds;
    }

    public void setAllIdleSeconds(int allIdleSeconds) {
        this.allIdleSeconds = allIdleSeconds;
    }

    public int getMaxContentLength() {
        return maxContentLength;
    }

    public void setMaxContentLength(int maxContentLength) {
        this.maxContentLength = maxContentLength;
    }

    public long getTokenExpireSeconds() {
        return tokenExpireSeconds;
    }

    public void setTokenExpireSeconds(long tokenExpireSeconds) {
        this.tokenExpireSeconds = tokenExpireSeconds;
    }

    public boolean isOriginCheckEnabled() {
        return originCheckEnabled;
    }

    public void setOriginCheckEnabled(boolean originCheckEnabled) {
        this.originCheckEnabled = originCheckEnabled;
    }

    public List<String> getOriginWhitelist() {
        return originWhitelist;
    }

    public void setOriginWhitelist(List<String> originWhitelist) {
        this.originWhitelist = originWhitelist;
    }
}
