package com.ming.imsocialservice.controller;

import com.ming.imapicontract.common.ApiResponse;
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
import com.ming.imapicontract.social.GroupCreateRequest;
import com.ming.imapicontract.social.GroupCreateResponse;
import com.ming.imapicontract.social.GroupJoinRequest;
import com.ming.imapicontract.social.GroupJoinResponse;
import com.ming.imapicontract.social.GroupMemberListRequest;
import com.ming.imapicontract.social.GroupMemberListResponse;
import com.ming.imapicontract.social.GroupQuitRequest;
import com.ming.imapicontract.social.GroupQuitResponse;
import com.ming.imapicontract.social.SocialApiPaths;
import com.ming.imapicontract.social.ValidateSingleChatPermissionRequest;
import com.ming.imapicontract.social.ValidateSingleChatPermissionResponse;
import com.ming.imsocialservice.service.SocialApplicationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * social 命令控制器。
 */
@RestController
@RequestMapping(SocialApiPaths.BASE)
public class SocialCommandController {

    private final SocialApplicationService socialApplicationService;

    public SocialCommandController(SocialApplicationService socialApplicationService) {
        this.socialApplicationService = socialApplicationService;
    }

    @PostMapping(SocialApiPaths.CONTACT_ADD)
    public ApiResponse<ContactOperateResponse> addContact(@RequestBody ContactOperateRequest request) {
        return ApiResponse.success(socialApplicationService.addContact(request));
    }

    @PostMapping(SocialApiPaths.CONTACT_REMOVE)
    public ApiResponse<ContactOperateResponse> removeContact(@RequestBody ContactOperateRequest request) {
        return ApiResponse.success(socialApplicationService.removeContact(request));
    }

    @PostMapping(SocialApiPaths.CONTACT_LIST)
    public ApiResponse<ContactListResponse> listContacts(@RequestBody ContactListRequest request) {
        return ApiResponse.success(socialApplicationService.listContacts(request));
    }

    @PostMapping(SocialApiPaths.CONTACT_ACTIVE_CHECK)
    public ApiResponse<CheckContactActiveResponse> checkContactActive(@RequestBody CheckContactActiveRequest request) {
        return ApiResponse.success(socialApplicationService.checkContactActive(request));
    }

    @PostMapping(SocialApiPaths.GROUP_CREATE)
    public ApiResponse<GroupCreateResponse> createGroup(@RequestBody GroupCreateRequest request) {
        return ApiResponse.success(socialApplicationService.createGroup(request));
    }

    @PostMapping(SocialApiPaths.GROUP_JOIN)
    public ApiResponse<GroupJoinResponse> joinGroup(@RequestBody GroupJoinRequest request) {
        return ApiResponse.success(socialApplicationService.joinGroup(request));
    }

    @PostMapping(SocialApiPaths.GROUP_QUIT)
    public ApiResponse<GroupQuitResponse> quitGroup(@RequestBody GroupQuitRequest request) {
        return ApiResponse.success(socialApplicationService.quitGroup(request));
    }

    @PostMapping(SocialApiPaths.GROUP_MEMBER_LIST)
    public ApiResponse<GroupMemberListResponse> listGroupMembers(@RequestBody GroupMemberListRequest request) {
        return ApiResponse.success(socialApplicationService.listGroupMembers(request));
    }

    @PostMapping(SocialApiPaths.VALIDATE_SINGLE_CHAT)
    public ApiResponse<ValidateSingleChatPermissionResponse> validateSingleChatPermission(
            @RequestBody ValidateSingleChatPermissionRequest request) {
        return ApiResponse.success(socialApplicationService.validateSingleChatPermission(request));
    }

    @PostMapping(SocialApiPaths.GET_GROUP_MEMBER_IDS)
    public ApiResponse<GetGroupMemberIdsResponse> getGroupMemberIds(@RequestBody GetGroupMemberIdsRequest request) {
        return ApiResponse.success(socialApplicationService.getGroupMemberIds(request));
    }

    @PostMapping(SocialApiPaths.CHECK_GROUP_RECALL)
    public ApiResponse<CheckGroupRecallPermissionResponse> checkGroupRecallPermission(
            @RequestBody CheckGroupRecallPermissionRequest request) {
        return ApiResponse.success(socialApplicationService.checkGroupRecallPermission(request));
    }
}
