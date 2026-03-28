package com.ming.imchatserver.service;

import com.ming.imchatserver.dao.ContactDO;
import com.ming.imchatserver.mapper.ContactMapper;
import com.ming.imchatserver.service.impl.ContactServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ContactServiceImplTest - 代码模块说明。
 * <p>
 * 本类注释由统一规范补充，描述职责、边界与使用语义。
 */
class ContactServiceImplTest {

    @Test
    void addAndRemoveShouldBeIdempotent() {
        ContactMapper mapper = mock(ContactMapper.class);
        ContactService service = new ContactServiceImpl(mapper);

        ContactDO active = relation(1L, 2L, 1);
        ContactDO deleted = relation(1L, 2L, 3);
        when(mapper.findByOwnerAndPeer(1L, 2L)).thenReturn(active, active, active, deleted);

        ContactService.Result add1 = service.addOrActivateContact(1L, 2L);
        ContactService.Result add2 = service.addOrActivateContact(1L, 2L);
        ContactService.Result del1 = service.removeOrDeactivateContact(1L, 2L);
        ContactService.Result del2 = service.removeOrDeactivateContact(1L, 2L);

        assertTrue(add1.isSuccess());
        assertTrue(add1.isIdempotent());
        assertTrue(add2.isIdempotent());
        assertTrue(del1.isSuccess());
        assertEquals(false, del1.isIdempotent());
        assertTrue(del2.isIdempotent());
        verify(mapper, never()).updateRelation(eq(1L), eq(2L), eq(1));
    }

    @Test
    void listActiveContactsShouldReturnStableCursorPage() {
        ContactMapper mapper = mock(ContactMapper.class);
        ContactService service = new ContactServiceImpl(mapper);

        when(mapper.pageActiveContacts(1L, 0L, 3)).thenReturn(List.of(
                relation(1L, 11L, 1),
                relation(1L, 22L, 1),
                relation(1L, 33L, 1)
        ));

        ContactService.ContactPageResult page = service.listActiveContacts(1L, 0L, 2);

        assertEquals(2, page.getItems().size());
        assertTrue(page.isHasMore());
        assertEquals(22L, page.getNextCursor());
        verify(mapper).pageActiveContacts(eq(1L), eq(0L), anyInt());
    }

    private ContactDO relation(Long ownerUserId, Long peerUserId, Integer status) {
        ContactDO r = new ContactDO();
        r.setOwnerUserId(ownerUserId);
        r.setPeerUserId(peerUserId);
        r.setRelationStatus(status);
        return r;
    }
}
