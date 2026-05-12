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

    public Chatbot(Context context) {
        db = new DBHelper(context);
    }

    // =========================
    // MAIN ENTRY (USED BY APP)
    // =========================
    public BotResponse reply(String input) {

        input = input.toLowerCase().trim();

        // 1. Exact match from DB
        String dbAnswer = db.getAnswer(input);
        if (dbAnswer != null) {
            return new BotResponse(dbAnswer, BotResponse.Source.DATABASE);
        }

        // 2. Simple built-in responses
        if (input.contains("hello")) {
            return new BotResponse("Hi! I'm HeroBot.", BotResponse.Source.SYSTEM);
        }

        if (input.contains("who are you")) {
            return new BotResponse("I am HeroBot, your Android assistant.", BotResponse.Source.SYSTEM);
        }

        if (input.contains("help")) {
            return new BotResponse("I can answer trained questions or learn new ones!", BotResponse.Source.SYSTEM);
        }

        // 3. Fallback learning (simple similarity search)
        String smart = findBestMatch(input);
        if (smart != null) {
            return new BotResponse(smart, BotResponse.Source.DATABASE);
        }

        // 4. Internet Search & Learning
        String webReply = fetchFromWeb(input);
        if (webReply != null) {
            train(input, webReply); // Persist the knowledge to DB
            return new BotResponse(webReply, BotResponse.Source.WEB);
        }

        // 5. Ollama LLM Fallback
        String llmReply = askLocalLLM(input);
        if (llmReply != null) {
            return new BotResponse(llmReply, BotResponse.Source.LLM);
        }

        return new BotResponse("I'm not sure how to answer that. You can teach me by typing 'train: question | answer'", BotResponse.Source.NONE);
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
                    String abstractText = json.optString("AbstractText", "");

                    if (!abstractText.isEmpty()) {
                        return abstractText;
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
        String modelName = "phi";
        try {
            // 10.0.2.2 is the special alias for the host loopback interface in the Android Emulator.
            // If you are running on a physical device, 'localhost' is used for Termux-hosted Ollama.
            String host = android.os.Build.FINGERPRINT.contains("generic") ? "10.0.2.2" : "localhost";
            
            URL url = new URL("http://" + host + ":11434/api/generate");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000); 
            conn.setReadTimeout(30000); // LLMs take time to generate, 30s is safer

            JSONObject jsonBody = new JSONObject();
            jsonBody.put("model", modelName);
            jsonBody.put("prompt", "You are HeroBot. Be concise. User: " + prompt);
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
                    return jsonResponse.optString("response", "I'm having trouble thinking right now.").trim();
                }
            } else if (conn.getResponseCode() == 404 && !isDownloading) {
                // Model not found on the server, try to pull it
                Log.w(TAG, "Model " + modelName + " not found. Attempting to download...");
                if (pullModelFromOllama(modelName)) {
                    return "I'm downloading the '" + modelName + "' model now. Please try again in a minute!";
                }
                return null; // If pullModelFromOllama returns false, we need to return something.
            } else {
                Log.e(TAG, "Ollama Error (Code: " + conn.getResponseCode() + ")");
                return null;
            }

        } catch (Exception e) {
            Log.e(TAG, "LLM Error: " + e.getMessage());
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * Commands the Ollama server to download a model.
     */
    private boolean pullModelFromOllama(String modelName) {
        isDownloading = true;
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                String host = android.os.Build.FINGERPRINT.contains("generic") ? "10.0.2.2" : "localhost";
                URL url = new URL("http://" + host + ":11434/api/pull");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                JSONObject jsonBody = new JSONObject();
                jsonBody.put("name", modelName);
                jsonBody.put("stream", false); // Keep it simple for the initial pull

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(jsonBody.toString().getBytes(StandardCharsets.UTF_8));
                }

                int code = conn.getResponseCode();
                if (code == 200) {
                    Log.d(TAG, "Model " + modelName + " downloaded successfully.");
                } else {
                    Log.e(TAG, "Failed to pull model. Code: " + code);
                }
            } catch (Exception e) {
                Log.e(TAG, "Download Error: " + e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
                isDownloading = false;
            }
        }).start();
        return true;
    }
}