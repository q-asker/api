package com.icc.qasker.quiz.dto.request;

import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import com.icc.qasker.quiz.domain.enums.DifficultyType;
import com.icc.qasker.quiz.domain.enums.QuizType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.Getter;

@Getter
public class FeGenerationRequest {

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

    public FeGenerationRequest(
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

        if (this.quizType == QuizType.MULTIPLE && this.difficultyType == DifficultyType.RECALL) {
            this.quizType = QuizType.BLANK;
        }
        validateUploadedUrl();
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

    public void validateUploadedUrl() {
        try {
            URL url = new URL(uploadedUrl);
            String encodedPath = encodePath(url.getPath());
            URL encodedUrl = new URL(url.getProtocol(), url.getHost(), url.getPort(), encodedPath);
            HttpURLConnection connection = (HttpURLConnection) encodedUrl.openConnection();
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new CustomException(ExceptionMessage.FILE_NOT_FOUND_ON_S3);
            }

        } catch (Exception e) {
            throw new CustomException(ExceptionMessage.FILE_NOT_FOUND_ON_S3);
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
