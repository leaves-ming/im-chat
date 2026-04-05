package com.ming.imsocialservice.service;

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
import com.ming.imapicontract.social.ValidateSingleChatPermissionRequest;
import com.ming.imapicontract.social.ValidateSingleChatPermissionResponse;

/**
 * social 应用服务。
 */
public interface SocialApplicationService {

    ContactOperateResponse addContact(ContactOperateRequest request);

    ContactOperateResponse removeContact(ContactOperateRequest request);

    ContactListResponse listContacts(ContactListRequest request);

    CheckContactActiveResponse checkContactActive(CheckContactActiveRequest request);

    GroupJoinResponse joinGroup(GroupJoinRequest request);

    GroupQuitResponse quitGroup(GroupQuitRequest request);

    GroupMemberListResponse listGroupMembers(GroupMemberListRequest request);

    ValidateSingleChatPermissionResponse validateSingleChatPermission(ValidateSingleChatPermissionRequest request);

    GetGroupMemberIdsResponse getGroupMemberIds(GetGroupMemberIdsRequest request);

    CheckGroupRecallPermissionResponse checkGroupRecallPermission(CheckGroupRecallPermissionRequest request);
}
