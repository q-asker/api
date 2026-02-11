package com.icc.qasker.ai.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class PdfUtils {

    private final RestClient restClient = RestClient.create();

    /**
     * 1. 원본 PDF 다운로드 (임시 파일 생성)
     *
     * @return 저장된 임시 파일의 Path
     */
    public Path downloadToTemp(String fileUrl) {
        log.info("PDF 원본 다운로드 시작: {}", fileUrl);
        try {
            Path tempPath = Files.createTempFile("origin__" + UUID.randomUUID(), ".pdf");

            try (InputStream is = restClient.get()
                .uri(URI.create(fileUrl))
                .retrieve()
                .body(InputStream.class)) {

                Files.copy(is, tempPath, StandardCopyOption.REPLACE_EXISTING);
            }

            return tempPath;
        } catch (IOException e) {
            throw new RuntimeException("PDF 파일 처리 중 오류 발생", e);
        }
    }

    /**
     * 2. 임시 파일 삭제 처리가 끝난 후 임시 파일을 정리합니다.
     */
    public void deleteTempFile(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.delete(path);
            log.debug("임시 파일 삭제 완료: {}", path);
        } catch (IOException e) {
            log.warn("임시 파일 삭제 실패 (무시됨): {}", path, e);
        }
    }
}
