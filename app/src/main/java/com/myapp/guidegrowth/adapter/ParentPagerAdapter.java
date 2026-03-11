package com.myapp.guidegrowth.adapter;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.myapp.guidegrowth.model.Child;
import com.myapp.guidegrowth.ui.parent.fragments.AppUsageFragment;
import com.myapp.guidegrowth.ui.parent.fragments.ControlsFragment;
import com.myapp.guidegrowth.ui.parent.fragments.LocationFragment;

public class ParentPagerAdapter extends FragmentStateAdapter {

    private Child selectedChild;
    private static final int TAB_COUNT = 3;

    public ParentPagerAdapter(@NonNull FragmentActivity fragmentActivity, Child selectedChild) {
        super(fragmentActivity);
        this.selectedChild = selectedChild;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0:
                return LocationFragment.newInstance(selectedChild);
            case 1:
                return AppUsageFragment.newInstance(selectedChild);
            case 2:
                return ControlsFragment.newInstance(selectedChild);
            default:
                return LocationFragment.newInstance(selectedChild);
        }
    }

    @Override
    public int getItemCount() {
        return TAB_COUNT;
    }
}
