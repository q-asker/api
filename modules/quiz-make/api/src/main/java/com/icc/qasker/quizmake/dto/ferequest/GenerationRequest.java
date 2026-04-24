package com.icc.qasker.quizmake.dto.ferequest;

import com.icc.qasker.quizmake.dto.ferequest.enums.Language;
import com.icc.qasker.quizset.dto.ferequest.enums.QuizType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import org.hibernate.validator.constraints.Length;
import org.hibernate.validator.constraints.UUID;

public record GenerationRequest(
    @Length(max = 600) String customInstruction,
    @NotNull(message = "sessionId가 null입니다.") @UUID(message = "sessionId가 유효한 UUID 형식이 아닙니다.")
        String sessionId,
    @NotBlank(message = "url이 존재하지 않습니다.") String uploadedUrl,
    @NotBlank(message = "title이 존재하지 않습니다.") String title,
    int quizCount,
    @NotNull(message = "quizType이 null입니다.") QuizType quizType,
    @NotNull(message = "pageNumbers가 null입니다.")
        @Size(min = 1, max = 150, message = "pageNumbers는 1개 이상 150 이하이어야 합니다.")
        List<
                @NotNull(message = "배열 요소가 null입니다.")
                @Min(value = 1, message = "배열 요소는 1 이상이어야 합니다.") Integer>
            pageNumbers,
    Language language) {

  private static final Set<Integer> ALLOWED_QUIZ_COUNTS = Set.of(5, 10, 15, 20);

  public GenerationRequest {
    if (!ALLOWED_QUIZ_COUNTS.contains(quizCount)) {
      throw new IllegalArgumentException(
          "quizCount는 " + ALLOWED_QUIZ_COUNTS + " 중 하나여야 합니다. 입력값: " + quizCount);
    }
    if (language == null) {
      language = Language.KO;
    }
  }
}
