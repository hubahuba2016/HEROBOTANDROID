package com.herobot;

public class ChatMessage {
    public enum Type {
        USER, BOT
    }

    private String text;
    private Type type;
    private BotResponse.Source source;

    public ChatMessage(String text, Type type) {
        this.text = text;
        this.type = type;
    }

    public ChatMessage(String text, Type type, BotResponse.Source source) {
        this.text = text;
        this.type = type;
        this.source = source;
    }

    public String getText() {
        return text;
    }

    public Type getType() {
        return type;
    }

    public BotResponse.Source getSource() {
        return source;
    }
}
