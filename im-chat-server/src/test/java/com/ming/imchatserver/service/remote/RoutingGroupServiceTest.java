package com.ming.imchatserver.service.remote;

import com.ming.imchatserver.dao.GroupMemberDO;
import com.ming.imchatserver.service.GroupService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoutingGroupServiceTest {

    @Test
    void createGroupShouldDelegateToRemoteService() {
        RemoteGroupService remoteGroupService = mock(RemoteGroupService.class);
        RoutingGroupService routingGroupService = new RoutingGroupService(remoteGroupService);
        GroupService.CreateGroupResult expected = new GroupService.CreateGroupResult(100L, "g123");
        when(remoteGroupService.createGroup(1L, "group-a", 200)).thenReturn(expected);

        GroupService.CreateGroupResult actual = routingGroupService.createGroup(1L, "group-a", 200);

        assertSame(expected, actual);
        verify(remoteGroupService).createGroup(1L, "group-a", 200);
    }

    @Test
    void shouldDelegateAllGroupQueriesToRemoteService() {
        RemoteGroupService remoteGroupService = mock(RemoteGroupService.class);
        RoutingGroupService routingGroupService = new RoutingGroupService(remoteGroupService);
        GroupService.JoinGroupResult joinResult = new GroupService.JoinGroupResult(true, false);
        GroupService.QuitGroupResult quitResult = new GroupService.QuitGroupResult(true, true);
        GroupMemberDO member = new GroupMemberDO();
        member.setGroupId(10L);
        member.setUserId(2L);
        GroupService.MemberPageResult pageResult = new GroupService.MemberPageResult(List.of(member), 2L, false);

        when(remoteGroupService.joinGroup(10L, 2L)).thenReturn(joinResult);
        when(remoteGroupService.quitGroup(10L, 2L)).thenReturn(quitResult);
        when(remoteGroupService.listMembers(10L, 1L, 50)).thenReturn(pageResult);
        when(remoteGroupService.isActiveMember(10L, 2L)).thenReturn(true);
        when(remoteGroupService.getActiveMember(10L, 2L)).thenReturn(member);
        when(remoteGroupService.canRecallMessage(10L, 2L, 3L)).thenReturn(true);
        when(remoteGroupService.listActiveMemberUserIds(10L)).thenReturn(List.of(2L, 3L));

        assertSame(joinResult, routingGroupService.joinGroup(10L, 2L));
        assertSame(quitResult, routingGroupService.quitGroup(10L, 2L));
        assertSame(pageResult, routingGroupService.listMembers(10L, 1L, 50));
        assertTrue(routingGroupService.isActiveMember(10L, 2L));
        assertSame(member, routingGroupService.getActiveMember(10L, 2L));
        assertTrue(routingGroupService.canRecallMessage(10L, 2L, 3L));
        assertEquals(List.of(2L, 3L), routingGroupService.listActiveMemberUserIds(10L));

        verify(remoteGroupService).joinGroup(10L, 2L);
        verify(remoteGroupService).quitGroup(10L, 2L);
        verify(remoteGroupService).listMembers(10L, 1L, 50);
        verify(remoteGroupService).isActiveMember(10L, 2L);
        verify(remoteGroupService).getActiveMember(10L, 2L);
        verify(remoteGroupService).canRecallMessage(10L, 2L, 3L);
        verify(remoteGroupService).listActiveMemberUserIds(10L);
    }
}
