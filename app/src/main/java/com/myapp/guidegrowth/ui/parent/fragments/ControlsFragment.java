package com.myapp.guidegrowth.ui.parent.fragments;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.myapp.guidegrowth.R;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.myapp.guidegrowth.databinding.DialogSendWarningBinding;
import com.myapp.guidegrowth.databinding.FragmentControlsBinding;
import com.myapp.guidegrowth.model.Child;
import com.myapp.guidegrowth.ui.parent.ParentDashboardActivity;
import com.myapp.guidegrowth.utils.CommandUtils;

import java.util.HashMap;
import java.util.Map;

public class ControlsFragment extends Fragment {

    private FragmentControlsBinding binding;
    private Child child;
    private FirebaseFirestore db;
    private String childId;

    public static ControlsFragment newInstance(Child child) {
        ControlsFragment fragment = new ControlsFragment();
        Bundle args = new Bundle();
        if (child != null) {
            args.putString("childId", child.getId());
            args.putString("childName", child.getName());
        }
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = FirebaseFirestore.getInstance();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentControlsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Get child info from arguments
        Bundle args = getArguments();
        String childName = null;
        
        if (args != null) {
            childId = args.getString("childId");
            childName = args.getString("childName", "Child");
        }
        
        // Check if we have a valid child
        if (childId == null) {
            binding.noChildSelectedMessage.setVisibility(View.VISIBLE);
            binding.controlsContainer.setVisibility(View.GONE);
            return;
        }
        
        binding.noChildSelectedMessage.setVisibility(View.GONE);
        binding.controlsContainer.setVisibility(View.VISIBLE);
        
        // Setup controls
        setupControlButtons(childName);
        setupAppLimits();
    }

