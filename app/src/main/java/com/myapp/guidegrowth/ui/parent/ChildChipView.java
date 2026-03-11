package com.myapp.guidegrowth.ui.parent;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.myapp.guidegrowth.R;
import com.myapp.guidegrowth.databinding.ViewChildChipBinding;
import com.myapp.guidegrowth.model.Child;

public class ChildChipView extends FrameLayout {

    private ViewChildChipBinding binding;
    private Child child;
    
    public ChildChipView(@NonNull Context context) {
        super(context);
        init(context);
    }
    
    public ChildChipView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }
    
    public ChildChipView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }
    
    private void init(Context context) {
        binding = ViewChildChipBinding.inflate(LayoutInflater.from(context), this, true);
    }
    
    public void setChild(Child child) {
        this.child = child;
        binding.childNameText.setText(child.getName());
    }
    
    public Child getChild() {
        return child;
    }
    
    public void setSelected(boolean selected) {
        binding.chipContainer.setSelected(selected);
        binding.childNameText.setTextColor(getResources().getColor(
                selected ? R.color.white : R.color.black));
    }
}
