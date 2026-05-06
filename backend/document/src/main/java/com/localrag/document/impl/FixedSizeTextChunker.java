package com.localrag.document.impl;

import com.hankcs.hanlp.HanLP;
import com.localrag.document.contract.TextChunker;
import com.localrag.document.model.Chunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class FixedSizeTextChunker implements TextChunker {

    @Override
    public List<Chunk> chunk(String text, int chunkSize, int unused, double overlapPct) {
        List<Chunk> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) return chunks;

        String[] paragraphs = text.split("\n\n");
        List<String> raw = new ArrayList<>();

        // 步骤1: 段落 → 句子 → HanLP 三级切分，每块 ≤ chunkSize
        for (String para : paragraphs) {
            String cleaned = para.trim();
            if (cleaned.isEmpty()) continue;

            if (cleaned.length() <= chunkSize) {
                raw.add(cleaned);
            } else {
                raw.addAll(splitBySentence(cleaned, chunkSize));
            }
        }

        // 步骤2: 合并过小块，输出最终块
        StringBuilder buf = new StringBuilder();
        int idx = 0;
        for (String block : raw) {
            if (buf.length() + block.length() > chunkSize && buf.length() > 0) {
                chunks.add(makeChunk(idx++, buf.toString().trim()));
                buf.setLength(0);
            }
            buf.append(block);
            if (buf.length() > 0 && !block.endsWith("。") && !block.endsWith("！")
                    && !block.endsWith("？") && !block.endsWith("；")) {
                buf.append(" ");
            }
        }
        if (buf.length() > 0) {
            chunks.add(makeChunk(idx, buf.toString().trim()));
        }

        return addOverlap(chunks);
    }

    /**
     * 三级切分：句子 → HanLP 分词，保证每块 ≤ chunkSize
     */
    private List<String> splitBySentence(String text, int chunkSize) {
        List<String> result = new ArrayList<>();
        String[] sentences = text.split("(?<=[。！？；])");

        for (String sent : sentences) {
            String s = sent.trim();
            if (s.isEmpty()) continue;
            if (s.length() <= chunkSize) {
                result.add(s);
            } else {
                result.addAll(hanlpSplit(s, chunkSize));
            }
        }
        return result;
    }

    /**
     * HanLP 分词后按 chunkSize 切分（超长句子保底）
     */
    private List<String> hanlpSplit(String text, int chunkSize) {
        List<String> result = new ArrayList<>();
        List<String> tokens = HanLP.segment(text).stream()
                .map(t -> t.word)
                .toList();

        StringBuilder buf = new StringBuilder();
        for (String token : tokens) {
            if (buf.length() + token.length() > chunkSize && buf.length() > 0) {
                result.add(buf.toString().trim());
                buf.setLength(0);
            }
            buf.append(token);
        }
        if (buf.length() > 0) result.add(buf.toString().trim());
        return result;
    }

    /**
     * 相邻块之间加 15% 重叠（尾部内容拼到下一块开头）
     */
    private List<Chunk> addOverlap(List<Chunk> chunks) {
        for (int i = 1; i < chunks.size(); i++) {
            Chunk prev = chunks.get(i - 1);
            int tailLen = Math.min((int) (prev.getText().length() * 0.15), 100);
            if (tailLen > 0) {
                String tail = prev.getText().substring(prev.getText().length() - tailLen);
                Chunk cur = chunks.get(i);
                cur.setText(tail + cur.getText());
                cur.setCharCount(cur.getText().length());
            }
        }
        return chunks;
    }

    private Chunk makeChunk(int idx, String text) {
        return Chunk.builder()
                .chunkId("chunk_" + idx)
                .index(idx)
                .text(text)
                .charCount(text.length())
                .build();
    }
}
