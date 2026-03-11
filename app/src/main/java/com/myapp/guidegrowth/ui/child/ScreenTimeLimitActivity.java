package com.myapp.guidegrowth.ui.child;

import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.myapp.guidegrowth.R;

public class ScreenTimeLimitActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_enforcement);
        
        // Prevent going back
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
                WindowManager.LayoutParams.FLAG_FULLSCREEN |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);

        TextView messageTextView = findViewById(R.id.enforcement_message);
        messageTextView.setText("You've reached your screen time limit for today.");
        
        Button okButton = findViewById(R.id.ok_button);
        okButton.setOnClickListener(v -> {
            finishAndRemoveTask();
            // Open the child service manager
            android.content.Intent intent = new Intent(this, ChildServiceManagerActivity.class);
            startActivity(intent);
        });
    }
    
    @Override
    public void onBackPressed() {
        // Prevent going back
    }
}
