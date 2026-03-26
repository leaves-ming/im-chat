package com.ming.imchatserver.group;

/**
 * 群聊领域常量定义。
 */
public final class GroupDomainConstants {

    private GroupDomainConstants() {
    }

    public static final int GROUP_STATUS_ACTIVE = 1;
    public static final int GROUP_STATUS_DISMISSED = 2;

    public static final int MEMBER_ROLE_MEMBER = 1;
    public static final int MEMBER_ROLE_OWNER = 3;

    public static final int MEMBER_STATUS_ACTIVE = 1;
    public static final int MEMBER_STATUS_QUIT = 2;
    public static final int MEMBER_STATUS_KICKED = 3;
}
