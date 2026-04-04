package com.ming.imsocialservice.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 联系人分页查询请求 DTO。
 */
@Data
public class ContactListRequestDTO {

    @Min(1)
    private Long ownerUserId;

    @Min(0)
    private Long cursorPeerUserId;

    @Min(1)
    private Integer limit;
}
