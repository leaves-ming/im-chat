package com.ming.imsocialservice.vo;

import java.util.List;

/**
 * social 服务元信息返回对象。
 */
public record SocialMetaVO(String serviceName,
                           String domain,
                           List<String> capabilities,
                           boolean redisEnabled) {
}
