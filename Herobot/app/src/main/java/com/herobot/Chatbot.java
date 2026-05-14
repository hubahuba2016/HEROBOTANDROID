package com.herobot;

import android.content.Context;
import android.database.Cursor;

import android.util.Log;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.net.URL;
import java.net.URLEncoder;
import java.io.InputStreamReader;
import java.util.*;

public class Chatbot {

    private static final String TAG = "HeroBot_Logic";
    private DBHelper db;
    private static boolean isDownloading = false;
    private final ConversationHistory history = new ConversationHistory();

    public Chatbot(Context context) {
        db = new DBHelper(context);
        seedDatabase(context);
    }

    private void seedDatabase(Context context) {
        Cursor cursor = db.getAll();
        if (cursor.getCount() == 0) {
            Log.d(TAG, "Database empty. Seeding with chit-chat data...");
            try {
                InputStream is = context.getResources().openRawResource(R.raw.chitchat);
                importTrainingData(is);
            } catch (Exception e) {
                Log.e(TAG, "Failed to seed database", e);
            }
        }
        cursor.close();
    }

    // =========================
    // MAIN ENTRY (USED BY APP)
    // =========================
    public BotResponse reply(String input) {

        input = input.toLowerCase().trim();
        history.add("User: " + input);

        // 0. Handle context-aware follow-ups
        if (input.equals("why?") || input.equals("how?")) {
            return new BotResponse("I'm basing my knowledge on my training data and the information I find online.", BotResponse.Source.SYSTEM);
        }

        // 1. Exact match from DB
        String dbAnswer = db.getAnswer(input);
        if (dbAnswer != null) {
            BotResponse res = new BotResponse(dbAnswer, BotResponse.Source.DATABASE);
            history.add("HeroBot: " + res.getText());
            return res;
        }

        // 2. Simple built-in responses
        if (input.contains("hello") || input.contains("hi ")) {
            BotResponse res = new BotResponse("Hi! I'm HeroBot.", BotResponse.Source.SYSTEM);
            history.add("HeroBot: " + res.getText());
            return res;
        }

        if (input.contains("who are you")) {
            BotResponse res = new BotResponse("I am HeroBot, your Android assistant.", BotResponse.Source.SYSTEM);
            history.add("HeroBot: " + res.getText());
            return res;
        }

        if (input.contains("help")) {
            BotResponse res = new BotResponse("I can answer trained questions or learn new ones!", BotResponse.Source.SYSTEM);
            history.add("HeroBot: " + res.getText());
            return res;
        }

        // 3. Simple built-in personality
        if (input.contains("favorite color")) {
            return new BotResponse("I like Blue, it reminds me of a clean interface.", BotResponse.Source.SYSTEM);
        }

        // 4. Fallback learning (simple similarity search)
        String smart = findBestMatch(input);
        if (smart != null) {
            BotResponse res = new BotResponse(smart, BotResponse.Source.DATABASE);
            history.add("HeroBot: " + res.getText());
            return res;
        }

        // 5. Internet Search: Wikipedia (High Priority for 'what/who is')
        if (input.matches("^(what is|who is|tell me about|who was|define|search for).*")) {
            String topic = input.replaceAll("^(what is|who is|tell me about|who was|define|search for)", "").trim();
            String wikiReply = fetchFromWikipedia(topic);
            if (wikiReply != null) {
                String cleaned = cleanText(wikiReply);
                String formatted = "I found this on Wikipedia: " + cleaned;
                train(input, cleaned);
                BotResponse res = new BotResponse(formatted, BotResponse.Source.WEB);
                history.add("HeroBot: " + res.getText());
                return res;
            }
        }

        // 6. Internet Search: DuckDuckGo (General fallback)
        String ddgReply = fetchFromWeb(input);
        if (ddgReply != null) {
            String cleaned = cleanText(ddgReply);
            train(input, cleaned); 
            BotResponse res = new BotResponse("According to the web: " + cleaned, BotResponse.Source.WEB);
            history.add("HeroBot: " + res.getText());
            return res;
        }

        // 7. Ollama LLM Fallback (With Context)
        String llmReply = askLocalLLM(input);
        if (llmReply != null) {
            BotResponse res = new BotResponse(llmReply, BotResponse.Source.LLM);
            history.add("HeroBot: " + res.getText());
            return res;
        }

        BotResponse res = new BotResponse("I'm not sure how to answer that. You can teach me by typing 'train: question | answer'", BotResponse.Source.NONE);
        history.add("HeroBot: " + res.getText());
        return res;
    }

