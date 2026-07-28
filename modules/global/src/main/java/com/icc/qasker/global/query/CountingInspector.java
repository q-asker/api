package com.icc.qasker.global.query;

import java.util.Objects;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.slf4j.MDC;

/** Hibernate가 매 SQL 실행 직전 호출 — 요청당 쿼리수 카운트 + reqId·uri·repoMethod·mode 주석 태깅. */
public class CountingInspector implements StatementInspector {

  private final String modeTag;

  public CountingInspector(String mode) {
    this.modeTag = mode == null || mode.isBlank() ? "" : " mode=%-3s".formatted(mode);
  }

  @Override
  public String inspect(String sql) {
    QueryCounter.increase();
    String reqId = MDC.get("reqId");
    if (reqId == null) {
      // 요청 컨텍스트 밖(스케줄러 등) — 태깅 생략
      return sql;
    }
    String repoMethod = Objects.requireNonNullElse(RepoMethodContext.resolve(), "");
    return "/* reqId=%s uri=%s repoMethod=%s%s */"
            .formatted(reqId, MDC.get("uri"), repoMethod, modeTag)
        + sql;
  }
}