    private void setupControlButtons(String childName) {
        // Lock Device Button
        binding.lockDeviceButton.setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Lock Device")
                    .setMessage("Are you sure you want to lock " + childName + "'s device?")
                    .setPositiveButton("Lock", (dialog, which) -> {
                        sendLockCommand();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
        
        // Unlock Device Button
        binding.unlockDeviceButton.setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Unlock Device")
                    .setMessage("Remove restrictions from " + childName + "'s device?")
                    .setPositiveButton("Unlock", (dialog, which) -> {
                        sendUnlockCommand();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
        
        // Send Warning Button
        binding.sendWarningButton.setOnClickListener(v -> {
            showSendWarningDialog();
        });
        
        // Send Link Button
        binding.sendLinkButton.setOnClickListener(v -> {
            showSendLinkDialog();
        });
        
        // Request Location Button
        binding.requestLocationButton.setOnClickListener(v -> {
            sendCheckInCommand();
            Toast.makeText(requireContext(), "Location request sent", Toast.LENGTH_SHORT).show();
        });
    }

    private void setupAppLimits() {
        // Load current limits
        loadCurrentLimits();
        
        // Daily Screen Time Limit
        binding.screenTimeLimitSlider.addOnChangeListener(new Slider.OnChangeListener() {
            @Override
            public void onValueChange(@NonNull Slider slider, float value, boolean fromUser) {
                int hours = (int) value;
                binding.screenTimeLimitValue.setText(hours + " hours");
            }
        });
        
        // Save limits button
        binding.saveLimitsButton.setOnClickListener(v -> {
            saveAppLimits();
        });
    }

    private void loadCurrentLimits() {
        if (childId == null) return;
        
        DocumentReference limitsRef = db.collection("children")
                .document(childId)
                .collection("settings")
                .document("limits");
        
        limitsRef.get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                Map<String, Object> data = documentSnapshot.getData();
                
                if (data != null) {
                    // Daily screen time limit (in hours)
                    Object dailyLimitObj = data.get("dailyScreenTimeHours");
                    if (dailyLimitObj instanceof Number) {
                        int dailyLimit = ((Number) dailyLimitObj).intValue();
                        binding.screenTimeLimitSlider.setValue(dailyLimit);
                        binding.screenTimeLimitValue.setText(dailyLimit + " hours");
                    }
                    
                    // Bedtime settings
                    Object bedtimeStartObj = data.get("bedtimeStart");
                    if (bedtimeStartObj instanceof String) {
                        binding.bedtimeStartInput.setText((String) bedtimeStartObj);
                    }
                    
                    Object bedtimeEndObj = data.get("bedtimeEnd");
                    if (bedtimeEndObj instanceof String) {
                        binding.bedtimeEndInput.setText((String) bedtimeEndObj);
                    }
                }
            }
        });
    }

    private void saveAppLimits() {
        if (childId == null) return;
        
        // Show loading
        binding.savingProgress.setVisibility(View.VISIBLE);
        binding.saveLimitsButton.setEnabled(false);
        
        // Get values from UI
        int screenTimeLimit = (int) binding.screenTimeLimitSlider.getValue();
        String bedtimeStart = binding.bedtimeStartInput.getText().toString();
        String bedtimeEnd = binding.bedtimeEndInput.getText().toString();
        
        // Create limits map
        Map<String, Object> limits = new HashMap<>();
        limits.put("dailyScreenTimeHours", screenTimeLimit);
        limits.put("bedtimeStart", bedtimeStart);
        limits.put("bedtimeEnd", bedtimeEnd);
        
        // Save to Firestore
        db.collection("children")
                .document(childId)
                .collection("settings")
                .document("limits")
                .set(limits)
                .addOnSuccessListener(aVoid -> {
                    // Also send a command to the child device to update limits immediately
                    if (getActivity() instanceof ParentDashboardActivity) {
                        Map<String, Object> commandData = new HashMap<>();
                        commandData.put(CommandUtils.FIELD_LIMITS, limits);
                        
                        ((ParentDashboardActivity) getActivity()).sendCommandToChild(
                                CommandUtils.CMD_SET_LIMITS, commandData);
                        
                        Toast.makeText(requireContext(), "Limits saved and sent to child device", 
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(), "Limits saved successfully", 
                                Toast.LENGTH_SHORT).show();
                    }
                    binding.savingProgress.setVisibility(View.GONE);
                    binding.saveLimitsButton.setEnabled(true);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(requireContext(), "Failed to save limits: " + e.getMessage(), 
                            Toast.LENGTH_SHORT).show();
                    binding.savingProgress.setVisibility(View.GONE);
                    binding.saveLimitsButton.setEnabled(true);
                });
    }

    private void sendLockCommand() {
        if (getActivity() instanceof ParentDashboardActivity) {
            ((ParentDashboardActivity) getActivity()).sendCommandToChild(CommandUtils.CMD_LOCK_DEVICE, null);
        }
    }

    private void sendUnlockCommand() {
        if (getActivity() instanceof ParentDashboardActivity) {
            ((ParentDashboardActivity) getActivity()).sendCommandToChild(CommandUtils.CMD_UNLOCK_DEVICE, null);
        }
    }

    private void sendCheckInCommand() {
        if (getActivity() instanceof ParentDashboardActivity) {
            ((ParentDashboardActivity) getActivity()).sendCommandToChild(CommandUtils.CMD_CHECK_IN, null);
        }
    }

    private void showSendWarningDialog() {
        // Create dialog with custom layout
        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(requireContext());
        DialogSendWarningBinding dialogBinding = DialogSendWarningBinding.inflate(getLayoutInflater());
        builder.setView(dialogBinding.getRoot());
        
        AlertDialog dialog = builder.create();
        
        // Set up buttons
        dialogBinding.sendButton.setOnClickListener(v -> {
            String message = dialogBinding.warningMessageInput.getText().toString().trim();
            if (!message.isEmpty()) {
                sendWarningMessage(message);
                dialog.dismiss();
            }
        });
        
        dialogBinding.cancelButton.setOnClickListener(v -> dialog.dismiss());
        
        dialog.show();
    }

    private void sendWarningMessage(String message) {
        if (getActivity() instanceof ParentDashboardActivity) {
            ((ParentDashboardActivity) getActivity()).sendCommandToChild(CommandUtils.CMD_WARNING, message);
        }
    }

    private void showSendLinkDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_send_link, null);
        TextInputEditText linkInput = dialogView.findViewById(R.id.link_input);
        
        new MaterialAlertDialogBuilder(requireContext())
            .setTitle("Send Link to Child's Device")
            .setView(dialogView)
            .setPositiveButton("Send", (dialog, which) -> {
                String link = linkInput.getText() != null ? linkInput.getText().toString().trim() : "";
                if (!TextUtils.isEmpty(link)) {
                    sendLinkToChild(link);
                } else {
                    Toast.makeText(requireContext(), "Please enter a valid URL", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void sendLinkToChild(String link) {
        if (childId == null) return;
        
        // Validate URL format and add http/https if missing
        if (!link.startsWith("http://") && !link.startsWith("https://")) {
            link = "https://" + link;
        }
        
        final String finalLink = link;
        
        // Show loading indicator
        binding.sendingProgress.setVisibility(View.VISIBLE);
        
        if (getActivity() instanceof ParentDashboardActivity) {
            Map<String, Object> commandData = new HashMap<>();
            commandData.put(CommandUtils.FIELD_URL, finalLink);
            
            ((ParentDashboardActivity) getActivity()).sendCommandToChild(
                    CommandUtils.CMD_OPEN_URL, commandData);
                    
            Toast.makeText(requireContext(), "Link sent to child device", 
                    Toast.LENGTH_SHORT).show();
            
            binding.sendingProgress.setVisibility(View.GONE);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
