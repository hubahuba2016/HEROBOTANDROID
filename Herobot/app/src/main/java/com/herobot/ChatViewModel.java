package com.herobot;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatViewModel extends ViewModel {

    private final MutableLiveData<List<ChatMessage>> messages = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> isProcessing = new MutableLiveData<>(false);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Chatbot bot;

    public void init(Chatbot bot) {
        this.bot = bot;
    }

    public LiveData<List<ChatMessage>> getMessages() {
        return messages;
    }

    public LiveData<Boolean> getIsProcessing() {
        return isProcessing;
    }

    public void sendMessage(String text) {
        List<ChatMessage> currentMessages = messages.getValue();
        if (currentMessages == null) currentMessages = new ArrayList<>();
        
        currentMessages.add(new ChatMessage(text, ChatMessage.Type.USER));
        messages.postValue(currentMessages);

        isProcessing.postValue(true);
        executor.execute(() -> {
            BotResponse response = bot.reply(text);
            ChatMessage botMsg = new ChatMessage(response.getText(), ChatMessage.Type.BOT, response.getSource());
            
            List<ChatMessage> updatedMessages = new ArrayList<>(messages.getValue());
            updatedMessages.add(botMsg);
            messages.postValue(updatedMessages);
            isProcessing.postValue(false);
        });
    }

    public void train(String q, String a) {
        if (bot != null) {
            bot.train(q, a);
            List<ChatMessage> currentMessages = new ArrayList<>(messages.getValue());
            currentMessages.add(new ChatMessage("System: HeroBot has learned a new response.", ChatMessage.Type.BOT, BotResponse.Source.SYSTEM));
            messages.postValue(currentMessages);
        }
    }
}
