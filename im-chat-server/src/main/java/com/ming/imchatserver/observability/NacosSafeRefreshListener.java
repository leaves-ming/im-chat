package com.ming.imchatserver.observability;

import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.ming.imchatserver.config.PlatformConfigProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.Executor;

/**
 * 仅对安全观测项执行最小范围热更新。
 */
@Component
public class NacosSafeRefreshListener {

    private static final Logger logger = LoggerFactory.getLogger(NacosSafeRefreshListener.class);
    private static final long GET_CONFIG_TIMEOUT_MS = 3000L;

    private final ObjectProvider<ConfigService> configServiceProvider;
    private final PlatformConfigProperties platformConfigProperties;
    private final RuntimeObservabilitySettings runtimeSettings;

    public NacosSafeRefreshListener(ObjectProvider<ConfigService> configServiceProvider,
                                    PlatformConfigProperties platformConfigProperties,
                                    RuntimeObservabilitySettings runtimeSettings) {
        this.configServiceProvider = configServiceProvider;
        this.platformConfigProperties = platformConfigProperties;
        this.runtimeSettings = runtimeSettings;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void registerListener() {
        ConfigService configService = configServiceProvider.getIfAvailable();
        if (configService == null) {
            return;
        }
        String dataId = platformConfigProperties.getConfig().getServiceDataId();
        String group = System.getenv().getOrDefault("IM_NACOS_SERVICE_GROUP", "IM_SERVICE");
        try {
            configService.addListener(dataId, group, new Listener() {
                @Override
                public Executor getExecutor() {
                    return null;
                }

                @Override
                public void receiveConfigInfo(String configInfo) {
                    applyRuntimeSettings(configInfo);
                }
            });
            String configInfo = configService.getConfig(dataId, group, GET_CONFIG_TIMEOUT_MS);
            if (configInfo != null && !configInfo.isBlank()) {
                applyRuntimeSettings(configInfo);
            }
            logger.info("registered nacos safe refresh listener dataId={} group={} prefixes={}",
                    dataId, group, platformConfigProperties.getConfig().getHotRefreshSafePrefixes());
        } catch (Exception ex) {
            logger.warn("register nacos safe refresh listener failed dataId={} group={}", dataId, group, ex);
        }
    }

    private void applyRuntimeSettings(String configInfo) {
        try {
            YamlPropertiesFactoryBean factoryBean = new YamlPropertiesFactoryBean();
            factoryBean.setResources(new ByteArrayResource(configInfo.getBytes(StandardCharsets.UTF_8)));
            Properties properties = factoryBean.getObject();
            if (properties == null) {
                return;
            }
            runtimeSettings.setAccessLogEnabled(Boolean.parseBoolean(
                    properties.getProperty("im.observability.access-log-enabled",
                            Boolean.toString(runtimeSettings.isAccessLogEnabled()))));
            runtimeSettings.setTraceHeaderName(properties.getProperty(
                    "im.observability.trace.header-name",
                    runtimeSettings.getTraceHeaderName()));
            runtimeSettings.setTraceGenerateIfMissing(Boolean.parseBoolean(
                    properties.getProperty("im.observability.trace.generate-if-missing",
                            Boolean.toString(runtimeSettings.isTraceGenerateIfMissing()))));
            logger.info("applied nacos safe refresh accessLogEnabled={} traceHeaderName={} generateIfMissing={}",
                    runtimeSettings.isAccessLogEnabled(),
                    runtimeSettings.getTraceHeaderName(),
                    runtimeSettings.isTraceGenerateIfMissing());
        } catch (Exception ex) {
            logger.warn("apply nacos safe refresh failed", ex);
        }
    }
}
