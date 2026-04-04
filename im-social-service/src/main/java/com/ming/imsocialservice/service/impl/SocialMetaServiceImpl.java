package com.ming.imsocialservice.service.impl;

import com.ming.imsocialservice.config.SocialServiceProperties;
import com.ming.imsocialservice.service.SocialMetaService;
import com.ming.imsocialservice.vo.SocialMetaVO;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * social 服务元信息默认实现。
 */
@Service
public class SocialMetaServiceImpl implements SocialMetaService {

    private final SocialServiceProperties socialServiceProperties;

    public SocialMetaServiceImpl(SocialServiceProperties socialServiceProperties) {
        this.socialServiceProperties = socialServiceProperties;
    }

    @Override
    public SocialMetaVO buildMeta() {
        return new SocialMetaVO(
                "im-social-service",
                "social",
                List.of(
                        "CONTACT_ADD",
                        "CONTACT_REMOVE",
                        "CONTACT_LIST",
                        "GROUP_CREATE",
                        "GROUP_JOIN",
                        "GROUP_QUIT",
                        "GROUP_MEMBER_LIST",
                        "GROUP_PERMISSION_CHECK"
                ),
                socialServiceProperties.isRedisEnabled()
        );
    }
}
