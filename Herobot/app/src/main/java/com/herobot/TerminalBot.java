package com.herobot;
import java.io.*;
import java.net.*;
import java.util.Scanner;

public class TerminalBot {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("--- HeroBot Terminal (Ollama Phi-3) ---");
        System.out.println("Type 'exit' to quit.");
        while (true) {
            System.out.print("\nYou: ");
            String input = scanner.nextLine();
            if (input.equalsIgnoreCase("exit")) break;
            askOllama(input);
        }
    }
    public static void askOllama(String prompt) {
        try {
            URL url = new URL("http://localhost:11434/api/generate");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            String cleanPrompt = prompt.replace("\"", "\\\"");
            String jsonInputString = "{\"model\": \"phi3\", \"prompt\": \"" + cleanPrompt + "\", \"stream\": false}";
            try (OutputStream os = conn.getOutputStream()) { os.write(jsonInputString.getBytes("utf-8")); }
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) { response.append(line); }
                String fullResponse = response.toString();
                if (fullResponse.contains("\"response\":\"")) {
                    String output = fullResponse.split("\"response\":\"")[1].split("\",\"")[0];
                    System.out.println("\nBot: " + output.replace("\\n", "\n").replace("\\\"", "\""));
                } else { System.out.println("\nBot (Raw): " + fullResponse); }
            }
        } catch (Exception e) { System.out.println("Error: Is Ollama serve running?"); }
    }
}
