package com.ming.imchatserver.remote.social;

import com.ming.im.apicontract.common.ApiResponse;
import com.ming.imapicontract.social.CheckContactActiveRequest;
import com.ming.imapicontract.social.CheckContactActiveResponse;
import com.ming.imapicontract.social.CheckGroupRecallPermissionRequest;
import com.ming.imapicontract.social.CheckGroupRecallPermissionResponse;
import com.ming.imapicontract.social.ContactListRequest;
import com.ming.imapicontract.social.ContactListResponse;
import com.ming.imapicontract.social.ContactOperateRequest;
import com.ming.imapicontract.social.ContactOperateResponse;
import com.ming.imapicontract.social.GetGroupMemberIdsRequest;
import com.ming.imapicontract.social.GetGroupMemberIdsResponse;
import com.ming.imapicontract.social.GroupJoinRequest;
import com.ming.imapicontract.social.GroupJoinResponse;
import com.ming.imapicontract.social.GroupMemberListRequest;
import com.ming.imapicontract.social.GroupMemberListResponse;
import com.ming.imapicontract.social.GroupQuitRequest;
import com.ming.imapicontract.social.GroupQuitResponse;
import com.ming.imapicontract.social.SocialApiPaths;
import com.ming.imapicontract.social.ValidateSingleChatPermissionRequest;
import com.ming.imapicontract.social.ValidateSingleChatPermissionResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * social 服务远程客户端。
 */
@FeignClient(
        name = "im-social-service",
        contextId = "socialServiceClient",
        path = SocialApiPaths.BASE,
        fallbackFactory = SocialServiceClientFallbackFactory.class
)
public interface SocialServiceClient {

    @PostMapping(SocialApiPaths.CONTACT_ADD)
    ApiResponse<ContactOperateResponse> addContact(@RequestBody ContactOperateRequest request);

    @PostMapping(SocialApiPaths.CONTACT_REMOVE)
    ApiResponse<ContactOperateResponse> removeContact(@RequestBody ContactOperateRequest request);

    @PostMapping(SocialApiPaths.CONTACT_LIST)
    ApiResponse<ContactListResponse> listContacts(@RequestBody ContactListRequest request);

    @PostMapping(SocialApiPaths.CONTACT_ACTIVE_CHECK)
    ApiResponse<CheckContactActiveResponse> checkContactActive(@RequestBody CheckContactActiveRequest request);

    @PostMapping(SocialApiPaths.GROUP_JOIN)
    ApiResponse<GroupJoinResponse> joinGroup(@RequestBody GroupJoinRequest request);

    @PostMapping(SocialApiPaths.GROUP_QUIT)
    ApiResponse<GroupQuitResponse> quitGroup(@RequestBody GroupQuitRequest request);

    @PostMapping(SocialApiPaths.GROUP_MEMBER_LIST)
    ApiResponse<GroupMemberListResponse> listGroupMembers(@RequestBody GroupMemberListRequest request);

    @PostMapping(SocialApiPaths.VALIDATE_SINGLE_CHAT)
    ApiResponse<ValidateSingleChatPermissionResponse> validateSingleChatPermission(
            @RequestBody ValidateSingleChatPermissionRequest request);

    @PostMapping(SocialApiPaths.GET_GROUP_MEMBER_IDS)
    ApiResponse<GetGroupMemberIdsResponse> getGroupMemberIds(@RequestBody GetGroupMemberIdsRequest request);

    @PostMapping(SocialApiPaths.CHECK_GROUP_RECALL)
    ApiResponse<CheckGroupRecallPermissionResponse> checkGroupRecallPermission(
            @RequestBody CheckGroupRecallPermissionRequest request);
}
