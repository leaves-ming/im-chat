package com.ming.imsocialservice.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 群成员分页查询请求 DTO。
 */
@Data
public class GroupMemberListRequestDTO {

    @Min(1)
    private Long groupId;

    @Min(0)
    private Long cursorUserId;

    @Min(1)
    private Integer limit;
}
