package com.ming.imchatserver.service;

import com.ming.imchatserver.dao.GroupDO;
import com.ming.imchatserver.dao.GroupMemberDO;
import com.ming.imchatserver.group.GroupDomainConstants;
import com.ming.imchatserver.mapper.GroupMapper;
import com.ming.imchatserver.mapper.GroupMemberMapper;
import com.ming.imchatserver.service.impl.GroupServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GroupServiceImplTest - 代码模块说明。
 * <p>
 * 本类注释由统一规范补充，描述职责、边界与使用语义。
 */
class GroupServiceImplTest {

    @Test
    /**
     * 方法说明。
     */
    void createGroupShouldInsertGroupAndOwner() {
        GroupMapper groupMapper = mock(GroupMapper.class);
        GroupMemberMapper groupMemberMapper = mock(GroupMemberMapper.class);
        GroupService service = new GroupServiceImpl(groupMapper, groupMemberMapper);

        doAnswer(invocation -> {
            GroupDO group = invocation.getArgument(0);
            group.setId(100L);
            return 1;
        }).when(groupMapper).insertGroup(any(GroupDO.class));

        GroupService.CreateGroupResult result = service.createGroup(1L, "test-group", 1000);

        assertEquals(100L, result.getGroupId());
        assertNotNull(result.getGroupNo());
        ArgumentCaptor<GroupDO> groupCaptor = ArgumentCaptor.forClass(GroupDO.class);
        verify(groupMapper).insertGroup(groupCaptor.capture());
        GroupDO inserted = groupCaptor.getValue();
        assertEquals(1L, inserted.getOwnerUserId());
        assertEquals(GroupDomainConstants.GROUP_STATUS_ACTIVE, inserted.getStatus());
        verify(groupMemberMapper).insertOwner(100L, 1L);
    }

    @Test
    /**
     * 方法说明。
     */
    void joinGroupShouldBeIdempotentWhenAlreadyActive() {
        GroupMapper groupMapper = mock(GroupMapper.class);
        GroupMemberMapper groupMemberMapper = mock(GroupMemberMapper.class);
        GroupService service = new GroupServiceImpl(groupMapper, groupMemberMapper);

        when(groupMapper.findById(1L)).thenReturn(activeGroup(1L, 10L, 1000));
        GroupMemberDO activeMember = new GroupMemberDO();
        activeMember.setGroupId(1L);
        activeMember.setUserId(2L);
        activeMember.setMemberStatus(GroupDomainConstants.MEMBER_STATUS_ACTIVE);
        when(groupMemberMapper.findActiveMember(1L, 2L)).thenReturn(activeMember);

        GroupService.JoinGroupResult result = service.joinGroup(1L, 2L);

        assertTrue(result.isJoined());
        assertTrue(result.isIdempotent());
        verify(groupMemberMapper, never()).upsertJoin(any(), any(), any(Integer.class), any(Integer.class));
    }

    @Test
    /**
     * 方法说明。
     */
    void joinGroupShouldFailWhenFull() {
        GroupMapper groupMapper = mock(GroupMapper.class);
        GroupMemberMapper groupMemberMapper = mock(GroupMemberMapper.class);
        GroupService service = new GroupServiceImpl(groupMapper, groupMemberMapper);

        when(groupMapper.findById(1L)).thenReturn(activeGroup(1L, 10L, 2));
        when(groupMemberMapper.findActiveMember(1L, 3L)).thenReturn(null);
        when(groupMemberMapper.countActiveMembers(1L)).thenReturn(2);

        GroupBizException ex = assertThrows(GroupBizException.class, () -> service.joinGroup(1L, 3L));
        assertEquals(GroupErrorCode.GROUP_FULL, ex.getCode());
        verify(groupMemberMapper, never()).upsertJoin(any(), any(), any(Integer.class), any(Integer.class));
    }

    @Test
    /**
     * 方法说明。
     */
    void quitGroupOwnerShouldFail() {
        GroupMapper groupMapper = mock(GroupMapper.class);
        GroupMemberMapper groupMemberMapper = mock(GroupMemberMapper.class);
        GroupService service = new GroupServiceImpl(groupMapper, groupMemberMapper);

        when(groupMapper.findById(1L)).thenReturn(activeGroup(1L, 9L, 1000));

        GroupBizException ex = assertThrows(GroupBizException.class, () -> service.quitGroup(1L, 9L));
        assertEquals(GroupErrorCode.OWNER_CANNOT_QUIT, ex.getCode());
        verify(groupMemberMapper, never()).markQuit(any(), any());
    }

    @Test
    /**
     * 方法说明。
     */
    void quitGroupShouldReturnIdempotentWhenNotActiveMember() {
        GroupMapper groupMapper = mock(GroupMapper.class);
        GroupMemberMapper groupMemberMapper = mock(GroupMemberMapper.class);
        GroupService service = new GroupServiceImpl(groupMapper, groupMemberMapper);

        when(groupMapper.findById(1L)).thenReturn(activeGroup(1L, 9L, 1000));
        when(groupMemberMapper.markQuit(1L, 8L)).thenReturn(0);

        GroupService.QuitGroupResult result = service.quitGroup(1L, 8L);
        assertTrue(result.isQuit());
        assertTrue(result.isIdempotent());
    }

