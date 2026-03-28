package com.ming.imchatserver.service;

import com.ming.imchatserver.dao.ContactDO;

import java.util.List;

/**
 * 联系人服务（MVP）。
 */
public interface ContactService {

    Result addOrActivateContact(Long ownerUserId, Long peerUserId);

    Result removeOrDeactivateContact(Long ownerUserId, Long peerUserId);

    ContactPageResult listActiveContacts(Long ownerUserId, Long cursorPeerUserId, Integer limit);

    boolean isActiveContact(Long ownerUserId, Long peerUserId);

    class Result {
        private final boolean success;
        private final boolean idempotent;

        public Result(boolean success, boolean idempotent) {
            this.success = success;
            this.idempotent = idempotent;
        }

        public boolean isSuccess() {
            return success;
        }

        public boolean isIdempotent() {
            return idempotent;
        }
    }

    class ContactPageResult {
        private final List<ContactDO> items;
        private final Long nextCursor;
        private final boolean hasMore;

        public ContactPageResult(List<ContactDO> items, Long nextCursor, boolean hasMore) {
            this.items = items;
            this.nextCursor = nextCursor;
            this.hasMore = hasMore;
        }

        public List<ContactDO> getItems() {
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
