package com.ming.imchatserver.service.impl;

import com.ming.imchatserver.dao.ContactDO;
import com.ming.imchatserver.mapper.ContactMapper;
import com.ming.imchatserver.service.ContactService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link ContactService} 默认实现。
 */
@Service
public class ContactServiceImpl implements ContactService {

    private static final int RELATION_ACTIVE = 1;
    private static final int RELATION_DELETED = 3;
    private static final int MAX_LIST_LIMIT = 200;

    private final ContactMapper contactMapper;

    public ContactServiceImpl(ContactMapper contactMapper) {
        this.contactMapper = contactMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result addOrActivateContact(Long ownerUserId, Long peerUserId) {
        validatePair(ownerUserId, peerUserId);
        ContactDO before = contactMapper.findByOwnerAndPeer(ownerUserId, peerUserId);
        contactMapper.upsertRelation(ownerUserId, peerUserId, RELATION_ACTIVE, "USER");
        boolean idempotent = before != null && Integer.valueOf(RELATION_ACTIVE).equals(before.getRelationStatus());
        return new Result(true, idempotent);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result removeOrDeactivateContact(Long ownerUserId, Long peerUserId) {
        validatePair(ownerUserId, peerUserId);
        ContactDO before = contactMapper.findByOwnerAndPeer(ownerUserId, peerUserId);
        if (before == null) {
            contactMapper.upsertRelation(ownerUserId, peerUserId, RELATION_DELETED, "USER");
            return new Result(true, true);
        }
        contactMapper.updateRelation(ownerUserId, peerUserId, RELATION_DELETED);
        boolean idempotent = Integer.valueOf(RELATION_DELETED).equals(before.getRelationStatus());
        return new Result(true, idempotent);
    }

    @Override
    public ContactPageResult listActiveContacts(Long ownerUserId, Long cursorPeerUserId, Integer limit) {
        if (ownerUserId == null || ownerUserId <= 0) {
            throw new IllegalArgumentException("ownerUserId must be greater than 0");
        }
        int pageSize = normalizeLimit(limit);
        long cursor = cursorPeerUserId == null ? 0L : Math.max(0L, cursorPeerUserId);
        List<ContactDO> fetched = contactMapper.pageActiveContacts(ownerUserId, cursor, pageSize + 1);
        boolean hasMore = fetched.size() > pageSize;
        List<ContactDO> items = hasMore ? new ArrayList<>(fetched.subList(0, pageSize)) : fetched;
        Long nextCursor = items.isEmpty() ? null : items.get(items.size() - 1).getPeerUserId();
        return new ContactPageResult(items, nextCursor, hasMore);
    }

    @Override
    public boolean isActiveContact(Long ownerUserId, Long peerUserId) {
        if (ownerUserId == null || ownerUserId <= 0 || peerUserId == null || peerUserId <= 0) {
            return false;
        }
        ContactDO relation = contactMapper.findByOwnerAndPeer(ownerUserId, peerUserId);
        return relation != null && Integer.valueOf(RELATION_ACTIVE).equals(relation.getRelationStatus());
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return 50;
        }
        return Math.min(limit, MAX_LIST_LIMIT);
    }

    private void validatePair(Long ownerUserId, Long peerUserId) {
        if (ownerUserId == null || ownerUserId <= 0 || peerUserId == null || peerUserId <= 0) {
            throw new IllegalArgumentException("ownerUserId and peerUserId must be greater than 0");
        }
        if (ownerUserId.equals(peerUserId)) {
            throw new IllegalArgumentException("ownerUserId and peerUserId must be different");
        }
    }
}
