package com.icc.qasker.quizhistory.service.mock;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

/**
 * 자기정리 write mock은 {@code mock & !mockai} 가드를 가져야 한다 — 부하 트레이스(mock)에선 활성이되, 기능 E2E(mockai)에선 비활성돼
 * 실제 서비스가 이력·폴더를 영속하게 한다(생성 mock과 동일 패턴). 가드가 {@code mock} 으로 되돌아가면 mockai E2E에서 sentinel 충돌로 500이
 * 재발하므로 잠근다.
 */
class MockWriteServiceProfileTest {

  @Test
  @DisplayName("이력·폴더 write mock은 mockai에서 비활성되는 & !mockai 가드를 유지한다")
  void self_cleaning_write_mocks_carry_not_mockai_guard() {
    assertThat(profileOf(MockQuizHistoryCommandService.class)).containsExactly("mock & !mockai");
    assertThat(profileOf(MockQuizFolderCommandService.class)).containsExactly("mock & !mockai");
  }

  private static String[] profileOf(Class<?> type) {
    Profile profile = type.getAnnotation(Profile.class);
    assertThat(profile).as("%s 에 @Profile 이 있어야 한다", type.getSimpleName()).isNotNull();
    return profile.value();
  }
}
