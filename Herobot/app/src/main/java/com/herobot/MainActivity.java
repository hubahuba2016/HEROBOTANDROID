package com.herobot;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.*;
import android.view.View;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    TextView chatView;
    EditText inputBox;
    Button sendBtn;
    ExecutorService executor = Executors.newSingleThreadExecutor();

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

                // Legacy training command check (optional, kept for backward compatibility)
                if (userMsg.toLowerCase().startsWith("train:")) {
                    handleLegacyTrainCommand(userMsg);
                    inputBox.setText("");
                    return;
                }
                if (userMsg.toLowerCase().equals("train")) {
                    showTrainingDialog();
                    inputBox.setText("");
                    return;
                }

                // get bot reply in background (for LLM networking)
                executor.execute(() -> {
                    BotResponse response = bot.reply(userMsg);
                    runOnUiThread(() -> {
                        // show bot message
                        chatView.append("HeroBot [" + response.getSource() + "]: " + response.getText() + "\n\n");
                    });
                });

                inputBox.setText("");
            }
        });

        // Better Interface: Long click the send button to open training dialog
        sendBtn.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                showTrainingDialog();
                return true;
            }
        });
    }

    private void showTrainingDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Train HeroBot");

        // Create a simple vertical layout for the dialog
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        final EditText qInput = new EditText(this);
        qInput.setHint("User Question (e.g. What is your favorite food?)");
        layout.addView(qInput);

        final EditText aInput = new EditText(this);
        aInput.setHint("Bot Answer (e.g. I love consuming data!)");
        layout.addView(aInput);

        builder.setView(layout);

        builder.setPositiveButton("Save Knowledge", (dialog, which) -> {
            String q = qInput.getText().toString().trim();
            String a = aInput.getText().toString().trim();
            if (!q.isEmpty() && !a.isEmpty()) {
                bot.train(q, a);
                chatView.append("System: HeroBot has learned a new response.\n\n");
            } else {
                Toast.makeText(this, "Both fields are required!", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void handleLegacyTrainCommand(String userMsg) {
        try {
            String content = userMsg.substring(6).trim();
            String[] parts = content.split("\\|");
            if (parts.length == 2) {
                bot.train(parts[0].trim(), parts[1].trim());
                chatView.append("HeroBot: System updated! I learned that.\n\n");
            }
        } catch (Exception e) {
            chatView.append("HeroBot: Error in training format. Use 'train: Q | A'\n\n");
        }
    }
}