package com.ming.imchatserver.sensitive;

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

    public boolean contains(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = normalize(text);
        for (int i = 0; i < normalized.length(); i++) {
            TrieNode current = root;
            for (int j = i; j < normalized.length(); j++) {
                current = current.children.get(normalized.charAt(j));
                if (current == null) {
                    break;
                }
                if (current.wordEnd) {
                    return true;
                }
            }
        }
        return false;
    }

    public String replace(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        char[] replaced = text.toCharArray();
        String normalized = normalize(text);
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
                for (int k = i; k <= lastMatchEnd; k++) {
                    replaced[k] = '*';
                }
                i = lastMatchEnd;
            }
        }
        return new String(replaced);
    }

    private void insert(String word) {
        String normalized = normalize(word);
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

    private String normalize(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }

    private static class TrieNode {
        private final Map<Character, TrieNode> children = new HashMap<>();
        private boolean wordEnd;
    }
}
