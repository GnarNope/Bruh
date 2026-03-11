package com.myapp.guidegrowth.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.myapp.guidegrowth.databinding.ItemAppUsageBinding;
import com.myapp.guidegrowth.model.AppUsage;
import com.myapp.guidegrowth.util.TimeUtils;

import java.util.List;

public class AppUsageAdapter extends RecyclerView.Adapter<AppUsageAdapter.AppUsageViewHolder> {
    
    private List<AppUsage> usageList;
    
    public AppUsageAdapter(List<AppUsage> usageList) {
        this.usageList = usageList;
    }
    
    public void updateData(List<AppUsage> newData) {
        this.usageList = newData;
        notifyDataSetChanged();
    }
    
    @NonNull
    @Override
    public AppUsageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemAppUsageBinding binding = ItemAppUsageBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new AppUsageViewHolder(binding);
    }
    
    @Override
    public void onBindViewHolder(@NonNull AppUsageViewHolder holder, int position) {
        AppUsage usage = usageList.get(position);
        holder.bind(usage);
    }
    
    @Override
    public int getItemCount() {
        return usageList.size();
    }
    
    static class AppUsageViewHolder extends RecyclerView.ViewHolder {
        
        private ItemAppUsageBinding binding;
        
        public AppUsageViewHolder(ItemAppUsageBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
        
        public void bind(AppUsage usage) {
            binding.appNameText.setText(usage.getAppName());
            binding.packageNameText.setText(usage.getPackageName());
            binding.usageTimeText.setText(usage.getFormattedUsageTime());
            
            // Show last used time if available
            if (usage.getLastUsed() > 0) {
                binding.lastUsedText.setText("Last used: " + 
                        TimeUtils.getFormattedTime(usage.getLastUsed()));
            } else {
                binding.lastUsedText.setText("Last used: Unknown");
            }
        }
    }
}
