package com.ming.imsocialservice.service.impl;

import com.ming.imapicontract.social.CheckContactActiveRequest;
import com.ming.imapicontract.social.CheckContactActiveResponse;
import com.ming.imapicontract.social.CheckGroupRecallPermissionRequest;
import com.ming.imapicontract.social.CheckGroupRecallPermissionResponse;
import com.ming.imapicontract.social.ContactItemDTO;
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
import com.ming.imapicontract.social.GroupMemberDTO;
import com.ming.imapicontract.social.GroupMemberListRequest;
import com.ming.imapicontract.social.GroupMemberListResponse;
import com.ming.imapicontract.social.GroupQuitRequest;
import com.ming.imapicontract.social.GroupQuitResponse;
import com.ming.imapicontract.social.ValidateSingleChatPermissionRequest;
import com.ming.imapicontract.social.ValidateSingleChatPermissionResponse;
import com.ming.imsocialservice.dao.ContactRelationDO;
import com.ming.imsocialservice.dao.SocialGroupMemberDO;
import com.ming.imsocialservice.service.ContactService;
import com.ming.imsocialservice.service.GroupService;
import com.ming.imsocialservice.service.SocialApplicationService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * social 应用服务默认实现。
 */
@Service
public class SocialApplicationServiceImpl implements SocialApplicationService {

    private final ContactService contactService;
    private final GroupService groupService;

    public SocialApplicationServiceImpl(ContactService contactService, GroupService groupService) {
        this.contactService = contactService;
        this.groupService = groupService;
    }

    @Override
    public ContactOperateResponse addContact(ContactOperateRequest request) {
        ContactService.Result result = contactService.addOrActivateContact(request.ownerUserId(), request.peerUserId());
        return new ContactOperateResponse(result.isSuccess(), result.isIdempotent());
    }

    @Override
    public ContactOperateResponse removeContact(ContactOperateRequest request) {
        ContactService.Result result = contactService.removeOrDeactivateContact(request.ownerUserId(), request.peerUserId());
        return new ContactOperateResponse(result.isSuccess(), result.isIdempotent());
    }

    @Override
    public ContactListResponse listContacts(ContactListRequest request) {
        ContactService.ContactPageResult pageResult = contactService.listActiveContacts(
                request.ownerUserId(), request.cursorPeerUserId(), request.limit());
        List<ContactItemDTO> items = new ArrayList<>();
        for (ContactRelationDO item : pageResult.getItems()) {
            items.add(new ContactItemDTO(
                    item.getOwnerUserId(),
                    item.getPeerUserId(),
                    item.getRelationStatus(),
                    item.getSource(),
                    item.getAlias(),
                    item.getCreatedAt(),
                    item.getUpdatedAt()
            ));
        }
        return new ContactListResponse(items, pageResult.getNextCursor(), pageResult.isHasMore());
    }

    @Override
    public CheckContactActiveResponse checkContactActive(CheckContactActiveRequest request) {
        return new CheckContactActiveResponse(contactService.isActiveContact(request.ownerUserId(), request.peerUserId()));
    }

    @Override
    public GroupCreateResponse createGroup(GroupCreateRequest request) {
        GroupService.CreateGroupResult result = groupService.createGroup(
                request.ownerUserId(), request.name(), request.memberLimit());
        return new GroupCreateResponse(result.getGroupId(), result.getGroupNo());
    }

    @Override
    public GroupJoinResponse joinGroup(GroupJoinRequest request) {
        GroupService.JoinGroupResult result = groupService.joinGroup(request.groupId(), request.userId());
        return new GroupJoinResponse(result.isJoined(), result.isIdempotent());
    }

    @Override
    public GroupQuitResponse quitGroup(GroupQuitRequest request) {
        GroupService.QuitGroupResult result = groupService.quitGroup(request.groupId(), request.userId());
        return new GroupQuitResponse(result.isQuit(), result.isIdempotent());
    }

    @Override
    public GroupMemberListResponse listGroupMembers(GroupMemberListRequest request) {
        GroupService.MemberPageResult pageResult = groupService.listMembers(request.groupId(), request.cursorUserId(), request.limit());
        List<GroupMemberDTO> items = new ArrayList<>();
        for (SocialGroupMemberDO item : pageResult.getItems()) {
            items.add(new GroupMemberDTO(
                    item.getGroupId(),
                    item.getUserId(),
                    item.getRole(),
                    item.getMemberStatus(),
                    item.getJoinedAt(),
                    item.getMutedUntil(),
                    item.getCreatedAt(),
                    item.getUpdatedAt()
            ));
        }
        return new GroupMemberListResponse(items, pageResult.getNextCursor(), pageResult.isHasMore());
    }

    @Override
    public ValidateSingleChatPermissionResponse validateSingleChatPermission(ValidateSingleChatPermissionRequest request) {
        boolean ownerToPeer = contactService.isActiveContact(request.fromUserId(), request.toUserId());
        boolean peerToOwner = contactService.isActiveContact(request.toUserId(), request.fromUserId());
        boolean allowed = ownerToPeer && peerToOwner;
        return new ValidateSingleChatPermissionResponse(allowed, allowed ? "OK" : "FORBIDDEN");
    }

    @Override
    public GetGroupMemberIdsResponse getGroupMemberIds(GetGroupMemberIdsRequest request) {
        return new GetGroupMemberIdsResponse(groupService.listActiveMemberUserIds(request.groupId()));
    }

    @Override
    public CheckGroupRecallPermissionResponse checkGroupRecallPermission(CheckGroupRecallPermissionRequest request) {
        boolean allowed = groupService.canRecallMessage(request.groupId(), request.operatorUserId(), request.targetUserId());
        return new CheckGroupRecallPermissionResponse(allowed, allowed ? "OK" : "FORBIDDEN");
    }
}
