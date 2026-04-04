package com.ming.imapicontract.message;

import java.util.Date;
import java.util.List;

/**
 * 游标分页结果。
 */
public record CursorPageDTO(List<MessageDTO> messages,
                            boolean hasMore,
                            Date nextCursorCreatedAt,
                            Long nextCursorId) {
}
