package com.ming.imchatserver.service.remote;

import com.ming.common.remote.RemoteCallTemplate;
import com.ming.imapicontract.common.ApiResponse;
import com.ming.imapicontract.social.CheckContactActiveRequest;
import com.ming.imapicontract.social.ContactItemDTO;
import com.ming.imapicontract.social.ContactListRequest;
import com.ming.imapicontract.social.ContactListResponse;
import com.ming.imapicontract.social.ContactOperateRequest;
import com.ming.imapicontract.social.ContactOperateResponse;
import com.ming.imapicontract.social.ValidateSingleChatPermissionRequest;
import com.ming.imapicontract.social.ValidateSingleChatPermissionResponse;
import com.ming.imchatserver.application.model.ContactOperationResult;
import com.ming.imchatserver.application.model.ContactPage;
import com.ming.imchatserver.application.model.ContactView;
import com.ming.imchatserver.config.SocialRouteProperties;
import com.ming.imchatserver.remote.social.SocialServiceClient;
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
public class RemoteContactService {

    private static final String SERVICE_NAME = "social-service";

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

    public ContactOperationResult addOrActivateContact(Long ownerUserId, Long peerUserId) {
        ContactOperateResponse response = RemoteCallTemplate.execute(() ->
                socialServiceClient.addContact(new ContactOperateRequest(ownerUserId, peerUserId)), SERVICE_NAME);
        socialCacheSupport.invalidateContactPair(ownerUserId, peerUserId);
        return new ContactOperationResult(response.success(), response.idempotent());
    }

    public ContactOperationResult removeOrDeactivateContact(Long ownerUserId, Long peerUserId) {
        ContactOperateResponse response = RemoteCallTemplate.execute(() ->
                socialServiceClient.removeContact(new ContactOperateRequest(ownerUserId, peerUserId)), SERVICE_NAME);
        socialCacheSupport.invalidateContactPair(ownerUserId, peerUserId);
        return new ContactOperationResult(response.success(), response.idempotent());
    }

    public ContactPage listActiveContacts(Long ownerUserId, Long cursorPeerUserId, Integer limit) {
        ContactListResponse response = RemoteCallTemplate.execute(() ->
                socialServiceClient.listContacts(new ContactListRequest(ownerUserId, cursorPeerUserId, limit)), SERVICE_NAME);
        List<ContactView> items = new ArrayList<>();
        for (ContactItemDTO item : response.items()) {
            items.add(new ContactView(
                    item.ownerUserId(),
                    item.peerUserId(),
                    item.relationStatus(),
                    item.source(),
                    item.alias(),
                    item.createdAt(),
                    item.updatedAt()
            ));
        }
        return new ContactPage(items, response.nextCursor(), response.hasMore());
    }

    public boolean isActiveContact(Long ownerUserId, Long peerUserId) {
        Boolean cached = socialCacheSupport.getContactActive(ownerUserId, peerUserId);
        if (cached != null) {
            return cached;
        }
        try {
            com.ming.imapicontract.social.CheckContactActiveResponse response = RemoteCallTemplate.execute(() ->
                    socialServiceClient.checkContactActive(new CheckContactActiveRequest(ownerUserId, peerUserId)), SERVICE_NAME);
            boolean active = response != null && response.active();
            socialCacheSupport.putContactActive(ownerUserId, peerUserId, active, contactActiveCacheTtlMillis());
            return active;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isSingleChatAllowed(Long fromUserId, Long toUserId) {
        Boolean cached = socialCacheSupport.getSingleChatPermission(fromUserId, toUserId);
        if (cached != null) {
            return cached;
        }
        try {
            ValidateSingleChatPermissionResponse response = RemoteCallTemplate.execute(() ->
                    socialServiceClient.validateSingleChatPermission(new ValidateSingleChatPermissionRequest(fromUserId, toUserId)), SERVICE_NAME);
            boolean allowed = response != null && response.allowed();
            socialCacheSupport.putSingleChatPermission(fromUserId, toUserId, allowed, singleChatPermissionCacheTtlMillis());
            return allowed;
        } catch (Exception e) {
            return false;
        }
    }

    private long singleChatPermissionCacheTtlMillis() {
        return Duration.ofSeconds(Math.max(1, socialRouteProperties.getSingleChatPermissionCacheTtlSeconds())).toMillis();
    }

    private long contactActiveCacheTtlMillis() {
        return Duration.ofSeconds(Math.max(1, socialRouteProperties.getContactActiveCacheTtlSeconds())).toMillis();
    }
}

