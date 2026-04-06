package com.ming.immessageservice.sensitive;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 基于 Trie 的敏感词匹配引擎。
 */
public class SensitiveWordEngine {

    private final TrieNode root;

    public SensitiveWordEngine(List<String> words) {
        this.root = new TrieNode();
        if (words == null) {
            return;
        }
        for (String word : words) {
            insert(word);
        }
    }

    public EngineResult filter(String text) {
        if (text == null || text.isEmpty()) {
            return new EngineResult(false, null, text);
        }
        char[] replaced = text.toCharArray();
        String normalized = normalizeText(text);
        String matchedWord = null;
        boolean hit = false;
        for (int i = 0; i < normalized.length(); i++) {
            TrieNode current = root;
            int lastMatchEnd = -1;
            for (int j = i; j < normalized.length(); j++) {
                current = current.children.get(normalized.charAt(j));
                if (current == null) {
                    break;
                }
                if (current.wordEnd) {
                    lastMatchEnd = j;
                }
            }
            if (lastMatchEnd >= i) {
                if (matchedWord == null) {
                    matchedWord = text.substring(i, lastMatchEnd + 1);
                }
                hit = true;
                for (int k = i; k <= lastMatchEnd; k++) {
                    replaced[k] = '*';
                }
                i = lastMatchEnd;
            }
        }
        return new EngineResult(hit, matchedWord, hit ? new String(replaced) : text);
    }

    private void insert(String word) {
        String normalized = normalizeWord(word);
        if (normalized.isEmpty()) {
            return;
        }
        TrieNode current = root;
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            current = current.children.computeIfAbsent(c, key -> new TrieNode());
        }
        current.wordEnd = true;
    }

    private String normalizeText(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }

    private String normalizeWord(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }

    private static class TrieNode {
        private final Map<Character, TrieNode> children = new HashMap<>();
        private boolean wordEnd;
    }

    public static class EngineResult {
        private final boolean hit;
        private final String matchedWord;
        private final String outputText;

        public EngineResult(boolean hit, String matchedWord, String outputText) {
            this.hit = hit;
            this.matchedWord = matchedWord;
            this.outputText = outputText;
        }

        public boolean isHit() {
            return hit;
        }

        public String getMatchedWord() {
            return matchedWord;
        }

        public String getOutputText() {
            return outputText;
        }
    }
}
