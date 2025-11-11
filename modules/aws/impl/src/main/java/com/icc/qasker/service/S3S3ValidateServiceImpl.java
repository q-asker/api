package com.icc.qasker.service;

import com.icc.qasker.S3ValidateService;
import com.icc.qasker.util.FileUrlValidator;
import org.springframework.stereotype.Service;

@Service
public class S3S3ValidateServiceImpl implements S3ValidateService {

    FileUrlValidator fileUrlValidator;

    @Override
    public boolean isCloudFrontUrl(String url) {
        return fileUrlValidator.isCloudFrontUrl(url);
    }
}
