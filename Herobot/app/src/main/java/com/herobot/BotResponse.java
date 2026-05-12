package com.herobot;

/**
 * Encapsulates the bot's response and its origin.
 */
public class BotResponse {
    public enum Source { DATABASE, WEB, LLM, SYSTEM, NONE }

    private final String text;
    private final Source source;

    public BotResponse(String text, Source source) {
        this.text = text;
        this.source = source;
    }

    public String getText() { return text; }
    public Source getSource() { return source; }
    @Override public String toString() { return text; }
}