package com.icc.qasker.ai.service.support;

import com.icc.qasker.ai.structure.GeminiQuestion;
import com.icc.qasker.ai.structure.GeminiQuestion.GeminiSelection;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 생성된 퀴즈 문항의 품질을 휴리스틱으로 검증하는 필터. LLM 호출 없이 순수 문자열 처리만 수행하므로 응답 시간에 영향이 없다.
 *
 * <p>검증 항목: 1. 정보 누출: 표/인용문/다이어그램의 텍스트가 정답 선택지에 그대로 포함되는지 2. 주제 중복: 이전 청크에서 이미 출제된 주제와 겹치는지
 */
@Slf4j
@Component
public class QuizQualityFilter {

  // 청크 간 주제 중복 감지를 위한 공유 상태 (세션별로 초기화 필요)
  private final ConcurrentHashMap<String, Set<String>> sessionTopicCache =
      new ConcurrentHashMap<>();

  // 자료(표/인용문/다이어그램) 추출 패턴
  private static final Pattern TABLE_PATTERN =
      Pattern.compile("\\|[^|]+\\|[^|]+\\|", Pattern.MULTILINE);
  private static final Pattern QUOTE_PATTERN = Pattern.compile("> \"([^\"]+)\"");
  private static final Pattern MERMAID_PATTERN =
      Pattern.compile("```mermaid\\n(.+?)```", Pattern.DOTALL);

  // 정보 누출 판단 임계값: 자료 텍스트와 정답 선택지의 단어 겹침 비율
  private static final double INFO_LEAK_THRESHOLD = 0.65;

  // 주제 중복 판단 임계값: 볼드 키워드(핵심 개념) 겹침 비율 (0.80 = 80% 이상 겹치면 중복)
  private static final double TOPIC_OVERLAP_THRESHOLD = 0.80;

  /**
   * 문항 리스트를 검증하여 품질 기준을 통과한 문항만 반환한다.
   *
   * @param questions 검증할 문항 리스트
   * @param sessionId 세션 ID (주제 중복 추적용)
   * @return 품질 기준을 통과한 문항 리스트
   */
  public List<GeminiQuestion> filter(List<GeminiQuestion> questions, String sessionId) {
    if (questions == null || questions.isEmpty()) {
      return questions;
    }

    Set<String> usedTopics =
        sessionTopicCache.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet());

    List<GeminiQuestion> passed = new ArrayList<>();
    int infoLeakFiltered = 0;
    int topicDupFiltered = 0;

    for (GeminiQuestion q : questions) {
      // 검증 1: 정보 누출 체크
      if (hasInfoLeak(q)) {
        infoLeakFiltered++;
        log.info("정보누출 필터링: \"{}\"", truncate(q.content(), 50));
        continue;
      }

      // 검증 2: 주제 중복 체크
      Set<String> keywords = extractKeywords(q.content());
      if (hasTopicOverlap(keywords, usedTopics)) {
        topicDupFiltered++;
        log.info("주제중복 필터링: \"{}\"", truncate(q.content(), 50));
        continue;
      }

      // 통과 → 주제 키워드 등록
      usedTopics.addAll(keywords);
      passed.add(q);
    }

    if (infoLeakFiltered > 0 || topicDupFiltered > 0) {
      log.info(
          "품질 필터 결과: 입력 {}개, 통과 {}개, 정보누출 제거 {}개, 주제중복 제거 {}개",
          questions.size(),
          passed.size(),
          infoLeakFiltered,
          topicDupFiltered);
    }

