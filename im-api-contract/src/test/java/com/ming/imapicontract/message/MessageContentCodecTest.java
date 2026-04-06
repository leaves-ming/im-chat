package com.ming.imapicontract.message;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageContentCodecTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void shouldNormalizeMessageType() {
        assertEquals(MessageContentCodec.MSG_TYPE_FILE, MessageContentCodec.normalizeMsgType("file"));
        assertEquals(MessageContentCodec.MSG_TYPE_TEXT, MessageContentCodec.normalizeMsgType("TEXT"));
        assertEquals(MessageContentCodec.MSG_TYPE_TEXT, MessageContentCodec.normalizeMsgType("unknown"));
        assertEquals(MessageContentCodec.MSG_TYPE_TEXT, MessageContentCodec.normalizeMsgType(null));
    }

    @Test
    void shouldSerializeAndDecodeTextContent() {
        String incoming = MessageContentCodec.validateAndSerializeIncomingContent("TEXT", mapper.getNodeFactory().textNode("hello"));
        String stored = MessageContentCodec.encodeForStorage("TEXT", incoming);

        assertEquals("hello", incoming);
        assertEquals("\"hello\"", stored);
        assertEquals("hello", MessageContentCodec.decodeFromStorage("TEXT", stored));
        assertEquals("raw-text", MessageContentCodec.decodeFromStorage("TEXT", "raw-text"));
    }

    @Test
    void shouldSerializeFileContentAndWriteProtocolNode() throws Exception {
        ObjectNode incoming = mapper.createObjectNode();
        incoming.put("uploadToken", "ut-1");

        String serialized = MessageContentCodec.validateAndSerializeIncomingContent("FILE", incoming);
        JsonNode protocolNode = MessageContentCodec.toProtocolContentNode("FILE", serialized);

        assertEquals("{\"uploadToken\":\"ut-1\"}", serialized);
        assertTrue(protocolNode.isObject());
        assertEquals("ut-1", protocolNode.get("uploadToken").asText());
        assertEquals(serialized, MessageContentCodec.decodeFromStorage("FILE", serialized));
    }

    @Test
    void shouldExtractUploadTokenFromIncomingFileContent() {
        assertEquals("ut-2", MessageContentCodec.extractIncomingUploadToken("{\"uploadToken\":\"ut-2\"}"));
    }

    @Test
    void shouldRejectInvalidFileContent() {
        IllegalArgumentException notObject = assertThrows(IllegalArgumentException.class,
                () -> MessageContentCodec.validateAndSerializeIncomingContent("FILE", mapper.getNodeFactory().textNode("bad")));
        IllegalArgumentException extraField = assertThrows(IllegalArgumentException.class,
                () -> MessageContentCodec.extractIncomingUploadToken("{\"uploadToken\":\"ut-3\",\"x\":1}"));
        IllegalArgumentException blankToken = assertThrows(IllegalArgumentException.class,
                () -> MessageContentCodec.extractIncomingUploadToken("{\"uploadToken\":\"\"}"));
        IllegalStateException badStoredJson = assertThrows(IllegalStateException.class,
                () -> MessageContentCodec.toProtocolContentNode("FILE", "{bad-json"));
        IllegalArgumentException blankText = assertThrows(IllegalArgumentException.class,
                () -> MessageContentCodec.validateAndSerializeIncomingContent("TEXT", mapper.getNodeFactory().textNode(" ")));

        assertEquals("content must be object when msgType=FILE", notObject.getMessage());
        assertEquals("content only supports uploadToken when msgType=FILE", extraField.getMessage());
        assertEquals("uploadToken must not be blank", blankToken.getMessage());
        assertEquals("parse file message content failed", badStoredJson.getMessage());
        assertEquals("content must not be blank", blankText.getMessage());
    }
}
