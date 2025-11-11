package com.icc.qasker.aws.service;

import com.icc.qasker.aws.S3ValidateService;
import com.icc.qasker.aws.util.FileUrlValidator;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class S3ValidateServiceImpl implements S3ValidateService {

    FileUrlValidator fileUrlValidator;

    @Override
    public boolean isCloudFrontUrl(String url) {
        return fileUrlValidator.isCloudFrontUrl(url);
    }
}
