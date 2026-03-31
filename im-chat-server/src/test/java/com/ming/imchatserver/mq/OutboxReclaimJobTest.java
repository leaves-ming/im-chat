package com.ming.imchatserver.mq;

import com.ming.imchatserver.config.ReliabilityProperties;
import com.ming.imchatserver.mapper.OutboxMapper;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OutboxReclaimJobTest - 代码模块说明。
 * <p>
 * 本类注释由统一规范补充，描述职责、边界与使用语义。
 */
class OutboxReclaimJobTest {

    @Test
    void timeoutProcessingShouldBeReclaimed() {
        OutboxMapper outboxMapper = mock(OutboxMapper.class);
        ReliabilityProperties properties = new ReliabilityProperties();
        properties.setProcessingTimeoutMs(30_000L);
        when(outboxMapper.reclaimTimeoutProcessing(any(Date.class), any(Date.class))).thenReturn(2);

        OutboxReclaimJob job = new OutboxReclaimJob(outboxMapper, properties, null);
        job.reclaimTimeoutProcessing();

        verify(outboxMapper).reclaimTimeoutProcessing(any(Date.class), any(Date.class));
    }

    @Test
    void nonTimeoutProcessingShouldNotBeReclaimed() {
        OutboxMapper outboxMapper = mock(OutboxMapper.class);
        ReliabilityProperties properties = new ReliabilityProperties();
        properties.setProcessingTimeoutMs(30_000L);
        when(outboxMapper.reclaimTimeoutProcessing(any(Date.class), any(Date.class))).thenReturn(0);

        OutboxReclaimJob job = new OutboxReclaimJob(outboxMapper, properties, null);
        job.reclaimTimeoutProcessing();

        verify(outboxMapper).reclaimTimeoutProcessing(any(Date.class), any(Date.class));
        verify(outboxMapper, never()).markRetryOrDlq(any(), any(Integer.class), any(Integer.class), any(Date.class), any());
    }
}
