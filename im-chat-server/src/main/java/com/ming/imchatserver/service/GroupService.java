package com.ming.imchatserver.service;

import com.ming.imchatserver.dao.GroupMemberDO;

import java.util.List;

/**
 * 群成员管理服务（MVP）。
 */
public interface GroupService {

    CreateGroupResult createGroup(Long ownerUserId, String name, Integer memberLimit);

    void joinGroup(Long groupId, Long userId);

    void quitGroup(Long groupId, Long userId);

    MemberPageResult listMembers(Long groupId, Long cursorUserId, Integer limit);

    class CreateGroupResult {
        private final Long groupId;
        private final String groupNo;

        public CreateGroupResult(Long groupId, String groupNo) {
            this.groupId = groupId;
            this.groupNo = groupNo;
        }

        public Long getGroupId() {
            return groupId;
        }

        public String getGroupNo() {
            return groupNo;
        }
    }

    class MemberPageResult {
        private final List<GroupMemberDO> items;
        private final Long nextCursor;
        private final boolean hasMore;

        public MemberPageResult(List<GroupMemberDO> items, Long nextCursor, boolean hasMore) {
            this.items = items;
            this.nextCursor = nextCursor;
            this.hasMore = hasMore;
        }

        public List<GroupMemberDO> getItems() {
            return items;
        }

        public Long getNextCursor() {
            return nextCursor;
        }

        public boolean isHasMore() {
            return hasMore;
        }
    }
}
