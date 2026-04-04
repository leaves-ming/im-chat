package com.ming.imsocialservice.controller;

import com.ming.imsocialservice.service.SocialMetaService;
import com.ming.imsocialservice.vo.SocialMetaVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * social 服务基础元信息接口。
 */
@RestController
@RequestMapping("/api/social")
public class SocialMetaController {

    private final SocialMetaService socialMetaService;

    public SocialMetaController(SocialMetaService socialMetaService) {
        this.socialMetaService = socialMetaService;
    }

    @GetMapping("/meta")
    public SocialMetaVO meta() {
        return socialMetaService.buildMeta();
    }
}
