package com.myapp.guidegrowth.ui.parent.fragments;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.myapp.guidegrowth.R;
import com.myapp.guidegrowth.model.AppUsage;
import com.myapp.guidegrowth.utils.CommandUtils;
import com.myapp.guidegrowth.adapter.AppUsageAdapter;
import com.myapp.guidegrowth.databinding.FragmentAppUsageBinding;
import com.myapp.guidegrowth.model.AppUsage;
import com.myapp.guidegrowth.model.Child;
import com.myapp.guidegrowth.ui.parent.ParentDashboardActivity;
import com.myapp.guidegrowth.util.TimeUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AppUsageFragment extends Fragment {

    private FragmentAppUsageBinding binding;
    private Child child;
    private FirebaseFirestore db;
    private ListenerRegistration usageListener;
    private AppUsageAdapter adapter;
    private List<AppUsage> appUsageList = new ArrayList<>();
    private PackageManager packageManager;

    public static AppUsageFragment newInstance(Child child) {
        AppUsageFragment fragment = new AppUsageFragment();
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
        packageManager = requireActivity().getPackageManager();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentAppUsageBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Get child info from arguments
        Bundle args = getArguments();
        final String childId = args != null ? args.getString("childId") : null;

        // Check if we have a valid child
        if (childId == null) {
            binding.noChildSelectedMessage.setVisibility(View.VISIBLE);
            binding.usageContainer.setVisibility(View.GONE);
            return;
        }

        // Setup usage stats recycler view
        adapter = new AppUsageAdapter(appUsageList);
        binding.appUsageRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        binding.appUsageRecyclerView.setAdapter(adapter);

        // Initialize charts
        setupUsageChart();

        // Setup refresh button
        binding.refreshUsageButton.setOnClickListener(v -> {
            requestUsageUpdate(childId);
        });

        // Start listening for usage updates
        startUsageListener(childId);
    }

    private void setupUsageChart() {
        // Setup chart appearance
        binding.usageChart.getDescription().setEnabled(false);
        binding.usageChart.setDrawGridBackground(false);
        binding.usageChart.setDrawBarShadow(false);
        binding.usageChart.setDrawValueAboveBar(true);
        binding.usageChart.setPinchZoom(false);
        binding.usageChart.setDoubleTapToZoomEnabled(false);
        binding.usageChart.setScaleEnabled(false);
        binding.usageChart.getLegend().setEnabled(false);

        // X-axis setup
        XAxis xAxis = binding.usageChart.getXAxis();
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setGranularityEnabled(true);
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setAvoidFirstLastClipping(true);

        // Set value formatter for displaying app names
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                int index = (int) value;
                if (index >= 0 && index < appUsageList.size()) {
                    String appName = appUsageList.get(index).getAppName();
                    // Truncate long app names
                    if (appName.length() > 10) {
                        return appName.substring(0, 7) + "...";
                    }
                    return appName;
                }
                return "";
            }
        });

        // Y-axis setup
        binding.usageChart.getAxisLeft().setDrawGridLines(true);
        binding.usageChart.getAxisLeft().setAxisMinimum(0f);
        binding.usageChart.getAxisLeft().setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                if (value == 0) return "0";
                return String.format("%.0f min", value);
            }
        });
        binding.usageChart.getAxisRight().setEnabled(false);

        // Set empty data initially to prevent crashes
        BarData emptyData = new BarData();
        binding.usageChart.setData(emptyData);
    }

    private void startUsageListener(String childId) {
        if (childId == null) return;

        DocumentReference usageRef = db.collection("children")
                .document(childId)
                .collection("usageStats")
                .document("latest");

        usageListener = usageRef.addSnapshotListener((snapshot, e) -> {
            binding.usageProgressBar.setVisibility(View.GONE);

            if (e != null) {
                Toast.makeText(getContext(), "Error listening for usage stats: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                Log.e("AppUsageFragment", "Error listening for usage stats", e);
                return;
            }

            try {
                if (snapshot != null && snapshot.exists()) {
                    Map<String, Object> data = snapshot.getData();

                    if (data != null) {
                        // Get total screen time
                        Object totalTimeObj = data.get("totalScreenTimeMs");
                        long totalScreenTime = 0;

                        if (totalTimeObj instanceof Long) {
                            totalScreenTime = (Long) totalTimeObj;
                        } else if (totalTimeObj instanceof Integer) {
                            totalScreenTime = ((Integer) totalTimeObj).longValue();
                        } else if (totalTimeObj instanceof Double) {
                            totalScreenTime = ((Double) totalTimeObj).longValue();
                        }

                        // Format and display total screen time
                        binding.totalScreenTimeText.setText(
                                "Total Screen Time: " + TimeUtils.formatMillis(totalScreenTime));

                        // Get timestamp
                        Object timestampObj = data.get("timestamp");
                        long timestamp;

                        if (timestampObj instanceof Timestamp) {
                            timestamp = ((Timestamp) timestampObj).toDate().getTime();
                        } else if (timestampObj instanceof Long) {
                            timestamp = (Long) timestampObj;
                        } else if (timestampObj instanceof Integer) {
                            timestamp = ((Integer) timestampObj).longValue();
                        } else if (timestampObj instanceof Double) {
                            timestamp = ((Double) timestampObj).longValue();
                        } else {
                            timestamp = System.currentTimeMillis();
                        }

                        // Update last update time
                        binding.lastUpdateText.setText(
                                "Last updated: " + TimeUtils.getFormattedTime(timestamp));

                        // Get app usage data
                        Map<String, Object> apps = (Map<String, Object>) data.get("apps");

                        if (apps != null && !apps.isEmpty()) {
                            parseAppUsageData(apps);
                            binding.noUsageDataMessage.setVisibility(View.GONE);
                            binding.usageContainer.setVisibility(View.VISIBLE);
                        } else {
                            binding.noUsageDataMessage.setText("No app usage data available");
                            binding.noUsageDataMessage.setVisibility(View.VISIBLE);
                            binding.usageContainer.setVisibility(View.GONE);
                        }
                    } else {
                        binding.noUsageDataMessage.setText("No usage data available");
                        binding.noUsageDataMessage.setVisibility(View.VISIBLE);
                        binding.usageContainer.setVisibility(View.GONE);
                    }
                } else {
                    binding.noUsageDataMessage.setText("Waiting for usage data...");
                    binding.noUsageDataMessage.setVisibility(View.VISIBLE);
                    binding.usageContainer.setVisibility(View.GONE);
                }
            } catch (Exception ex) {
                Log.e("AppUsageFragment", "Error processing usage snapshot", ex);
                binding.noUsageDataMessage.setText("Error processing usage data");
                binding.noUsageDataMessage.setVisibility(View.VISIBLE);
                binding.usageContainer.setVisibility(View.GONE);
            }
        });
    }

    private void parseAppUsageData(Map<String, Object> apps) {
        appUsageList.clear();

        try {
            for (Map.Entry<String, Object> entry : apps.entrySet()) {
                String packageName = entry.getKey();

                // Only skip system packages that aren't important for displaying to parents
                if (packageName == null || packageName.isEmpty() || 
                    packageName.startsWith("com.android.systemui") || 
                    packageName.equals("android")) {
                    continue;
                }

                Object appDataObj = entry.getValue();
                if (!(appDataObj instanceof Map)) {
                    continue;
                }

                Map<String, Object> appData = (Map<String, Object>) appDataObj;

                if (appData != null) {
                    long usageTime = 0;
                    long lastUsed = 0;

                    Object usageTimeObj = appData.get("totalTimeMs");
                    if (usageTimeObj instanceof Long) {
                        usageTime = (Long) usageTimeObj;
                    } else if (usageTimeObj instanceof Integer) {
                        usageTime = ((Integer) usageTimeObj).longValue();
                    } else if (usageTimeObj instanceof Double) {
                        usageTime = ((Double) usageTimeObj).longValue();
                    }

                    Object lastUsedObj = appData.get("lastTimeUsed");
                    if (lastUsedObj instanceof Long) {
                        lastUsed = (Long) lastUsedObj;
                    } else if (lastUsedObj instanceof Integer) {
                        lastUsed = ((Integer) lastUsedObj).longValue();
                    } else if (lastUsedObj instanceof Double) {
                        lastUsed = ((Double) lastUsedObj).longValue();
                    }

                    // Include all apps with any meaningful usage (more than 5 seconds)
                    if (usageTime > 5000) {
                        String appName = getApplicationName(packageName);
                        AppUsage usage = new AppUsage(packageName, appName, usageTime, lastUsed);
                        appUsageList.add(usage);
                    }
                }
            }

            // Sort by usage time (descending)
            Collections.sort(appUsageList, (o1, o2) -> Long.compare(o2.getUsageTimeMs(), o1.getUsageTimeMs()));

            // Update chart with top apps for visualization
            List<AppUsage> chartList;
            if (appUsageList.size() > 10) {
                chartList = appUsageList.subList(0, 10);
            } else {
                chartList = appUsageList;
            }

            // Update chart and RecyclerView
            updateChart(chartList);
            adapter.updateData(appUsageList);

        } catch (Exception e) {
            Log.e("AppUsageFragment", "Error parsing app usage data: " + e.getMessage());
            Toast.makeText(getContext(), "Error displaying app usage data", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateChart(List<AppUsage> chartApps) {
        List<BarEntry> entries = new ArrayList<>();

        // Check if the list is empty to avoid crashes
        if (chartApps.isEmpty()) {
            // Set empty data to avoid null pointer exception
            BarData barData = new BarData();
            binding.usageChart.setData(barData);
            binding.usageChart.invalidate();
            return;
        }

        for (int i = 0; i < chartApps.size(); i++) {
            AppUsage app = chartApps.get(i);
            // Convert ms to minutes for better readability
            float minutes = app.getUsageTimeMs() / (1000f * 60f);
            entries.add(new BarEntry(i, minutes));
        }

        BarDataSet dataSet = new BarDataSet(entries, "App Usage (minutes)");
        dataSet.setColors(ColorTemplate.MATERIAL_COLORS);
        dataSet.setDrawValues(true);
        dataSet.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.format("%.1f", value);
            }
        });

        BarData barData = new BarData(dataSet);
        barData.setBarWidth(0.7f); // Make bars wider

        binding.usageChart.setData(barData);
        binding.usageChart.getXAxis().setLabelCount(Math.min(chartApps.size(), 5)); // Limit label count
        binding.usageChart.getXAxis().setLabelRotationAngle(45); // Rotate labels
        binding.usageChart.getXAxis().setTextSize(8f); // Smaller text
        binding.usageChart.setExtraBottomOffset(10f); // Add space for rotated labels
        binding.usageChart.setVisibleXRangeMaximum(8); // Show max 8 bars at a time
        binding.usageChart.invalidate();
        binding.usageChart.animateY(1000);
    }

    private String getApplicationName(String packageName) {
        try {
            ApplicationInfo info = packageManager.getApplicationInfo(packageName, 0);
            return packageManager.getApplicationLabel(info).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return packageName;
        }
    }

    private void requestUsageUpdate(String childId) {
        if (childId == null) return;

        if (getActivity() instanceof ParentDashboardActivity) {
            // Send check-in command to request immediate usage stats update
            Map<String, Object> commandParams = new HashMap<>();
            commandParams.put("type", "immediate");
            commandParams.put("timestamp", System.currentTimeMillis());
            commandParams.put("updateType", "usage_stats");  // Specify we want usage stats
            
            // Use a proper command name that the child app will recognize
            ((ParentDashboardActivity) getActivity()).sendCommandToChild("check_in", commandParams);
            
            Toast.makeText(getContext(), "Requesting latest usage data...", Toast.LENGTH_SHORT).show();
        }
        
        binding.usageProgressBar.setVisibility(View.VISIBLE);
        binding.refreshUsageButton.setEnabled(false);

        // Re-enable after delay but with longer timeout
        binding.refreshUsageButton.postDelayed(() -> {
            if (isAdded()) {
                binding.refreshUsageButton.setEnabled(true);
                binding.usageProgressBar.setVisibility(View.GONE);
            }
        }, 10000); // Wait 10 seconds for data to arrive
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        // Remove usage listener
        if (usageListener != null) {
            usageListener.remove();
        }

        binding = null;
    }
}
