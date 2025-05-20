package com.icc.qasker.aws.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
public class S3UploadRequest {

    MultipartFile file;
}
