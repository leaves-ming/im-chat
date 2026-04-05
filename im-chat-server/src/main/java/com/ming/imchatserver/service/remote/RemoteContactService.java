package com.ming.imchatserver.service.remote;

import com.ming.im.apicontract.common.ApiResponse;
import com.ming.imapicontract.social.CheckContactActiveRequest;
import com.ming.imapicontract.social.ContactItemDTO;
import com.ming.imapicontract.social.ContactListRequest;
import com.ming.imapicontract.social.ContactListResponse;
import com.ming.imapicontract.social.ContactOperateRequest;
import com.ming.imapicontract.social.ContactOperateResponse;
import com.ming.imapicontract.social.ValidateSingleChatPermissionRequest;
import com.ming.imapicontract.social.ValidateSingleChatPermissionResponse;
import com.ming.imchatserver.config.SocialRouteProperties;
import com.ming.imchatserver.dao.ContactDO;
import com.ming.imchatserver.remote.social.SocialServiceClient;
import com.ming.imchatserver.service.ContactService;
import com.ming.imchatserver.service.SingleChatPermissionCapable;
import com.ming.imchatserver.service.SocialRpcException;
import com.ming.imchatserver.service.support.SocialCacheSupport;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * social 联系人远程服务包装。
 */
@Component
public class RemoteContactService implements SingleChatPermissionCapable {

    private final SocialServiceClient socialServiceClient;
    private final SocialCacheSupport socialCacheSupport;
    private final SocialRouteProperties socialRouteProperties;

    public RemoteContactService(SocialServiceClient socialServiceClient,
                                SocialCacheSupport socialCacheSupport,
                                SocialRouteProperties socialRouteProperties) {
        this.socialServiceClient = socialServiceClient;
        this.socialCacheSupport = socialCacheSupport;
        this.socialRouteProperties = socialRouteProperties;
    }

    public ContactService.Result addOrActivateContact(Long ownerUserId, Long peerUserId) {
        ContactOperateResponse response = unwrap(call(() ->
                socialServiceClient.addContact(new ContactOperateRequest(ownerUserId, peerUserId))));
        socialCacheSupport.invalidateContactPair(ownerUserId, peerUserId);
        return new ContactService.Result(response.success(), response.idempotent());
    }

    public ContactService.Result removeOrDeactivateContact(Long ownerUserId, Long peerUserId) {
        ContactOperateResponse response = unwrap(call(() ->
                socialServiceClient.removeContact(new ContactOperateRequest(ownerUserId, peerUserId))));
        socialCacheSupport.invalidateContactPair(ownerUserId, peerUserId);
        return new ContactService.Result(response.success(), response.idempotent());
    }

    public ContactService.ContactPageResult listActiveContacts(Long ownerUserId, Long cursorPeerUserId, Integer limit) {
        ContactListResponse response = unwrap(call(() ->
                socialServiceClient.listContacts(new ContactListRequest(ownerUserId, cursorPeerUserId, limit))));
        List<ContactDO> items = new ArrayList<>();
        for (ContactItemDTO item : response.items()) {
            ContactDO target = new ContactDO();
            target.setOwnerUserId(item.ownerUserId());
            target.setPeerUserId(item.peerUserId());
            target.setRelationStatus(item.relationStatus());
            target.setSource(item.source());
            target.setAlias(item.alias());
            target.setCreatedAt(item.createdAt());
            target.setUpdatedAt(item.updatedAt());
            items.add(target);
        }
        return new ContactService.ContactPageResult(items, response.nextCursor(), response.hasMore());
    }

    public boolean isActiveContact(Long ownerUserId, Long peerUserId) {
        Boolean cached = socialCacheSupport.getContactActive(ownerUserId, peerUserId);
        if (cached != null) {
            return cached;
        }
        ApiResponse<com.ming.imapicontract.social.CheckContactActiveResponse> response = call(() ->
                socialServiceClient.checkContactActive(new CheckContactActiveRequest(ownerUserId, peerUserId)));
        if (response != null && response.isSuccess()) {
            boolean active = response.getData() != null && response.getData().active();
            socialCacheSupport.putContactActive(ownerUserId, peerUserId, active, contactActiveCacheTtlMillis());
            return active;
        }
        if (isRemoteUnavailable(response)) {
            throw new SocialRpcException("REMOTE_UNAVAILABLE", messageOf(response, "contact active check unavailable"));
        }
        return false;
    }

    @Override
    public boolean isSingleChatAllowed(Long fromUserId, Long toUserId) {
        Boolean cached = socialCacheSupport.getSingleChatPermission(fromUserId, toUserId);
        if (cached != null) {
            return cached;
        }
        ApiResponse<ValidateSingleChatPermissionResponse> response = call(() ->
                socialServiceClient.validateSingleChatPermission(new ValidateSingleChatPermissionRequest(fromUserId, toUserId)));
        if (response != null && response.isSuccess()) {
            boolean allowed = response.getData() != null && response.getData().allowed();
            socialCacheSupport.putSingleChatPermission(fromUserId, toUserId, allowed, singleChatPermissionCacheTtlMillis());
            return allowed;
        }
        if (isRemoteUnavailable(response)) {
            throw new SocialRpcException("REMOTE_UNAVAILABLE", messageOf(response, "single chat permission unavailable"));
        }
        return false;
    }

    private <T> T unwrap(ApiResponse<T> response) {
        if (response == null) {
            throw new SocialRpcException("REMOTE_UNAVAILABLE", "social service response is null");
        }
        if (response.isSuccess()) {
            return response.getData();
        }
        throw mapToException(response);
    }

    private RuntimeException mapToException(ApiResponse<?> response) {
        String code = response == null ? "REMOTE_UNAVAILABLE" : response.getCode();
        String message = messageOf(response, "social service call failed");
        if ("FORBIDDEN".equals(code)) {
            return new SecurityException(message);
        }
        if ("INVALID_PARAM".equals(code)) {
            return new IllegalArgumentException(message);
        }
        if ("REMOTE_UNAVAILABLE".equals(code)) {
            return new SocialRpcException(code, message);
        }
        return new SocialRpcException(code == null ? "REMOTE_ERROR" : code, message);
    }

    private boolean isRemoteUnavailable(ApiResponse<?> response) {
        return response == null || "REMOTE_UNAVAILABLE".equals(response.getCode());
    }

    private <T> ApiResponse<T> call(SocialCall<T> socialCall) {
        try {
            return socialCall.execute();
        } catch (RuntimeException ex) {
            throw new SocialRpcException("REMOTE_UNAVAILABLE", ex.getMessage());
        }
    }

    private String messageOf(ApiResponse<?> response, String fallback) {
        return response == null || response.getMessage() == null || response.getMessage().isBlank()
                ? fallback
                : response.getMessage();
    }

    private long singleChatPermissionCacheTtlMillis() {
        return Duration.ofSeconds(Math.max(1, socialRouteProperties.getSingleChatPermissionCacheTtlSeconds())).toMillis();
    }

    private long contactActiveCacheTtlMillis() {
        return Duration.ofSeconds(Math.max(1, socialRouteProperties.getContactActiveCacheTtlSeconds())).toMillis();
    }

    @FunctionalInterface
    private interface SocialCall<T> {
        ApiResponse<T> execute();
    }
}
