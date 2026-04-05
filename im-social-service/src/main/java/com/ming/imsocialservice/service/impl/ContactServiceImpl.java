package com.ming.imsocialservice.service.impl;

import com.ming.imsocialservice.dao.ContactRelationDO;
import com.ming.imsocialservice.mapper.ContactRelationMapper;
import com.ming.imsocialservice.service.ContactService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 联系人服务默认实现。
 */
@Service
public class ContactServiceImpl implements ContactService {

    private static final int RELATION_ACTIVE = 1;
    private static final int RELATION_DELETED = 3;
    private static final int MAX_LIST_LIMIT = 200;

    private final ContactRelationMapper contactRelationMapper;

    public ContactServiceImpl(ContactRelationMapper contactRelationMapper) {
        this.contactRelationMapper = contactRelationMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result addOrActivateContact(Long ownerUserId, Long peerUserId) {
        validatePair(ownerUserId, peerUserId);
        ContactRelationDO before = contactRelationMapper.findByOwnerAndPeer(ownerUserId, peerUserId);
        contactRelationMapper.upsertRelation(ownerUserId, peerUserId, RELATION_ACTIVE, "USER");
        boolean idempotent = before != null && Integer.valueOf(RELATION_ACTIVE).equals(before.getRelationStatus());
        return new Result(true, idempotent);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Result removeOrDeactivateContact(Long ownerUserId, Long peerUserId) {
        validatePair(ownerUserId, peerUserId);
        ContactRelationDO before = contactRelationMapper.findByOwnerAndPeer(ownerUserId, peerUserId);
        if (before == null) {
            contactRelationMapper.upsertRelation(ownerUserId, peerUserId, RELATION_DELETED, "USER");
            return new Result(true, true);
        }
        contactRelationMapper.updateRelation(ownerUserId, peerUserId, RELATION_DELETED);
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
        List<ContactRelationDO> fetched = contactRelationMapper.pageActiveContacts(ownerUserId, cursor, pageSize + 1);
        boolean hasMore = fetched.size() > pageSize;
        List<ContactRelationDO> items = hasMore ? new ArrayList<>(fetched.subList(0, pageSize)) : fetched;
        Long nextCursor = items.isEmpty() ? null : items.getLast().getPeerUserId();
        return new ContactPageResult(items, nextCursor, hasMore);
    }

    @Override
    public boolean isActiveContact(Long ownerUserId, Long peerUserId) {
        if (ownerUserId == null || ownerUserId <= 0 || peerUserId == null || peerUserId <= 0) {
            return false;
        }
        ContactRelationDO relation = contactRelationMapper.findByOwnerAndPeer(ownerUserId, peerUserId);
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
