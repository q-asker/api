package com.icc.qasker.ai.util;

import com.icc.qasker.ai.dto.ChunkInfo;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ChunkSplitter {

  /**
   * 페이지 목록과 퀴즈 수를 받아 청크로 분할한다.
   *
   * <p>알고리즘:
   *
   * <ol>
   *   <li>퀴즈 수를 라운드 로빈으로 분배
   *   <li>pageNumbers를 청크 수에 맞게 균등 분할
   * </ol>
   *
   * @param pageNumbers 참조할 페이지 번호 목록 (정렬된 상태)
   * @param totalQuizCount 총 생성할 문제 수
   * @param maxChunkCount 최대 청크 수
   * @return 분할된 청크 목록
   */
  public static List<ChunkInfo> createPageChunks(
      List<Integer> pageNumbers, int totalQuizCount, int maxChunkCount) {

    // 진짜 청크 카운트는 전체 퀴즈 수, 선택된 청크 수 중 작은 값
    int realChunkCount = Math.min(totalQuizCount, maxChunkCount);

    int[] distributedQuizCounts = distributeQuizCounts(totalQuizCount, realChunkCount);
    List<List<Integer>> distributedPages = distributePages(pageNumbers, realChunkCount);

    List<ChunkInfo> chunkInfos = new ArrayList<>(realChunkCount);
    for (int i = 0; i < realChunkCount; i++) {
      chunkInfos.add(new ChunkInfo(distributedPages.get(i), distributedQuizCounts[i]));
    }

    return chunkInfos;
  }

  /**
   * 퀴즈 수를 라운드 로빈으로 균등 분배한다. 첫 번째 청크는 1문제 고정(Fast First). 나머지 문제는 counts[1]부터 순환하며 분배한다.
   *
   * <p>시나리오 1: totalQuizCount=10, chunkCount=3 counts[0]=1 (고정), 나머지 9문제를 counts[1]~[2] 라운드 로빈 →
   * [1, 5, 4] (합=10)
   *
   * <p>시나리오 2: totalQuizCount=5, chunkCount=5 counts[0]=1 (고정), 나머지 4문제를 counts[1]~[4] 라운드 로빈 → [1,
   * 1, 1, 1, 1] (합=5)
   *
   * <p>시나리오 3: totalQuizCount=10, chunkCount=5 counts[0]=1 (고정), 나머지 9문제를 counts[1]~[4] 라운드 로빈 →
   * [1, 3, 2, 2, 2] (합=10)
   */
  static int[] distributeQuizCounts(int totalQuizCount, int chunkCount) {
    int[] counts = new int[chunkCount];
    if (chunkCount == 1) {
      counts[0] = totalQuizCount;
      return counts;
    }
    counts[0] = 1;
    for (int i = 0; i < totalQuizCount - 1; i++) {
      counts[i % (chunkCount - 1) + 1]++;
    }
    return counts;
  }

  /**
   * 페이지 번호를 청크 수에 맞게 균등 분할한다. 페이지가 부족하면 순환하여 최소 1페이지를 보장한다.
   *
   * <p>시나리오 1: 페이지 >= 청크 (균등 분배) pages=[1,2,3,4,5], chunkCount=3 → baseSize=1, remainder=2 i=0:
   * pageCountForChunk=2, [1,2] i=1: pageCountForChunk=2, [3,4] i=2: pageCountForChunk=1, [5]
   *
   * <p>시나리오 2: 페이지 < 청크 (순환 할당) pages=[28,29,30], chunkCount=5 → baseSize=0, remainder=3 i=0:
   * pageCountForChunk=1, startIndex=0%3=0, [28] i=1: pageCountForChunk=1, startIndex=1%3=1, [29]
   * i=2: pageCountForChunk=1, startIndex=2%3=2, [30] i=3: pageCountForChunk=1, startIndex=3%3=0,
   * [28] ← 순환 i=4: pageCountForChunk=1, startIndex=1%3=1, [29] ← 순환
   */
  private static List<List<Integer>> distributePages(List<Integer> pageNumbers, int chunkCount) {
    int pageCount = pageNumbers.size();
    int baseSize = pageCount / chunkCount;
    // 다 N빵하고 남은 페이지
    int remainder = pageCount % chunkCount;

    List<List<Integer>> result = new ArrayList<>(chunkCount);
    int startIndex = 0;

    for (int i = 0; i < chunkCount; i++) {
      int pageCountForChunk = Math.max(1, baseSize + (i < remainder ? 1 : 0));
      startIndex = startIndex % pageCount;

      int endIndex = Math.min(startIndex + pageCountForChunk, pageCount);
      result.add(List.copyOf(pageNumbers.subList(startIndex, endIndex)));
      startIndex = endIndex;
    }

    return result;
  }
}