    /**
     * Cleans up text by removing Wikipedia-style citations [1], [2], etc.
     */
    private String cleanText(String text) {
        if (text == null) return null;
        // Remove citation brackets like [1], [12], [citation needed]
        String cleaned = text.replaceAll("\\[\\d+\\]|\\[[^\\]]+\\]", "");
        return cleaned.trim();
    }

    // =========================
    // TRAIN BOT
    // =========================
    public void train(String question, String answer) {
        db.insertQA(question, answer);
    }

    public void importTrainingData(InputStream is) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            String question = null;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("Q: ")) {
                    question = line.substring(3).trim();
                } else if (line.startsWith("A: ") && question != null) {
                    String answer = line.substring(3).trim();
                    db.insertQA(question, answer);
                    question = null;
                }
            }
            Log.d(TAG, "Training data seeded successfully.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to import training data", e);
        }
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

        Map<String, Integer> inputVector = NLPHelper.getVector(input);

        for (int i = 0; i < questions.size(); i++) {
            Map<String, Integer> questionVector = NLPHelper.getVector(questions.get(i));
            double score = NLPHelper.cosineSimilarity(inputVector, questionVector);

            if (score > bestScore) {
                bestScore = score;
                bestAnswer = answers.get(i);
            }
        }

        // Using a 0.65 threshold for a balance between accuracy and flexibility
        if (bestScore > 0.65) {
            return bestAnswer;
        }

        return null;
    }

    private String fetchFromWikipedia(String query) {
        HttpURLConnection conn = null;
        try {
            String urlStr = "https://en.wikipedia.org/api/rest_v1/page/summary/" +
                    URLEncoder.encode(query.replace(" ", "_"), StandardCharsets.UTF_8.name());

            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            if (conn.getResponseCode() == 200) {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) {
                        response.append(line);
                    }
                    JSONObject json = new JSONObject(response.toString());
                    return json.optString("extract", null);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Wikipedia Error: " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }

    private String fetchFromWeb(String query) {
        HttpURLConnection conn = null;
        try {
            String urlStr = "https://api.duckduckgo.com/?q=" +
                    URLEncoder.encode(query, StandardCharsets.UTF_8.name()) +
                    "&format=json&no_html=1&skip_disambig=1";

            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            if (conn.getResponseCode() == 200) {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) {
                        response.append(line);
                    }
                    JSONObject json = new JSONObject(response.toString());
                    
                    // Priority 1: AbstractText
                    String abstractText = json.optString("AbstractText", "");
                    if (!abstractText.isEmpty()) return abstractText;

                    // Priority 2: Text from first RelatedTopic
                    if (json.has("RelatedTopics")) {
                        org.json.JSONArray topics = json.getJSONArray("RelatedTopics");
                        if (topics.length() > 0) {
                            JSONObject firstTopic = topics.optJSONObject(0);
                            if (firstTopic != null) {
                                return firstTopic.optString("Text", null);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Web Search Error: " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }

    // =========================
    // LLM FALLBACK
    // =========================
    private String askLocalLLM(String prompt) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL("http://localhost:11434/api/generate");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            // Build context-aware prompt using conversation history
            StringBuilder fullPrompt = new StringBuilder("You are HeroBot, a helpful Android assistant.\n");
            for (String h : history.getHistory()) {
                fullPrompt.append(h).append("\n");
            }
            fullPrompt.append("HeroBot:");

            JSONObject jsonBody = new JSONObject();
            jsonBody.put("model", "phi"); // Or your preferred model
            jsonBody.put("prompt", fullPrompt.toString());
            jsonBody.put("stream", false);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.toString().getBytes(StandardCharsets.UTF_8));
            }

            if (conn.getResponseCode() == 200) {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) {
                        response.append(line);
                    }
                    JSONObject jsonResponse = new JSONObject(response.toString());
                    return jsonResponse.optString("response", "").trim();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "LLM Error: " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }

    /**
     * Simple class to manage conversation context for the LLM.
     */
    private static class ConversationHistory {
        private final List<String> logs = new ArrayList<>();
        public void add(String message) { logs.add(message); if (logs.size() > 10) logs.remove(0); }
        public List<String> getHistory() { return logs; }
    }
}