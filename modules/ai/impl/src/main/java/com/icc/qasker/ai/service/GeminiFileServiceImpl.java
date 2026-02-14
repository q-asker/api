package com.icc.qasker.ai.service;

import com.icc.qasker.ai.GeminiFileService;
import com.icc.qasker.ai.dto.GeminiFileUploadResponse;
import com.icc.qasker.ai.dto.GeminiFileUploadResponse.FileMetadata;
import com.icc.qasker.ai.util.PdfUtils;
import com.icc.qasker.global.error.CustomException;
import com.icc.qasker.global.error.ExceptionMessage;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.model.google.genai.autoconfigure.chat.GoogleGenAiConnectionProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
public class GeminiFileServiceImpl implements GeminiFileService {

    private static final int POLL_INTERVAL_MS = 1_000;
    private static final int MAX_POLL_ATTEMPTS = 30;
    private static final String STATE_ACTIVE = "ACTIVE";
    private static final String STATE_FAILED = "FAILED";

    private final RestClient restClient;
    private final String apiKey;
    private final PdfUtils pdfUtils;

    public GeminiFileServiceImpl(
        @Qualifier("geminiFileRestClient")
        RestClient restClient,
        GoogleGenAiConnectionProperties properties,
        PdfUtils pdfUtils
    ) {
        this.restClient = restClient;
        this.apiKey = properties.getApiKey();
        this.pdfUtils = pdfUtils;
    }

    @Override
    public FileMetadata uploadPdf(String pdfUrl) {
        Path tempFile = null;

        try {
            tempFile = pdfUtils.downloadToTemp(pdfUrl);
            long fileSize = Files.size(tempFile);

            String uploadSessionUrl = initiateUpload(fileSize, extractFileName(pdfUrl));

            GeminiFileUploadResponse response = uploadBytes(uploadSessionUrl, tempFile, fileSize);

            String fileName = response.file().name();

            log.info("PDF 업로드 완료: name={}, state={]", fileName, response.file().state());

            FileMetadata metadata = waitForProcessing(fileName);
            log.info("파일 처리 완료: name={}, uri={}", metadata.name(), metadata.uri());
            return metadata;
        } catch (IOException e) {
            log.error("PDF 업로드 중 I/O 오류: {}", e.getMessage());
            throw new CustomException(ExceptionMessage.AI_SERVER_COMMUNICATION_ERROR);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("PDF 처리 대기 중 인터럽트 발생");
            throw new CustomException(ExceptionMessage.AI_SERVER_COMMUNICATION_ERROR);
        } finally {
            pdfUtils.deleteTempFile(tempFile);
        }
    }

    @Override
    public void deleteFile(String fileName) {
        try {
            restClient.delete()
                .uri("/v1beta/" + fileName + "?key={key}", apiKey)
                .retrieve()
                .toBodilessEntity();

            log.info("Gemini 파일 삭제 완료: name={}", fileName);
        } catch (Exception e) {
            log.warn("Gemini 파일 삭제 실패 (무시): name={}, error={}", fileName, e.getMessage());
        }
    }

    private String initiateUpload(long fileSize, String displayName) {
        Map<String, Map<String, String>> requestBody = Map.of("file",
            Map.of("display_name", displayName));

        // exchange()를 사용하는 이유: 응답 헤더에서 업로드 세션 URL을 추출해야 하기 때문
        // retrieve()는 body만 반환하므로 헤더 접근 불가
        return restClient.post()
            .uri("/upload/v1beta/files?key={key}", apiKey)
            .header("X-Goog-Upload-Protocol", "resumable")
            .header("X-Goog-Upload-Command", "start")
            .header("X-Goog-Upload-Header-Content-Type", "application/pdf")
            .header("X-Goog-Upload-Header-Content-Length", String.valueOf(fileSize))
            .contentType(MediaType.APPLICATION_JSON)
            .exchange((req, res) -> {
                if (!res.getStatusCode().is2xxSuccessful()) {
                    throw new CustomException(ExceptionMessage.AI_SERVER_RESPONSE_ERROR);
                }
                String url = res.getHeaders().getFirst("x-goog-upload-url");
                if (url == null || url.isBlank()) {
                    throw new CustomException(ExceptionMessage.AI_SERVER_RESPONSE_ERROR);
                }
                return url;
            });
    }

    private GeminiFileUploadResponse uploadBytes(String uploadSessionUrl, Path pdfFile,
        long fileSize) {
        return RestClient.create().post()
            .uri(URI.create(uploadSessionUrl))
            .header("X-Goog-Upload-Command", "upload, finalize")
            .header("X-Goog-Upload-Offset", "0")
            .header("Content-Length", String.valueOf(fileSize))
            .body(new FileSystemResource(pdfFile))
            .retrieve()
            .body(GeminiFileUploadResponse.class);
    }

    @Override
    public FileMetadata waitForProcessing(String fileName) throws InterruptedException {
        for (int attempt = 1; attempt <= MAX_POLL_ATTEMPTS; attempt++) {
            FileMetadata metadata = getFile(fileName);
            String state = metadata.state();

            log.debug("파일 상태 폴링 [{}/{}]: name={}, state={}", attempt, MAX_POLL_ATTEMPTS, fileName,
                state);

            if (STATE_ACTIVE.equals(state)) {
                return metadata;
            }
            if (STATE_FAILED.equals(state)) {
                log.error("Gemini 파일 처리 실패: name={}", fileName);
                throw new CustomException(ExceptionMessage.AI_SERVER_RESPONSE_ERROR);
            }
            Thread.sleep(POLL_INTERVAL_MS);
        }

        log.error("파일 처리 타임아웃: name={}, {}ms * {} attempts", fileName, POLL_INTERVAL_MS,
            MAX_POLL_ATTEMPTS);
        throw new CustomException(ExceptionMessage.AI_SERVER_TIMEOUT);
    }

    private FileMetadata getFile(String fileName) {
        return restClient.get()
            .uri("/v1beta/" + fileName + "?key={key}", apiKey)
            .retrieve()
            .body(FileMetadata.class);
    }

    private String extractFileName(String url) {
        int lastSlash = url.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < url.length() - 1) {
            String name = url.substring(lastSlash + 1);
            int queryIdx = name.indexOf('?');
            return queryIdx > 0 ? name.substring(0, queryIdx) : name;
        }
        log.error("파일 이름을 찾을수 없음 url: {}", url);
        throw new IllegalArgumentException();
    }
}
