package com.icc.qasker.board.kafka;

import com.icc.qasker.board.event.BoardEventPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BoardKafkaProducer {

  private static final String TOPIC = "board-events";
  private final KafkaTemplate<String, BoardEventPayload> kafkaTemplate;

  public void send(BoardEventPayload payload) {
    kafkaTemplate.send(TOPIC, payload);
  }
}
