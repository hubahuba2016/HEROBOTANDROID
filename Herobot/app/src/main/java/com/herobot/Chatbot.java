package com.herobot;

import android.content.Context;
import android.database.Cursor;

import java.util.*;

public class Chatbot {

    private DBHelper db;

    private static final Set<String> STOPWORDS = Set.of(
            "what","is","the","a","an","are","you","can","to","of","and",
            "in","on","at","for","with","who","why","how","when","where"
    );

    public Chatbot(Context context) {
        db = new DBHelper(context);
    }

    // =========================
    // MAIN ENTRY (USED BY APP)
    // =========================
    public String reply(String input) {

        input = input.toLowerCase().trim();

        // 1. Exact match from DB
        String dbAnswer = db.getAnswer(input);
        if (dbAnswer != null) {
            return dbAnswer;
        }

        // 2. Simple built-in responses
        if (input.contains("hello")) {
            return "Hi! I'm HeroBot.";
        }

        if (input.contains("who are you")) {
            return "I am HeroBot, your Android assistant.";
        }

        if (input.contains("help")) {
            return "I can answer trained questions or learn new ones!";
        }

        // 3. Fallback learning (simple similarity search)
        String smart = findBestMatch(input);
        if (smart != null) {
            return smart;
        }

        return "I don't understand yet. You can train me!";
    }

    // =========================
    // TRAIN BOT
    // =========================
    public void train(String question, String answer) {
        db.insertQA(question, answer);
    }

    // =========================
    // SIMPLE NLP MATCH (NO LIBRARIES)
    // =========================
    private String findBestMatch(String input) {

        Cursor cursor = db.getAll();

        List<String> questions = new ArrayList<>();
        List<String> answers = new ArrayList<>();

        while (cursor.moveToNext()) {
            questions.add(cursor.getString(0));
            answers.add(cursor.getString(1));
        }
        cursor.close();

        if (questions.isEmpty()) return null;

        String bestAnswer = null;
        double bestScore = 0;

        for (int i = 0; i < questions.size(); i++) {

            double score = similarity(input, questions.get(i));

            if (score > bestScore) {
                bestScore = score;
                bestAnswer = answers.get(i);
            }
        }

        if (bestScore > 0.5) {
            return bestAnswer;
        }

        return null;
    }

    // =========================
    // VERY LIGHT WEIGHT SIMILARITY
    // =========================
    private double similarity(String a, String b) {

        Set<String> aWords = tokenize(a);
        Set<String> bWords = tokenize(b);

        if (aWords.isEmpty() || bWords.isEmpty()) return 0;

        int match = 0;

        for (String w : aWords) {
            if (bWords.contains(w)) {
                match++;
            }
        }

        return (double) match / Math.max(aWords.size(), bWords.size());
    }

    // =========================
    // TOKENIZER
    // =========================
    private Set<String> tokenize(String text) {

        text = text.replaceAll("[^a-zA-Z0-9 ]", "").toLowerCase();

        Set<String> words = new HashSet<>();

        for (String w : text.split("\\s+")) {
            if (!STOPWORDS.contains(w) && !w.isEmpty()) {
                words.add(w);
            }
        }

        return words;
    }
}