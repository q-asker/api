package com.icc.qasker.ai;

import com.icc.qasker.ai.dto.GeminiFileUploadResponse.FileMetadata;

public interface GeminiFileService {

    FileMetadata uploadPdf(String pdfUrl);

    void deleteFile(String fileName);

    FileMetadata waitForProcessing(String fileName) throws InterruptedException;
}
