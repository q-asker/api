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
   *   <li>첫 번째 청크는 1문제로 고정 (Fast First Chunk), 나머지를 라운드 로빈 분배
   *   <li>pageNumbers를 청크 수에 맞게 균등 분할
   *   <li>첫 번째 청크만 fastFirst=true, 나머지는 false
   * </ol>
   *
   * @param pageNumbers 참조할 페이지 번호 목록 (정렬된 상태)
   * @param totalQuizCount 총 생성할 문제 수
   * @param maxChunkCount 최대 청크 수
   * @return 분할된 청크 목록
   */
  public static List<ChunkInfo> createPageChunks(
      List<Integer> pageNumbers, int totalQuizCount, int maxChunkCount) {
    int[] quizCounts = distributeQuizCount(totalQuizCount, maxChunkCount);
    int realChunkCount = quizCounts.length;
    boolean applyFastFirst = realChunkCount >= 2;

    int pageCount = pageNumbers.size();
    int basicCountPerChunk = pageCount / realChunkCount;
    int extraPages = pageCount % realChunkCount;

    List<ChunkInfo> chunks = new ArrayList<>(realChunkCount);
    int cursor = 0;

    for (int i = 0; i < realChunkCount; i++) {
      int pagesForThisChunk = basicCountPerChunk;
      if (extraPages > 0) {
        pagesForThisChunk++;
        extraPages--;
      }

      int end = Math.min(cursor + pagesForThisChunk, pageCount);
      List<Integer> referencedPages = pageNumbers.subList(cursor, end);

      boolean fastFirst = applyFastFirst && i == 0;
      chunks.add(new ChunkInfo(List.copyOf(referencedPages), quizCounts[i], fastFirst));
      cursor += pagesForThisChunk;
    }

    return chunks;
  }

  /**
   * totalQuizCount를 Fast First Chunk 전략으로 분배한다.
   *
   * <p>첫 번째 청크는 1문제로 고정하고, 나머지를 라운드 로빈으로 분배한다.
   *
   * <p>예시: totalQuizCount=15, maxChunkCount=10 → [1, 2,2,2,2,2,1,1,1,1]
   *
   * <p>예시: totalQuizCount=1, maxChunkCount=10 → [1] (Fast First 미적용)
   */
  static int[] distributeQuizCount(int totalQuizCount, int maxChunkCount) {
    if (totalQuizCount <= 1) {
      return new int[] {totalQuizCount};
    }

    int remaining = totalQuizCount - 1;
    int remainingChunkCount = Math.min(remaining, maxChunkCount - 1);
    int[] counts = new int[1 + remainingChunkCount];
    counts[0] = 1;

    for (int i = 0; i < remaining; i++) {
      counts[1 + (i % remainingChunkCount)]++;
    }

    return counts;
  }
}
