package com.ming.imapicontract.user;

/**
 * 用户查询结果。
 */
public record UserDTO(
        Long userId,
        String accountNo,
        String username,
        String nickname,
        String avatar,
        Integer sex,
        Integer activeStatus,
        String lastLoginIp
) {
}
