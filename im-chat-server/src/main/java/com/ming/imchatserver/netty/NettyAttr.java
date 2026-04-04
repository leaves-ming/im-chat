package com.ming.imchatserver.netty;

import io.netty.util.AttributeKey;
/**
 * Netty Channel 属性键定义。
 * <p>
 * 用于在不同 Handler 之间共享同一个连接上的认证与绑定状态。
 */

    public class NettyAttr {    /** 当前连接对应的业务用户 ID。 */
    
    public static final AttributeKey<Long> USER_ID = AttributeKey.valueOf("USER_ID");    /** 握手鉴权是否通过。 */
    
    public static final AttributeKey<Boolean> AUTH_OK = AttributeKey.valueOf("AUTH_OK");    /** 是否已完成“用户-连接”绑定。 */
    
    public static final AttributeKey<Boolean> BOUND = AttributeKey.valueOf("BOUND");
    /** 当前连接所属设备 ID。 */
    public static final AttributeKey<String> DEVICE_ID = AttributeKey.valueOf("DEVICE_ID");
    /** 当前连接的 traceId。 */
    public static final AttributeKey<String> TRACE_ID = AttributeKey.valueOf("TRACE_ID");
}
