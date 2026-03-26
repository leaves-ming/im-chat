package com.ming.imchatserver.mq;

import com.ming.imchatserver.config.ReliabilityProperties;
import com.ming.imchatserver.dao.OutboxMessageDO;
import com.ming.imchatserver.mapper.OutboxMapper;
import com.ming.imchatserver.metrics.MetricsService;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OutboxRelayJobTest - 代码模块说明。
 * <p>
 * 本类注释由统一规范补充，描述职责、边界与使用语义。
 */
class OutboxRelayJobTest {

    @Test
    /**
     * 方法说明。
     */
    void claimFailedShouldSkipSending() {
        OutboxMapper outboxMapper = mock(OutboxMapper.class);
        DispatchProducer dispatchProducer = mock(DispatchProducer.class);
        MetricsService metricsService = mock(MetricsService.class);
        ReliabilityProperties properties = new ReliabilityProperties();
        properties.setRelayBatchSize(10);

        OutboxMessageDO outbox = readyOutbox(1L);
        when(outboxMapper.findReadyBatch(any(Date.class), eq(10))).thenReturn(List.of(outbox));
        when(outboxMapper.claimForProcessing(eq(1L), any(Date.class))).thenReturn(0);

        OutboxRelayJob job = new OutboxRelayJob(outboxMapper, dispatchProducer, properties, metricsService);
        job.relay();

        verify(dispatchProducer, never()).sendSingleDispatch(any());
        verify(outboxMapper, never()).markSent(any());
        verify(outboxMapper, never()).markRetryOrDlq(any(), any(Integer.class), any(Integer.class), any(Date.class), any());
        verify(metricsService, never()).incrementRelaySend();
        verify(metricsService, never()).incrementRelayFail();
    }

    @Test
    /**
     * 方法说明。
     */
    void claimSucceededShouldSendAndMarkSent() {
        OutboxMapper outboxMapper = mock(OutboxMapper.class);
        DispatchProducer dispatchProducer = mock(DispatchProducer.class);
        MetricsService metricsService = mock(MetricsService.class);
        ReliabilityProperties properties = new ReliabilityProperties();
        properties.setRelayBatchSize(10);

        OutboxMessageDO outbox = readyOutbox(2L);
        when(outboxMapper.findReadyBatch(any(Date.class), eq(10))).thenReturn(List.of(outbox));
        when(outboxMapper.claimForProcessing(eq(2L), any(Date.class))).thenReturn(1);

        OutboxRelayJob job = new OutboxRelayJob(outboxMapper, dispatchProducer, properties, metricsService);
        job.relay();

        verify(metricsService).incrementRelaySend();
        verify(dispatchProducer).sendSingleDispatch(any(DispatchMessagePayload.class));
        verify(outboxMapper).markSent(2L);
        verify(outboxMapper, never()).markRetryOrDlq(any(), any(Integer.class), any(Integer.class), any(Date.class), any());
    }

    @Test
    /**
     * 方法说明。
     */
    void claimSucceededButSendFailedShouldMarkRetry() {
        OutboxMapper outboxMapper = mock(OutboxMapper.class);
        DispatchProducer dispatchProducer = mock(DispatchProducer.class);
        MetricsService metricsService = mock(MetricsService.class);
        ReliabilityProperties properties = new ReliabilityProperties();
        properties.setRelayBatchSize(10);
        properties.setMaxRetryCount(8);

        OutboxMessageDO outbox = readyOutbox(3L);
        outbox.setRetryCount(1);
        when(outboxMapper.findReadyBatch(any(Date.class), eq(10))).thenReturn(List.of(outbox));
        when(outboxMapper.claimForProcessing(eq(3L), any(Date.class))).thenReturn(1);
        doThrow(new IllegalStateException("mq down")).when(dispatchProducer).sendSingleDispatch(any(DispatchMessagePayload.class));

        OutboxRelayJob job = new OutboxRelayJob(outboxMapper, dispatchProducer, properties, metricsService);
        job.relay();

        verify(metricsService).incrementRelaySend();
        verify(metricsService).incrementRelayFail();
        verify(outboxMapper).markRetryOrDlq(eq(3L), eq(2), eq(2), any(Date.class), eq("mq down"));
        verify(outboxMapper, never()).markSent(any());
    }

    private OutboxMessageDO readyOutbox(Long id) {
        OutboxMessageDO outbox = new OutboxMessageDO();
        outbox.setId(id);
        outbox.setEventId("evt-" + id);
        outbox.setPayload("""
                {
                  "eventId":"evt-1",
                  "serverMsgId":"srv-1",
                  "clientMsgId":"cid-1",
                  "fromUserId":1,
                  "toUserId":2,
                  "content":"hello",
                  "msgType":"TEXT"
                }
                """);
        outbox.setRetryCount(0);
        return outbox;
    }
}