    return passed;
  }

  /** 세션 종료 시 주제 캐시를 정리한다. */
  public void clearSession(String sessionId) {
    sessionTopicCache.remove(sessionId);
  }

  // ════════════════════════════════════════════════════════════════
  // 검증 1: 정보 누출 감지
  // ════════════════════════════════════════════════════════════════

  /**
   * 질문문의 자료(표/인용문/다이어그램)에서 추출한 텍스트가 정답 선택지에 과도하게 포함되는지 검사한다.
   *
   * @return true면 정보 누출 의심
   */
  private boolean hasInfoLeak(GeminiQuestion q) {
    if (q.content() == null || q.selections() == null) return false;

    // 질문문에서 자료 텍스트 추출
    String resourceText = extractResourceText(q.content());
    if (resourceText.isBlank()) return false;

    Set<String> resourceWords = tokenize(resourceText);
    if (resourceWords.size() < 3) return false;

    // 정답 선택지 찾기
    GeminiSelection correctSelection =
        q.selections().stream().filter(GeminiSelection::correct).findFirst().orElse(null);

    if (correctSelection == null || correctSelection.content() == null) return false;

    Set<String> selectionWords = tokenize(correctSelection.content());
    if (selectionWords.isEmpty()) return false;

    // 자료 텍스트와 정답 선택지의 단어 겹침 비율 계산
    long overlap = selectionWords.stream().filter(resourceWords::contains).count();
    double overlapRatio = (double) overlap / selectionWords.size();

    return overlapRatio >= INFO_LEAK_THRESHOLD;
  }

  /** 질문문에서 표, 인용문, 다이어그램 텍스트를 추출한다. */
  private String extractResourceText(String content) {
    StringBuilder sb = new StringBuilder();

    // 표 추출
    Matcher tableMatcher = TABLE_PATTERN.matcher(content);
    while (tableMatcher.find()) {
      sb.append(tableMatcher.group()).append(" ");
    }

    // 인용문 추출
    Matcher quoteMatcher = QUOTE_PATTERN.matcher(content);
    while (quoteMatcher.find()) {
      sb.append(quoteMatcher.group(1)).append(" ");
    }

    // 다이어그램 텍스트 추출 (노드 라벨)
    Matcher mermaidMatcher = MERMAID_PATTERN.matcher(content);
    while (mermaidMatcher.find()) {
      String mermaidContent = mermaidMatcher.group(1);
      // 노드 라벨 추출: A[라벨], A(라벨), A{라벨} 등
      Pattern labelPattern = Pattern.compile("[\\[({]([^\\])}]+)[\\])}]");
      Matcher labelMatcher = labelPattern.matcher(mermaidContent);
      while (labelMatcher.find()) {
        sb.append(labelMatcher.group(1)).append(" ");
      }
      // 엣지 라벨 추출: -->|라벨|
      Pattern edgePattern = Pattern.compile("\\|([^|]+)\\|");
      Matcher edgeMatcher = edgePattern.matcher(mermaidContent);
      while (edgeMatcher.find()) {
        sb.append(edgeMatcher.group(1)).append(" ");
      }
    }

    return sb.toString();
  }

  // ════════════════════════════════════════════════════════════════
  // 검증 2: 주제 중복 감지
  // ════════════════════════════════════════════════════════════════

  /**
   * 새 문항의 키워드가 이미 출제된 주제와 과도하게 겹치는지 검사한다.
   *
   * @return true면 주제 중복 의심
   */
  private boolean hasTopicOverlap(Set<String> newKeywords, Set<String> usedTopics) {
    if (newKeywords.isEmpty() || usedTopics.isEmpty()) return false;

    long overlap = newKeywords.stream().filter(usedTopics::contains).count();
    double overlapRatio = (double) overlap / newKeywords.size();

    return overlapRatio >= TOPIC_OVERLAP_THRESHOLD;
  }

  // ════════════════════════════════════════════════════════════════
  // 유틸리티
  // ════════════════════════════════════════════════════════════════

  /**
   * 질문문에서 **볼드 처리된 핵심 개념**만 추출한다. 프롬프트에서 "핵심 용어·조건은 굵게 강조하세요"라고 지시했으므로, 볼드 텍스트가 문항의 실제 주제를 나타낸다. 전체
   * 텍스트 대신 볼드 키워드만 비교하면 단일 도메인 문서에서도 주제 중복을 정확하게 감지할 수 있다.
   */
  private Set<String> extractKeywords(String text) {
    if (text == null) return Set.of();

    // **볼드** 텍스트만 추출
    Set<String> keywords = new HashSet<>();
    Pattern boldPattern = Pattern.compile("\\*\\*([^*]+)\\*\\*");
    Matcher matcher = boldPattern.matcher(text);
    while (matcher.find()) {
      String boldText = matcher.group(1).replaceAll("[^가-힣a-zA-Z0-9_ ]", " ").trim();
      // 볼드 내 개별 단어가 아닌 전체 구문을 키워드로 사용 (예: "Consumer Group Rebalancing")
      if (boldText.length() >= 2) {
        keywords.add(boldText.toLowerCase());
      }
    }

    return keywords;
  }

  /** 한국어 불용어 (조사, 접속사, 일반 서술어) */
  private boolean isStopWord(String word) {
    return Set.of(
            "다음", "가장", "타당한", "판단", "무엇", "것은", "대한", "위한", "중에서", "고려할", "적절한", "경우", "있는", "없는",
            "하는", "되는", "이러한", "때문에", "있다", "없다", "한다", "된다", "이다", "것이", "통해", "대해", "설명", "문항",
            "선택", "다음과", "같은", "무엇입니까", "무엇인가", "방안", "이유", "강의노트", "시스템", "설정", "환경", "구성", "방식",
            "전략", "구조", "기반", "활용", "특징", "개념", "메커니즘")
        .contains(word);
  }

  /** 텍스트를 의미 단위 토큰으로 분리한다. */
  private Set<String> tokenize(String text) {
    if (text == null) return Set.of();
    String cleaned = text.replaceAll("[^가-힣a-zA-Z0-9_ ]", " ");
    return Arrays.stream(cleaned.split("\\s+"))
        .filter(w -> w.length() >= 2)
        .collect(Collectors.toSet());
  }

  private String truncate(String text, int maxLen) {
    if (text == null) return "";
    return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
  }
}
