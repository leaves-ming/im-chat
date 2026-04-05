package com.ming.imsocialservice.service;

import com.ming.imsocialservice.dao.SocialGroupMemberDO;

import java.util.List;

/**
 * 群关系服务。
 */
public interface GroupService {

    CreateGroupResult createGroup(Long ownerUserId, String name, Integer memberLimit);

    JoinGroupResult joinGroup(Long groupId, Long userId);

    QuitGroupResult quitGroup(Long groupId, Long userId);

    MemberPageResult listMembers(Long groupId, Long cursorUserId, Integer limit);

    boolean canRecallMessage(Long groupId, Long operatorUserId, Long targetUserId);

    List<Long> listActiveMemberUserIds(Long groupId);

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
        private final List<SocialGroupMemberDO> items;
        private final Long nextCursor;
        private final boolean hasMore;

        public MemberPageResult(List<SocialGroupMemberDO> items, Long nextCursor, boolean hasMore) {
            this.items = items;
            this.nextCursor = nextCursor;
            this.hasMore = hasMore;
        }

        public List<SocialGroupMemberDO> getItems() {
            return items;
        }

        public Long getNextCursor() {
            return nextCursor;
        }

        public boolean isHasMore() {
            return hasMore;
        }
    }

    class JoinGroupResult {
        private final boolean joined;
        private final boolean idempotent;

        public JoinGroupResult(boolean joined, boolean idempotent) {
            this.joined = joined;
            this.idempotent = idempotent;
        }

        public boolean isJoined() {
            return joined;
        }

        public boolean isIdempotent() {
            return idempotent;
        }
    }

    class QuitGroupResult {
        private final boolean quit;
        private final boolean idempotent;

        public QuitGroupResult(boolean quit, boolean idempotent) {
            this.quit = quit;
            this.idempotent = idempotent;
        }

        public boolean isQuit() {
            return quit;
        }

        public boolean isIdempotent() {
            return idempotent;
        }
    }
}
