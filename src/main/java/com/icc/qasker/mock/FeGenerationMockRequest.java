package com.icc.qasker.mock;

import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quiz.domain.enums.DifficultyType;
import com.icc.qasker.quiz.domain.enums.QuizType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FeGenerationMockRequest {

    @NotBlank(message = "url이 존재하지 않습니다.")
    private String uploadedUrl;
    @Min(value = 5, message = "quizCount는 5이상입니다.")
    @Max(value = 50, message = "quizCount는 50이하입니다.")
    private int quizCount;
    @NotNull(message = "quizType이 null입니다.")
    private QuizType quizType;
    @NotNull(message = "difficultyType가 null입니다.")
    private DifficultyType difficultyType;
    @NotNull(message = "pageNumbers가 null입니다.")
    @Size(min = 1, message = "pageNumbers는 최소 1개 이상이어야 합니다.")
    private List<Integer> pageNumbers;

    public FeGenerationMockRequest(
        String uploadedUrl,
        int quizCount,
        QuizType quizType,
        DifficultyType difficultyType,
        List<Integer> pageNumbers

    ) {
        this.uploadedUrl = uploadedUrl;
        this.quizCount = quizCount;
        this.quizType = quizType;
        this.difficultyType = difficultyType;
        this.pageNumbers = pageNumbers;

        validateQuizCount();
        validatePageSize();
    }

    public void validateQuizCount() {
        if (quizCount % 5 != 0) {
            throw new CustomException(ExceptionMessage.INVALID_QUIZ_COUNT_REQUEST);
        }
    }

    public void validatePageSize() {
        if (pageNumbers.size() > 100) {
            throw new CustomException(ExceptionMessage.INVALID_PAGE_REQUEST);
        }
    }

    private String encodePath(String path) {
        String[] segments = path.split("/");
        StringBuilder encoded = new StringBuilder();
        for (String segment : segments) {
            if (!segment.isEmpty()) {
                encoded.append("/").append(URLEncoder.encode(segment, StandardCharsets.UTF_8));
            }
        }
        return encoded.toString();
    }
}
