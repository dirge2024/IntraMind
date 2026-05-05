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

    private static final int MAX_OVERLAP = 512;

    @Override
    public List<Chunk> chunk(String text, int bufferSize, int minChunk, double overlapPct) {
        List<Chunk> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return chunks;
        }

        int idx = 0;
        int pos = 0;
        String prevTail = "";

        while (pos < text.length()) {
            int end = Math.min(pos + bufferSize, text.length());
            String segment = prevTail + text.substring(pos, end);

            List<String> blocks = splitSegment(segment, minChunk);

            for (int i = 0; i < blocks.size(); i++) {
                String block = blocks.get(i);
                if (block.isEmpty()) continue;

                if (i > 0 || !prevTail.isEmpty()) {
                    chunks.add(Chunk.builder()
                            .chunkId("chunk_" + idx)
                            .index(idx)
                            .text(block)
                            .charCount(block.length())
                            .build());
                    idx++;
                }
            }

            if (!blocks.isEmpty() && end < text.length()) {
                String lastBlock = blocks.get(blocks.size() - 1);
                int overlapLen = (int) (lastBlock.length() * overlapPct);
                if (overlapLen > MAX_OVERLAP) overlapLen = MAX_OVERLAP;
                prevTail = lastBlock.substring(Math.max(0, lastBlock.length() - overlapLen));
            } else {
                prevTail = "";
            }

            pos = end;
        }

        if (chunks.isEmpty() && !text.isEmpty()) {
            chunks.add(Chunk.builder()
                    .chunkId("chunk_0")
                    .index(0)
                    .text(text)
                    .charCount(text.length())
                    .build());
        }

        return chunks;
    }

    private List<String> splitSegment(String segment, int minChunk) {
        List<String> result = new ArrayList<>();
        String[] paragraphs = segment.split("\n\n", -1);

        StringBuilder buffer = new StringBuilder();
        for (String para : paragraphs) {
            String cleaned = para.trim();
            if (cleaned.isEmpty()) continue;

            if (cleaned.length() < minChunk) {
                buffer.append(cleaned).append("\n\n");
            } else {
                if (buffer.length() >= minChunk) {
                    result.add(buffer.toString().trim());
                    buffer.setLength(0);
                } else if (buffer.length() > 0) {
                    String merged = buffer.toString() + cleaned;
                    if (merged.length() <= minChunk * 3) {
                        result.add(merged.trim());
                        buffer.setLength(0);
                        continue;
                    }
                    result.add(buffer.toString().trim());
                    buffer.setLength(0);
                }

                List<String> sentences = splitSentences(cleaned);
                for (String sent : sentences) {
                    if (sent.length() < minChunk) {
                        buffer.append(sent).append(" ");
                    } else if (sent.length() >= minChunk && sent.length() < 2000) {
                        if (buffer.length() > 0) {
                            buffer.append(sent);
                            result.add(buffer.toString().trim());
                            buffer.setLength(0);
                        } else {
                            result.add(sent);
                        }
                    } else {
                        if (buffer.length() > 0) {
                            result.add(buffer.toString().trim());
                            buffer.setLength(0);
                        }
                        result.addAll(hanlpSplit(sent, minChunk));
                    }
                }
            }
        }

        if (buffer.length() >= minChunk) {
            result.add(buffer.toString().trim());
        } else if (buffer.length() > 0 && !result.isEmpty()) {
            int lastIdx = result.size() - 1;
            result.set(lastIdx, result.get(lastIdx) + "\n\n" + buffer.toString().trim());
        } else if (buffer.length() > 0) {
            result.add(buffer.toString().trim());
        }

        return result;
    }

    private List<String> splitSentences(String text) {
        List<String> sentences = new ArrayList<>();
        String[] parts = text.split("(?<=[。！？；\\n])");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                sentences.add(trimmed);
            }
        }
        return sentences.isEmpty() ? List.of(text) : sentences;
    }

    private List<String> hanlpSplit(String text, int chunkSize) {
        List<String> result = new ArrayList<>();
        List<String> tokens = HanLP.segment(text).stream()
                .map(t -> t.word)
                .toList();

        StringBuilder buffer = new StringBuilder();
        for (String token : tokens) {
            if (buffer.length() + token.length() > chunkSize && buffer.length() > 0) {
                result.add(buffer.toString().trim());
                buffer.setLength(0);
            }
            buffer.append(token);
        }
        if (buffer.length() > 0) {
            result.add(buffer.toString().trim());
        }
        return result;
    }
}
