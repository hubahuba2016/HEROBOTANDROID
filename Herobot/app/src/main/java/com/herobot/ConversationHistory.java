package com.herobot;

import java.util.LinkedList;
import java.util.List;

public class ConversationHistory {
    private final int MAX_SIZE = 5;
    private final LinkedList<String> history = new LinkedList<>();

    public void add(String message) {
        if (history.size() >= MAX_SIZE) {
            history.removeFirst();
        }
        history.add(message);
    }

    public List<String> getHistory() {
        return history;
    }

    public String getFormattedHistory() {
        StringBuilder sb = new StringBuilder();
        for (String msg : history) {
            sb.append(msg).append("\n");
        }
        return sb.toString();
    }
}
