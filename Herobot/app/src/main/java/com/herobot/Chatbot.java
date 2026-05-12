package com.herobot;

import android.content.Context;
import android.database.Cursor;

import android.util.Log;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.io.InputStreamReader;
import java.util.*;

public class Chatbot {

    private static final String TAG = "HeroBot_Logic";
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

        // 4. Internet Search & Learning
        String webReply = fetchFromWeb(input);
        if (webReply != null) {
            train(input, webReply); // Persist the knowledge to DB
            return webReply;
        }

        // 5. Ollama LLM Fallback
        String llmReply = askLocalLLM(input);
        if (llmReply != null && !llmReply.contains("error")) {
            return llmReply;
        }

        return "I'm not sure how to answer that, and I couldn't find it online. You can teach me by typing 'train: question | answer'";
    }

    // =========================
    // TRAIN BOT
    // =========================
    public void train(String question, String answer) {
        db.insertQA(question, answer);
    }

    public void importTrainingData(InputStream is) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
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

        Map<String, Integer> inputVector = getVector(input);

        for (int i = 0; i < questions.size(); i++) {
            Map<String, Integer> questionVector = getVector(questions.get(i));
            double score = cosineSimilarity(inputVector, questionVector);

            if (score > bestScore) {
                bestScore = score;
                bestAnswer = answers.get(i);
            }
        }

        // Using a 0.55 threshold for a balance between accuracy and flexibility
        if (bestScore > 0.65) {
            return bestAnswer;
        }

        return null;
    }

    // =========================
    private Map<String, Integer> getVector(String text) {
        Map<String, Integer> vector = new HashMap<>();
        for (String word : tokenize(text)) {
            vector.put(word, vector.getOrDefault(word, 0) + 1);
        }
        return vector;
    }

    private double cosineSimilarity(Map<String, Integer> v1, Map<String, Integer> v2) {
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        Set<String> allWords = new HashSet<>(v1.keySet());
        allWords.addAll(v2.keySet());

        for (String word : allWords) {
            int c1 = v1.getOrDefault(word, 0);
            int c2 = v2.getOrDefault(word, 0);
            dotProduct += (double) c1 * c2;
            norm1 += Math.pow(c1, 2);
            norm2 += Math.pow(c2, 2);
        }

        return (norm1 == 0 || norm2 == 0) ? 0.0 : dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    // =========================
    // TOKENIZER
    // =========================
    private List<String> tokenize(String text) {

        text = text.replaceAll("[^a-zA-Z0-9 ]", "").toLowerCase();

        List<String> words = new ArrayList<>();

        for (String w : text.split("\\s+")) {
            if (!STOPWORDS.contains(w) && !w.isEmpty()) {
                words.add(w);
            }
        }

        return words;
    }

    private String fetchFromWeb(String query) {
        try {
            String urlStr = "https://api.duckduckgo.com/?q=" +
                    URLEncoder.encode(query, "UTF-8") +
                    "&format=json&no_html=1&skip_disambig=1";

            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);

            if (conn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    response.append(line);
                }
                in.close();

                JSONObject json = new JSONObject(response.toString());
                String abstractText = json.optString("AbstractText", "");

                if (!abstractText.isEmpty()) {
                    return abstractText;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Web Search Error: " + e.getMessage());
        }
        return null;
    }

    // =========================
    // LLM FALLBACK
    // =========================
    private String askLocalLLM(String prompt) {
        try {
            // Use 10.0.2.2 for Android Emulator to reach your PC, 
            // or use 'localhost' if running Ollama via Termux on the phone.
            URL url = new URL("http://10.0.2.2:11434/api/generate");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000); // 5 sec timeout

            JSONObject jsonBody = new JSONObject();
            jsonBody.put("model", "phi");
            jsonBody.put("prompt", "You are HeroBot. Be concise. User: " + prompt);
            jsonBody.put("stream", false);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.toString().getBytes());
            }

            if (conn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    response.append(line);
                }
                in.close();

                JSONObject jsonResponse = new JSONObject(response.toString());
                return jsonResponse.getString("response").trim();
            } else {
                return "Ollama is not reachable (Code: " + conn.getResponseCode() + ")";
            }

        } catch (Exception e) {
            Log.e(TAG, "LLM Error: " + e.getMessage());
            return "Local LLM connection error. Make sure Ollama is running.";
        }
    }
}