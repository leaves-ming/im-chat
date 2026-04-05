package com.ming.imchatserver.application.model;

public record ContactOperationResult(boolean success, boolean idempotent) {
}
