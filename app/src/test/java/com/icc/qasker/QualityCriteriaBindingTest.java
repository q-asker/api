package com.icc.qasker;

import static org.assertj.core.api.Assertions.assertThat;

import com.icc.qasker.ai.properties.QualityProperties;
import com.icc.qasker.ai.service.quality.prompt.QualityCriterion;
import com.icc.qasker.ai.service.quality.prompt.Strictness;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * ai-setting.yml 의 검증 항목(q-asker.ai.quality.criteria)이 enum 으로 빠짐없이 바인딩되는지 확인한다.
 *
 * <p>키·값이 enum 이라 오타는 기동 실패가 되지만, 항목을 통째로 빠뜨리면 조용히 사라진다 — 그 누락을 여기서 잡는다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class QualityCriteriaBindingTest {

  @Autowired private QualityProperties properties;

  @Test
  void everyCriterionIsConfigured() {
    assertThat(properties.getCriteria())
        .containsOnlyKeys(QualityCriterion.values())
        .doesNotContainValue(null);
  }

  @Test
  void strictnessIsBoundAsEnum() {
    assertThat(properties.getCriteria())
        .containsEntry(QualityCriterion.CONSTRUCTION_STRATEGY, Strictness.STRICT)
        .containsEntry(QualityCriterion.DISTRACTORS_PLAUSIBLE, Strictness.NORMAL);
  }
}
