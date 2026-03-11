package com.myapp.guidegrowth.ui.child;

import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.myapp.guidegrowth.R;
import com.myapp.guidegrowth.utils.DeviceUtils;

public class BedtimeEnforcementActivity extends AppCompatActivity {

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
        messageTextView.setText("It's bedtime! The device is locked until morning.");
        
        Button okButton = findViewById(R.id.ok_button);
        okButton.setOnClickListener(v -> {
            // Lock the device
            DeviceUtils.lockDevice(this);
            finish();
        });
    }
    
    @Override
    public void onBackPressed() {
        // Prevent going back
    }
}
