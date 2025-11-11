package com.icc.qasker.util;

import com.icc.qasker.error.ExceptionMessage;
import com.icc.qasker.global.error.CustomException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import lombok.extern.slf4j.Slf4j;
import org.jodconverter.core.office.OfficeException;
import org.jodconverter.core.office.OfficeManager;
import org.jodconverter.core.office.OfficeUtils;
import org.jodconverter.local.JodConverter;
import org.jodconverter.local.office.LocalOfficeManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Component
public class ToPDFConverter {

    private final String officeHome;
    private final int port;

    public ToPDFConverter(
        @Value("${app.office.home}") String officeHome,
        @Value("${app.office.port}") int port
    ) {
        this.officeHome = officeHome;
        this.port = port;
    }

    public File convert(MultipartFile target) {
        OfficeManager officeManager = null;
        File inputFile = null;

        try {
            inputFile = Files.createTempFile("upload-", "." + getExtension(target)).toFile();
            target.transferTo(inputFile);
            File outputFile = Files.createTempFile("converted-", ".pdf").toFile();
            officeManager = LocalOfficeManager.builder()
                .officeHome(officeHome)
                .portNumbers(port)
                .install()
                .build();
            officeManager.start();
            JodConverter
                .convert(inputFile)
                .to(outputFile)
                .execute();

            return outputFile;
        } catch (OfficeException e) {
            log.error("Error during office conversion: {}", e.getMessage());
            throw new CustomException(ExceptionMessage.NO_FILE_UPLOADED);
        } catch (IOException e) {
            log.error("Error during file handling: {}", e.getMessage());
            throw new CustomException(ExceptionMessage.NO_FILE_UPLOADED);
        } finally {
            OfficeUtils.stopQuietly(officeManager);
            if (inputFile != null && !inputFile.delete()) {
                log.warn("Failed to delete temporary input file: {}", inputFile.getAbsolutePath());
            }
        }
    }

    private String getExtension(MultipartFile multipartFile) {
        String originalFilename = multipartFile.getOriginalFilename();
        return StringUtils.getFilenameExtension(originalFilename);
    }
}
