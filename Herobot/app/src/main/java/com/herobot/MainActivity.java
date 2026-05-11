package com.herobot;

import android.app.Activity;
import android.os.Bundle;
import android.widget.*;
import android.view.View;

public class MainActivity extends Activity {

    TextView chatView;
    EditText inputBox;
    Button sendBtn;

    Chatbot bot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        chatView = findViewById(R.id.chatView);
        inputBox = findViewById(R.id.inputBox);
        sendBtn = findViewById(R.id.sendBtn);

        // ✅ CORRECT initialization (ONLY HERE)
        bot = new Chatbot(this);

        sendBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                String userMsg = inputBox.getText().toString().trim();

                if (userMsg.isEmpty()) return;

                // show user message
                chatView.append("You: " + userMsg + "\n");

                // get bot reply
                String reply = bot.reply(userMsg);

                // show bot message
                chatView.append("HeroBot: " + reply + "\n\n");

                inputBox.setText("");
            }
        });
    }
}