    @Test
    /**
     * 方法说明。
     */
    void listMembersShouldReturnStableCursorPage() {
        GroupMapper groupMapper = mock(GroupMapper.class);
        GroupMemberMapper groupMemberMapper = mock(GroupMemberMapper.class);
        GroupService service = new GroupServiceImpl(groupMapper, groupMemberMapper);

        when(groupMapper.findById(1L)).thenReturn(activeGroup(1L, 9L, 1000));
        when(groupMemberMapper.pageActiveMembers(1L, 0L, 3)).thenReturn(List.of(member(11L), member(22L), member(33L)));

        GroupService.MemberPageResult page = service.listMembers(1L, 0L, 2);

        assertEquals(2, page.getItems().size());
        assertTrue(page.isHasMore());
        assertEquals(22L, page.getNextCursor());
    }

    @Test
    void joinGroupShouldBeIdempotentSuccessUnderConcurrencyAndKeepSingleActiveRelation() throws Exception {
        GroupMapper groupMapper = mock(GroupMapper.class);
        GroupMemberMapper groupMemberMapper = mock(GroupMemberMapper.class);
        GroupService service = new GroupServiceImpl(groupMapper, groupMemberMapper);

        when(groupMapper.findById(1L)).thenReturn(activeGroup(1L, 10L, 1000));

        Set<Long> activeUsers = ConcurrentHashMap.newKeySet();
        when(groupMemberMapper.findActiveMember(1L, 2L)).thenAnswer(invocation -> {
            if (!activeUsers.contains(2L)) {
                return null;
            }
            GroupMemberDO member = new GroupMemberDO();
            member.setGroupId(1L);
            member.setUserId(2L);
            member.setMemberStatus(GroupDomainConstants.MEMBER_STATUS_ACTIVE);
            return member;
        });
        when(groupMemberMapper.upsertJoin(1L, 2L, GroupDomainConstants.MEMBER_ROLE_MEMBER, GroupDomainConstants.MEMBER_STATUS_ACTIVE))
                .thenAnswer(invocation -> {
                    activeUsers.add(2L);
                    return 1;
                });
        when(groupMemberMapper.countActiveMembers(1L)).thenAnswer(invocation -> activeUsers.size());

        int concurrency = 16;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch ready = new CountDownLatch(concurrency);
        List<Future<GroupService.JoinGroupResult>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < concurrency; i++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await(3, TimeUnit.SECONDS);
                return service.joinGroup(1L, 2L);
            }));
        }
        ready.await(3, TimeUnit.SECONDS);
        start.countDown();

        int exceptionCount = 0;
        for (Future<GroupService.JoinGroupResult> future : futures) {
            try {
                GroupService.JoinGroupResult result = future.get(3, TimeUnit.SECONDS);
                assertTrue(result.isJoined());
            } catch (Exception ex) {
                exceptionCount++;
            }
        }
        executor.shutdownNow();

        assertEquals(0, exceptionCount);
        assertEquals(1, activeUsers.size());
        assertFalse(activeUsers.isEmpty());
        verify(groupMemberMapper, never()).markQuit(any(), any());
    }

    @Test
    void canRecallMessageShouldRespectRoleHierarchy() {
        GroupMapper groupMapper = mock(GroupMapper.class);
        GroupMemberMapper groupMemberMapper = mock(GroupMemberMapper.class);
        GroupService service = new GroupServiceImpl(groupMapper, groupMemberMapper);

        when(groupMapper.findById(1L)).thenReturn(activeGroup(1L, 9L, 1000));
        when(groupMemberMapper.findActiveMember(1L, 9L)).thenReturn(memberWithRole(9L, GroupDomainConstants.MEMBER_ROLE_OWNER));
        when(groupMemberMapper.findActiveMember(1L, 8L)).thenReturn(memberWithRole(8L, GroupDomainConstants.MEMBER_ROLE_ADMIN));
        when(groupMemberMapper.findActiveMember(1L, 7L)).thenReturn(memberWithRole(7L, GroupDomainConstants.MEMBER_ROLE_MEMBER));

        assertTrue(service.canRecallMessage(1L, 9L, 8L));
        assertTrue(service.canRecallMessage(1L, 8L, 7L));
        assertFalse(service.canRecallMessage(1L, 7L, 8L));
        assertFalse(service.canRecallMessage(1L, 8L, 9L));
    }

    private GroupDO activeGroup(Long id, Long ownerUserId, int memberLimit) {
        GroupDO group = new GroupDO();
        group.setId(id);
        group.setOwnerUserId(ownerUserId);
        group.setStatus(GroupDomainConstants.GROUP_STATUS_ACTIVE);
        group.setMemberLimit(memberLimit);
        return group;
    }

    private GroupMemberDO member(Long userId) {
        GroupMemberDO member = new GroupMemberDO();
        member.setUserId(userId);
        member.setMemberStatus(GroupDomainConstants.MEMBER_STATUS_ACTIVE);
        return member;
    }

    private GroupMemberDO memberWithRole(Long userId, Integer role) {
        GroupMemberDO member = member(userId);
        member.setRole(role);
        return member;
    }
}
