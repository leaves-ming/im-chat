package com.ming.imchatserver.service;

import java.util.List;

/**
 * 跨实例在线状态视图。
 */
public interface PresenceService {

    record OnlineEndpoint(String serverId, String deviceId, String sessionId, long lastHeartbeatAt) {
    }

    void register(Long userId, String serverId, String deviceId, String sessionId, long lastHeartbeatAt);

    void refreshHeartbeat(Long userId, String sessionId, long lastHeartbeatAt);

    void unregister(Long userId, String sessionId);

    List<OnlineEndpoint> listOnlineEndpoints(Long userId);

    boolean isOnline(Long userId);
}

