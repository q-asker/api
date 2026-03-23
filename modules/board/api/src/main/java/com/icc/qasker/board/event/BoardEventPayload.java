package com.icc.qasker.board.event;

import java.time.Instant;

public record BoardEventPayload(
    BoardEventType eventType, Long boardId, String userId, String title, Instant occurredAt) {}
