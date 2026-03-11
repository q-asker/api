// package com.icc.qasker.aws;
//
// import com.amazonaws.services.lambda.runtime.Context;
// import com.amazonaws.services.lambda.runtime.RequestHandler;
// import java.io.File;
// import java.io.IOException;
// import java.nio.file.Path;
// import java.util.List;
// import java.util.Map;
// import software.amazon.awssdk.core.sync.RequestBody;
// import software.amazon.awssdk.services.s3.S3Client;
// import software.amazon.awssdk.services.s3.model.GetObjectRequest;
// import software.amazon.awssdk.services.s3.model.PutObjectRequest;
//
// public class ToPDFConverter implements RequestHandler<Map<String, Object>, String> {
//
//  // 람다에서 유일하게 쓰기가 허용된 경로
//  private static final String TEMP_DIR = "/tmp/";
//  private final S3Client s3Client = S3Client.create();
//
//  @Override
//  public String handleRequest(Map<String, Object> event, Context context) {
//    System.out.println("Processing event: " + event);
//
//    try {
//      // 1. S3 이벤트 파싱 (Records -> [0] -> s3 -> bucket/object)
//      // 이벤트 구조: { "Records": [ { "s3": { "bucket": { "name": "..." }, "object": { "key": "..." }
// } } ] }
//      List<Map<String, Object>> records = (List<Map<String, Object>>) event.get("Records");
//
//      if (records == null || records.isEmpty()) {
//        System.out.println("Error: No 'Records' found in event.");
//        return "Error: Event does not contain Records";
//      }
//
//      Map<String, Object> firstRecord = records.get(0);
//      Map<String, Object> s3 = (Map<String, Object>) firstRecord.get("s3");
//      Map<String, Object> bucketMap = (Map<String, Object>) s3.get("bucket");
//      Map<String, Object> objectMap = (Map<String, Object>) s3.get("object");
//
//      String bucket = (String) bucketMap.get("name");
//      String key = (String) objectMap.get("key");
//
//      if (bucket == null || key == null) {
//        System.out.println("Error: Bucket or Key is missing in S3 event.");
//        return "Error: Bucket or Key is missing";
//      }
//
//      // URL 디코딩 (공백이 +로 들어오거나 한글이 인코딩된 경우 처리)
//      key = java.net.URLDecoder.decode(key, java.nio.charset.StandardCharsets.UTF_8);
//
//      System.out.println("Bucket: " + bucket);
//      System.out.println("Key: " + key);
//
//      // 1. 이미 PDF 파일이면 변환하지 않고 종료 (가장 중요)
//      if (key.toLowerCase().endsWith(".pdf")) {
//        System.out.println(
//          "Skipping: File is already PDF. Stopping to prevent infinite loop.");
//        return "Skipped: Already PDF";
//      }
//
//      // 파일명만 추출 (예: file.docx)
//      String fileName = new File(key).getName();
//      String downloadPath = TEMP_DIR + fileName;
//      String uploadKey = fileName.substring(0, fileName.lastIndexOf(".")) + ".pdf";
//
//      // 2. S3에서 파일 다운로드 (/tmp 폴더로)
//      System.out.println("Downloading from S3: " + key);
//      s3Client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build(),
//        Path.of(downloadPath));
//
//      // 3. 리브레오피스로 PDF 변환 실행
//      System.out.println("Converting to PDF...");
//      convertToPdf(downloadPath);
//
//      // 4. 변환된 PDF를 S3에 업로드
//      File pdfFile = new File(TEMP_DIR + uploadKey);
//      if (!pdfFile.exists()) {
//        throw new RuntimeException("Conversion failed: Output PDF not found.");
//      }
//
//      System.out.println("Uploading to S3: " + uploadKey);
//      s3Client.putObject(PutObjectRequest.builder().bucket(bucket).key(uploadKey).build(),
//        RequestBody.fromFile(pdfFile));
//
//      return "Success: " + uploadKey;
//    } catch (Exception e) {
//      System.err.println("Error processing request: " + e.getMessage());
//      e.printStackTrace();
//      return "Failed: " + e.getMessage();
//    }
//  }
//
//  // 리눅스 명령어를 자바에서 실행하는 메서드
//  private void convertToPdf(String inputPath) throws IOException, InterruptedException {
//    ProcessBuilder pb = new ProcessBuilder(
//      "soffice",
//      "--headless",
//      "--invisible",
//      "--nodefault",
//      "--view",
//      "--nolockcheck",
//      "--nologo",
//      "--norestore",
//      "--convert-to", "pdf",
//      "--outdir", TEMP_DIR,
//      inputPath
//    );
//
//    // ★중요★: 리브레오피스는 홈 디렉토리에 설정을 쓰려 하므로, 홈을 /tmp로 속여야 함
//    Map<String, String> env = pb.environment();
//    env.put("HOME", TEMP_DIR);
//
//    // 명령어 실행
//    Process process = pb.start();
//    int exitCode = process.waitFor();
//
//    if (exitCode != 0) {
//      throw new RuntimeException("LibreOffice exited with code: " + exitCode);
//    }
//  }
// }
