package com.herobot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Core library for text processing and similarity calculation.
 */
public class NLPHelper {

    private static final Set<String> STOPWORDS = Set.of(
            "what","is","the","a","an","are","you","can","to","of","and",
            "in","on","at","for","with","who","why","how","when","where"
    );

    public static List<String> tokenize(String text) {
        if (text == null) return new ArrayList<>();
        text = text.replaceAll("[^a-zA-Z0-9 ]", "").toLowerCase();

        List<String> words = new ArrayList<>();
        for (String w : text.split("\\s+")) {
            if (!STOPWORDS.contains(w) && !w.isEmpty()) {
                words.add(w);
            }
        }
        return words;
    }

    public static Map<String, Integer> getVector(String text) {
        Map<String, Integer> vector = new HashMap<>();
        for (String word : tokenize(text)) {
            vector.put(word, vector.getOrDefault(word, 0) + 1);
        }
        return vector;
    }

    public static double cosineSimilarity(Map<String, Integer> v1, Map<String, Integer> v2) {
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (Map.Entry<String, Integer> entry : v1.entrySet()) {
            int c1 = entry.getValue();
            norm1 += Math.pow(c1, 2);
            Integer c2 = v2.get(entry.getKey());
            if (c2 != null) dotProduct += (double) c1 * c2;
        }

        for (int c2 : v2.values()) norm2 += Math.pow(c2, 2);
        return (norm1 == 0 || norm2 == 0) ? 0.0 : dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
}