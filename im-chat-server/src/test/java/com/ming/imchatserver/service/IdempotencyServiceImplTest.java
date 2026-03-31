package com.ming.imchatserver.service;

import com.ming.imchatserver.redis.RedisKeyFactory;
import com.ming.imchatserver.service.impl.IdempotencyServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class IdempotencyServiceImplTest {

    @Test
    void releaseClientMessageShouldDeleteRedisKey() {
        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        IdempotencyServiceImpl service = new IdempotencyServiceImpl(stringRedisTemplate, new RedisKeyFactory());

        service.releaseClientMessage(8L, "cid-1");

        verify(stringRedisTemplate).delete("im:idempotent:client_msg:8:cid-1");
    }

    @Test
    void releaseClientMessageShouldIgnoreBlankInput() {
        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        IdempotencyServiceImpl service = new IdempotencyServiceImpl(stringRedisTemplate, new RedisKeyFactory());

        service.releaseClientMessage(8L, " ");

        verify(stringRedisTemplate, never()).delete(org.mockito.ArgumentMatchers.anyString());
    }
}
