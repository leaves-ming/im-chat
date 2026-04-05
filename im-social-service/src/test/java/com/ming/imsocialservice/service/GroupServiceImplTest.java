package com.ming.imsocialservice.service;

import com.ming.imsocialservice.dao.SocialGroupDO;
import com.ming.imsocialservice.group.GroupDomainConstants;
import com.ming.imsocialservice.mapper.SocialGroupMapper;
import com.ming.imsocialservice.mapper.SocialGroupMemberMapper;
import com.ming.imsocialservice.service.impl.GroupServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class GroupServiceImplTest {

    @Test
    void createGroupShouldInsertGroupAndOwner() {
        SocialGroupMapper groupMapper = mock(SocialGroupMapper.class);
        SocialGroupMemberMapper groupMemberMapper = mock(SocialGroupMemberMapper.class);
        GroupService service = new GroupServiceImpl(groupMapper, groupMemberMapper);

        doAnswer(invocation -> {
            SocialGroupDO group = invocation.getArgument(0);
            group.setId(100L);
            return 1;
        }).when(groupMapper).insertGroup(any(SocialGroupDO.class));

        GroupService.CreateGroupResult result = service.createGroup(1L, " test-group ", null);

        assertEquals(100L, result.getGroupId());
        assertNotNull(result.getGroupNo());
        ArgumentCaptor<SocialGroupDO> groupCaptor = ArgumentCaptor.forClass(SocialGroupDO.class);
        verify(groupMapper).insertGroup(groupCaptor.capture());
        SocialGroupDO inserted = groupCaptor.getValue();
        assertEquals(1L, inserted.getOwnerUserId());
        assertEquals("test-group", inserted.getName());
        assertEquals(GroupDomainConstants.GROUP_STATUS_ACTIVE, inserted.getStatus());
        assertEquals(1000, inserted.getMemberLimit());
        verify(groupMemberMapper).insertOwner(100L, 1L);
    }
}
