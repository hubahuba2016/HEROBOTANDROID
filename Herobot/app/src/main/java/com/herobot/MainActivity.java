package com.herobot;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
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
    private Chatbot chatbot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        chatbot = new Chatbot(this);
        viewModel = new ViewModelProvider(this).get(ChatViewModel.class);
        viewModel.init(chatbot);
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
            } else if (userMsg.toLowerCase().startsWith("ollama:")) {
                handleOllamaCommand(userMsg);
            } else {
                viewModel.sendMessage(userMsg);
            }
            binding.inputBox.setText("");
        });

        binding.voiceBtn.setOnClickListener(v -> {
            voiceAssistant.startListening(this, REQ_CODE_SPEECH_INPUT);
        });

        binding.sendBtn.setOnLongClickListener(v -> {
            showOllamaSettingsDialog();
            return true;
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "Ollama Settings");
        menu.add(0, 2, 0, "Train Bot");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == 1) {
            showOllamaSettingsDialog();
            return true;
        } else if (item.getItemId() == 2) {
            showTrainingDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
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

    private void showOllamaSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Ollama Settings");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        final EditText urlInput = new EditText(this);
        urlInput.setHint("Server URL (e.g., http://192.168.1.5:11434/api/generate)");
        layout.addView(urlInput);

        final EditText modelInput = new EditText(this);
        modelInput.setHint("Model Name (e.g., llama3, phi)");
        layout.addView(modelInput);

        builder.setView(layout);
        builder.setPositiveButton("Save", (dialog, which) -> {
            String url = urlInput.getText().toString().trim();
            String model = modelInput.getText().toString().trim();
            if (!url.isEmpty()) chatbot.setOllamaServerUrl(url);
            if (!model.isEmpty()) chatbot.setOllamaModel(model);
            Toast.makeText(this, "Ollama settings updated", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
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

    private void handleOllamaCommand(String userMsg) {
        try {
            String content = userMsg.substring(7).trim();
            if (content.contains("|")) {
                String[] parts = content.split("\\|");
                chatbot.setOllamaServerUrl(parts[0].trim());
                chatbot.setOllamaModel(parts[1].trim());
                Toast.makeText(this, "Ollama Configured", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Use 'ollama: URL | MODEL'", Toast.LENGTH_SHORT).show();
        }
    }
}
