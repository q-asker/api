package com.icc.qasker.admin.properties;

import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 관리자 이미지 업로드 검증 설정.
 *
 * <p>문서 업로드용 {@code q-asker.file-validation}(PDF/PPT/DOCX·50MB)과 규칙이 다르므로 별도 프리픽스로 분리한다.
 *
 * @param maxSize 허용 최대 크기(바이트)
 * @param allowedTypes 허용 contentType 집합
 */
@ConfigurationProperties(prefix = "q-asker.admin.image-upload")
public record ImageUploadProperties(long maxSize, Set<String> allowedTypes) {}
