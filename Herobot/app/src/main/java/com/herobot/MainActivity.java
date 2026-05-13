package com.herobot;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.herobot.databinding.ActivityMainBinding;

import android.content.Intent;
import android.speech.RecognizerIntent;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_CODE_SPEECH_INPUT = 100;
    private ActivityMainBinding binding;
    private ChatViewModel viewModel;
    private ChatAdapter adapter;
    private VoiceAssistant voiceAssistant;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(ChatViewModel.class);
        viewModel.init(new Chatbot(this));
        voiceAssistant = new VoiceAssistant(this);

        setupRecyclerView();
        observeViewModel();
        setupListeners();
    }

    private void setupRecyclerView() {
        adapter = new ChatAdapter();
        binding.rvChat.setLayoutManager(new LinearLayoutManager(this));
        binding.rvChat.setAdapter(adapter);
    }

    private void observeViewModel() {
        viewModel.getMessages().observe(this, messages -> {
            adapter.setMessages(messages);
            if (messages.size() > 0) {
                binding.rvChat.scrollToPosition(messages.size() - 1);
                ChatMessage lastMsg = messages.get(messages.size() - 1);
                if (lastMsg.getType() == ChatMessage.Type.BOT) {
                    voiceAssistant.speak(lastMsg.getText());
                }
            }
        });

        viewModel.getIsProcessing().observe(this, isProcessing -> {
            binding.pbThinking.setVisibility(isProcessing ? View.VISIBLE : View.GONE);
            binding.sendBtn.setEnabled(!isProcessing);
            binding.voiceBtn.setEnabled(!isProcessing);
            binding.inputBox.setEnabled(!isProcessing);
        });
    }

    private void setupListeners() {
        binding.sendBtn.setOnClickListener(v -> {
            String userMsg = binding.inputBox.getText().toString().trim();
            if (userMsg.isEmpty()) return;

            if (userMsg.toLowerCase().startsWith("train:")) {
                handleLegacyTrainCommand(userMsg);
            } else if (userMsg.toLowerCase().equals("train")) {
                showTrainingDialog();
            } else {
                viewModel.sendMessage(userMsg);
            }
            binding.inputBox.setText("");
        });

        binding.voiceBtn.setOnClickListener(v -> {
            voiceAssistant.startListening(this, REQ_CODE_SPEECH_INPUT);
        });

        binding.sendBtn.setOnLongClickListener(v -> {
            showTrainingDialog();
            return true;
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_CODE_SPEECH_INPUT && resultCode == RESULT_OK && data != null) {
            ArrayList<String> result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (result != null && !result.isEmpty()) {
                viewModel.sendMessage(result.get(0));
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        voiceAssistant.shutdown();
    }

    private void showTrainingDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Train HeroBot");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        final EditText qInput = new EditText(this);
        qInput.setHint("User Question");
        layout.addView(qInput);

        final EditText aInput = new EditText(this);
        aInput.setHint("Bot Answer");
        layout.addView(aInput);

        builder.setView(layout);
        builder.setPositiveButton("Save", (dialog, which) -> {
            String q = qInput.getText().toString().trim();
            String a = aInput.getText().toString().trim();
            if (!q.isEmpty() && !a.isEmpty()) {
                viewModel.train(q, a);
            } else {
                Toast.makeText(this, "Fields required", Toast.LENGTH_SHORT).show();
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
                viewModel.train(parts[0].trim(), parts[1].trim());
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error in format. Use 'train: Q | A'", Toast.LENGTH_SHORT).show();
        }
    }
}
