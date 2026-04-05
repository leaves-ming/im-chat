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
import com.ming.imapicontract.social.ValidateSingleChatPermissionRequest;
import com.ming.imapicontract.social.ValidateSingleChatPermissionResponse;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * social 服务远程调用兜底。
 */
@Component
public class SocialServiceClientFallbackFactory implements FallbackFactory<SocialServiceClient> {

    @Override
    public SocialServiceClient create(Throwable cause) {
        String message = cause == null ? "im-social-service unavailable" : cause.getMessage();
        return new SocialServiceClient() {
            @Override
            public ApiResponse<ContactOperateResponse> addContact(ContactOperateRequest request) {
                return ApiResponse.failure("REMOTE_UNAVAILABLE", message);
            }

            @Override
            public ApiResponse<ContactOperateResponse> removeContact(ContactOperateRequest request) {
                return ApiResponse.failure("REMOTE_UNAVAILABLE", message);
            }

            @Override
            public ApiResponse<ContactListResponse> listContacts(ContactListRequest request) {
                return ApiResponse.failure("REMOTE_UNAVAILABLE", message);
            }

            @Override
            public ApiResponse<CheckContactActiveResponse> checkContactActive(CheckContactActiveRequest request) {
                return ApiResponse.failure("REMOTE_UNAVAILABLE", message);
            }

            @Override
            public ApiResponse<GroupJoinResponse> joinGroup(GroupJoinRequest request) {
                return ApiResponse.failure("REMOTE_UNAVAILABLE", message);
            }

            @Override
            public ApiResponse<GroupQuitResponse> quitGroup(GroupQuitRequest request) {
                return ApiResponse.failure("REMOTE_UNAVAILABLE", message);
            }

            @Override
            public ApiResponse<GroupMemberListResponse> listGroupMembers(GroupMemberListRequest request) {
                return ApiResponse.failure("REMOTE_UNAVAILABLE", message);
            }

            @Override
            public ApiResponse<ValidateSingleChatPermissionResponse> validateSingleChatPermission(
                    ValidateSingleChatPermissionRequest request) {
                return ApiResponse.failure("REMOTE_UNAVAILABLE", message);
            }

            @Override
            public ApiResponse<GetGroupMemberIdsResponse> getGroupMemberIds(GetGroupMemberIdsRequest request) {
                return ApiResponse.failure("REMOTE_UNAVAILABLE", message);
            }

            @Override
            public ApiResponse<CheckGroupRecallPermissionResponse> checkGroupRecallPermission(
                    CheckGroupRecallPermissionRequest request) {
                return ApiResponse.failure("REMOTE_UNAVAILABLE", message);
            }
        };
    }
}
