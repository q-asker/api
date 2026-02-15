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
     * <ol>
     *   <li>quizCount를 maxChunkCount개의 청크로 라운드 로빈 분배</li>
     *   <li>pageNumbers를 청크 수에 맞게 균등 분할</li>
     * </ol>
     *
     * @param pageNumbers    참조할 페이지 번호 목록 (정렬된 상태)
     * @param totalQuizCount 총 생성할 문제 수
     * @param maxChunkCount  최대 청크 수
     * @return 분할된 청크 목록
     */
    public static List<ChunkInfo> createPageChunks(
        List<Integer> pageNumbers,
        int totalQuizCount,
        int maxChunkCount
    ) {
        int[] quizCounts = distributeQuizCount(totalQuizCount, maxChunkCount);
        int realChunkCount = quizCounts.length;

        int pageCount = pageNumbers.size();
        int basicCountPerChunk = pageCount / realChunkCount;
        int extraPages = pageCount % realChunkCount;

        List<ChunkInfo> chunks = new ArrayList<>(realChunkCount);
        int cursor = 0;

        for (int quizCount : quizCounts) {
            int pagesForThisChunk = basicCountPerChunk;
            if (extraPages > 0) {
                pagesForThisChunk++;
                extraPages--;
            }

            int end = Math.min(cursor + pagesForThisChunk, pageCount);
            List<Integer> referencedPages = pageNumbers.subList(cursor, end);

            chunks.add(new ChunkInfo(List.copyOf(referencedPages), quizCount));
            cursor += pagesForThisChunk;
        }

        return chunks;
    }

    /**
     * totalQuizCount를 maxChunkCount 이하의 청크로 라운드 로빈 분배한다.
     *
     * <p>예시: totalQuizCount=7, maxChunkCount=3
     * → [3, 2, 2] (첫 청크부터 나머지 분배)
     *
     * <p>예시: totalQuizCount=3, maxChunkCount=10
     * → [1, 1, 1] (실제 청크 수 = min(totalQuizCount, maxChunkCount))
     */
    private static int[] distributeQuizCount(int totalQuizCount, int maxChunkCount) {
        int realChunkCount = Math.min(totalQuizCount, maxChunkCount);
        int[] counts = new int[realChunkCount];
        for (int i = 0; i < totalQuizCount; i++) {
            counts[i % realChunkCount]++;
        }
        return counts;
    }
}
