package com.icc.qasker.global.query;

import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.slf4j.MDC;

/** Hibernate가 매 SQL 실행 직전 호출 — 요청당 쿼리수 카운트 + reqId·uri·repoMethod 주석 태깅. */
public class CountingInspector implements StatementInspector {

  @Override
  public String inspect(String sql) {
    QueryCounter.increase();
    String reqId = MDC.get("reqId");
    if (reqId == null) {
      // 요청 컨텍스트 밖(스케줄러 등) — 태깅 생략
      return sql;
    }
    String repoMethod = RepoMethodContext.resolve();
    return "/* reqId="
        + reqId
        + " uri="
        + MDC.get("uri")
        + " repoMethod="
        + (repoMethod == null ? "" : repoMethod)
        + " */"
        + sql;
  }
}
