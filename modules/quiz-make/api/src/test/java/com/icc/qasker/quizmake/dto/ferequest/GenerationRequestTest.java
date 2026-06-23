package com.icc.qasker.quizmake.dto.ferequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.icc.qasker.quizmake.dto.ferequest.enums.Language;
import com.icc.qasker.quizset.dto.ferequest.enums.QuizType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class GenerationRequestTest {

  @Nested
  @DisplayName("ESSAY quizCount 정책")
  class EssayPolicy {

    @ParameterizedTest
    @ValueSource(ints = {5, 10})
    @DisplayName("ESSAY는 5, 10만 허용한다")
    void essay_allows_5_and_10(int quizCount) {
      assertThat(buildRequest(QuizType.ESSAY, quizCount, Language.KO)).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {15, 20, 25, 30})
    @DisplayName("ESSAY는 DEFAULT 집합의 다른 값(15/20/25/30)을 거부한다")
    void essay_rejects_default_only_values(int quizCount) {
      assertThatThrownBy(() -> buildRequest(QuizType.ESSAY, quizCount, Language.KO))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("ESSAY")
          .hasMessageContaining(String.valueOf(quizCount));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 7, 11, 100})
    @DisplayName("ESSAY는 허용 집합 밖의 임의 값을 거부한다")
    void essay_rejects_arbitrary_values(int quizCount) {
      assertThatThrownBy(() -> buildRequest(QuizType.ESSAY, quizCount, Language.KO))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("ESSAY");
    }
  }

  @Nested
  @DisplayName("DEFAULT(비ESSAY) quizCount 정책 회귀")
  class DefaultPolicy {

    @ParameterizedTest
    @EnumSource(
        value = QuizType.class,
        names = {"MULTIPLE", "BLANK", "REAL_BLANK", "OX"})
    @DisplayName("비ESSAY 타입은 5, 10, 15, 20, 25, 30을 모두 허용한다")
    void non_essay_allows_all_default_counts(QuizType quizType) {
      for (int count : List.of(5, 10, 15, 20, 25, 30)) {
        assertThat(buildRequest(quizType, count, Language.KO)).isNotNull();
      }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 7, 11, 100})
    @DisplayName("비ESSAY 타입도 허용 집합 밖의 값은 거부한다")
    void non_essay_rejects_invalid(int quizCount) {
      assertThatThrownBy(() -> buildRequest(QuizType.MULTIPLE, quizCount, Language.KO))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("MULTIPLE")
          .hasMessageContaining(String.valueOf(quizCount));
    }
  }

  @Nested
  @DisplayName("language 기본값 보정")
  class LanguageDefault {

    @Test
    @DisplayName("language가 null이면 KO로 보정된다")
    void null_language_defaults_to_ko() {
      GenerationRequest req = buildRequest(QuizType.MULTIPLE, 5, null);
      assertThat(req.language()).isEqualTo(Language.KO);
    }

    @Test
    @DisplayName("language가 명시되면 그 값을 유지한다")
    void explicit_language_preserved() {
      GenerationRequest req = buildRequest(QuizType.MULTIPLE, 5, Language.EN);
      assertThat(req.language()).isEqualTo(Language.EN);
    }
  }

  private static GenerationRequest buildRequest(QuizType quizType, int quizCount, Language lang) {
    return new GenerationRequest(
        null,
        UUID.randomUUID().toString(),
        "https://example.com/file.pdf",
        "title",
        quizCount,
        quizType,
        List.of(1, 2),
        lang);
  }
}
