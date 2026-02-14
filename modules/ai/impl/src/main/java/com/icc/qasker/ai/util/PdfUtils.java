package com.icc.qasker.ai.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PdfUtils {

    public Path downloadToTemp(String pdfUrl) throws IOException {
        Path tempFile = Files.createTempFile("gemini-upload-", ".pdf");

        log.debug("PDF 다운로드 시작: {} -> {}", pdfUrl, tempFile);

        try (
            InputStream in = URI.create(pdfUrl).toURL().openStream()
        ) {
            Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            deleteTempFile(tempFile);
            throw e;
        }

        log.debug("PDF 다운로드 완료: {} bytes", Files.size(tempFile));
        return tempFile;
    }

    public void deleteTempFile(Path tempFile) {
        if (tempFile == null) {
            return;
        }
        try {
            boolean deleted = Files.deleteIfExists(tempFile);
            if (deleted) {
                log.debug("임시 파일 삭제 완료: {}", tempFile);
            }
        } catch (IOException e) {
            log.warn("임시파일 삭제 실패: {} - {}", tempFile, e.getMessage());
        }
    }
}
