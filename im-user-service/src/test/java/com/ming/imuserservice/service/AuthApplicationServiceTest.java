package com.ming.imuserservice.service;

import com.ming.imapicontract.user.IntrospectTokenResponse;
import com.ming.imapicontract.user.LoginRequest;
import com.ming.imapicontract.user.LoginResponse;
import com.ming.imuserservice.dao.UserCoreDO;
import com.ming.imuserservice.service.impl.AuthApplicationServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthApplicationServiceTest {

    @Test
    void shouldIssueTokenWhenPasswordMatched() {
        UserDomainService userDomainService = mock(UserDomainService.class);
        JwtTokenService jwtTokenService = mock(JwtTokenService.class);
        LoginRiskControlService loginRiskControlService = mock(LoginRiskControlService.class);
        AuthApplicationServiceImpl service = new AuthApplicationServiceImpl(
                userDomainService, jwtTokenService, loginRiskControlService);

        UserCoreDO user = new UserCoreDO();
        user.setUserId(1001L);
        user.setUsername("alice");
        user.setPasswordHash("hash");
        when(userDomainService.findCoreByUsername("alice")).thenReturn(Optional.of(user));
        when(userDomainService.verifyPassword("pwd", "hash")).thenReturn(true);
        when(jwtTokenService.issueToken(1001L, "alice")).thenReturn("jwt-1");
        when(jwtTokenService.expiresInSeconds()).thenReturn(3600L);

        LoginResponse response = service.login(new LoginRequest("alice", "pwd", "127.0.0.1", "ios"));

        assertEquals(1001L, response.userId());
        assertEquals("jwt-1", response.token());
        assertEquals(3600L, response.expiresIn());
    }

    @Test
    void shouldThrowWhenRiskControlRejected() {
        UserDomainService userDomainService = mock(UserDomainService.class);
        JwtTokenService jwtTokenService = mock(JwtTokenService.class);
        LoginRiskControlService loginRiskControlService = mock(LoginRiskControlService.class);
        AuthApplicationServiceImpl service = new AuthApplicationServiceImpl(
                userDomainService, jwtTokenService, loginRiskControlService);

        UserCoreDO user = new UserCoreDO();
        user.setUserId(1001L);
        user.setUsername("alice");
        user.setPasswordHash("hash");
        when(userDomainService.findCoreByUsername("alice")).thenReturn(Optional.of(user));
        when(userDomainService.verifyPassword("bad", "hash")).thenReturn(false);
        when(loginRiskControlService.onLoginFailure("127.0.0.1", "ios", "alice")).thenReturn(false);

        assertThrows(AuthApplicationServiceImpl.TooManyLoginAttemptsException.class,
                () -> service.login(new LoginRequest("alice", "bad", "127.0.0.1", "ios")));
    }

    @Test
    void shouldReturnIntrospectResult() {
        UserDomainService userDomainService = mock(UserDomainService.class);
        JwtTokenService jwtTokenService = mock(JwtTokenService.class);
        LoginRiskControlService loginRiskControlService = mock(LoginRiskControlService.class);
        AuthApplicationServiceImpl service = new AuthApplicationServiceImpl(
                userDomainService, jwtTokenService, loginRiskControlService);
        IntrospectTokenResponse expected = new IntrospectTokenResponse(true, 1L, "alice", 1L, 2L);
        when(jwtTokenService.introspect("jwt")).thenReturn(expected);

        IntrospectTokenResponse actual = service.introspect("jwt");

        assertEquals(expected, actual);
    }
}
