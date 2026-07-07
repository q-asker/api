package com.icc.qasker.quizset.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * 실행 SQL을 가로채 수집하는 Hibernate StatementInspector. 인핸스먼트 쓰기 시나리오 테스트에서 실제 UPDATE 문의 컬럼 구성을 검사하는 데 쓴다.
 * Hibernate가 no-arg로 인스턴스화하므로 수집 리스트는 static이다.
 */
public class SqlCapture implements StatementInspector {

  private static final List<String> STATEMENTS = new CopyOnWriteArrayList<>();

  @Override
  public String inspect(String sql) {
    if (sql != null) {
      STATEMENTS.add(sql);
    }
    return sql;
  }

  public static void clear() {
    STATEMENTS.clear();
  }

  /** 지정 테이블에 대한 UPDATE 문만 소문자로 정규화해 반환한다. */
  public static List<String> updatesFor(String table) {
    return STATEMENTS.stream()
        .map(s -> s.toLowerCase().replaceAll("\\s+", " ").trim())
        .filter(s -> s.startsWith("update " + table.toLowerCase() + " "))
        .collect(Collectors.toList());
  }
}
