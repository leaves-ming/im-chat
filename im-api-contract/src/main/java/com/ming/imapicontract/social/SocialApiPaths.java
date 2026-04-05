package com.ming.imapicontract.social;

/**
 * social 服务 HTTP 契约路径常量。
 */
public final class SocialApiPaths {

    public static final String BASE = "/api/social";
    public static final String CONTACT_ADD = "/contact/add";
    public static final String CONTACT_REMOVE = "/contact/remove";
    public static final String CONTACT_LIST = "/contact/list";
    public static final String CONTACT_ACTIVE_CHECK = "/contact/active/check";
    public static final String GROUP_JOIN = "/group/join";
    public static final String GROUP_QUIT = "/group/quit";
    public static final String GROUP_MEMBER_LIST = "/group/member/list";
    public static final String VALIDATE_SINGLE_CHAT = "/permission/single-chat/validate";
    public static final String GET_GROUP_MEMBER_IDS = "/group/member/ids";
    public static final String CHECK_GROUP_RECALL = "/group/recall/check";

    private SocialApiPaths() {
    }
}
