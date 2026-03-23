package com.icc.qasker.board.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icc.qasker.board.entity.BoardEventOutbox;
import com.icc.qasker.board.event.BoardEventPayload;
import com.icc.qasker.board.kafka.BoardKafkaProducer;
import com.icc.qasker.board.repository.BoardEventOutboxRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BoardMessageRelayService {

  private final BoardEventOutboxRepository outboxRepository;
  private final BoardKafkaProducer kafkaProducer;
  private final ObjectMapper objectMapper;

  @Scheduled(fixedDelay = 2000)
  @Transactional
  public void relay() {
    List<BoardEventOutbox> pending = outboxRepository.findAllByPublishedFalse();
    for (BoardEventOutbox outbox : pending) {
      try {
        BoardEventPayload payload =
            objectMapper.readValue(outbox.getPayload(), BoardEventPayload.class);
        kafkaProducer.send(payload);
        outbox.markPublished();
      } catch (Exception e) {
        log.error("Kafka 발행 실패 - outboxId: {}", outbox.getId(), e);
      }
    }
  }
}
