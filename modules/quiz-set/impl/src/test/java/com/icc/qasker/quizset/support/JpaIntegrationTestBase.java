package com.icc.qasker.quizset.support;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

/**
 * H2 기반 JPA 슬라이스 통합 테스트 베이스. Hibernate statistics를 활성화해 쿼리 수·엔티티 로드 수 단언을 지원한다(FR-004/005/006 검증용).
 */
@DataJpaTest
@TestPropertySource(
    properties = {
      "spring.jpa.properties.hibernate.generate_statistics=true",
      "spring.jpa.hibernate.ddl-auto=create-drop"
    })
public abstract class JpaIntegrationTestBase {

  @PersistenceContext protected EntityManager em;

  @BeforeEach
  void clearStatistics() {
    statistics().clear();
  }

  protected Statistics statistics() {
    return em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
  }

  protected long queryExecutionCount() {
    return statistics().getQueryExecutionCount();
  }

  protected void flushAndClear() {
    em.flush();
    em.clear();
  }
}
