package com.icc.qasker.aws.util;

import org.jodconverter.core.office.OfficeException;
import org.jodconverter.core.office.OfficeManager;
import org.jodconverter.core.office.OfficeUtils;
import org.jodconverter.local.JodConverter;
import org.jodconverter.local.office.LocalOfficeManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

@Component
public class PPTtoPDFConverter {

    private final String officeHome;
    private final int port;

    // ① @Value로 application.properties 값 주입
    public PPTtoPDFConverter(
            @Value("${app.office.home}") String officeHome,
            @Value("${app.office.port:2002}") int port
    ) {
        this.officeHome = officeHome;
        this.port = port;
    }

    public File convertPPTtoPDF(MultipartFile pptx) throws IOException {
        File inputFile = Files.createTempFile("upload-", ".pptx").toFile();
        pptx.transferTo(inputFile);
        File outputFile = Files.createTempFile("converted-", ".pdf").toFile();

        OfficeManager officeManager = LocalOfficeManager.builder()
                .officeHome(officeHome)
                .portNumbers(port)
                .install()
                .build();
        try {
            officeManager.start();
            // 4) 변환 실행
            JodConverter
                    .convert(inputFile)
                    .to(outputFile)
                    .execute();
        } catch (OfficeException e) {
            throw new IOException("PPT → PDF 변환 실패", e);
        } finally {
            OfficeUtils.stopQuietly(officeManager);
            inputFile.delete();
        }
        // 변환된 PDF 파일 반환 (정리: 컨트롤러에서 삭제)
        return outputFile;
    }
}
