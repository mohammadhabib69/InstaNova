package com.insta.clone.ui.reels;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;
import com.insta.clone.R;

/**
 * Reels Fragment with full-screen vertical ViewPager2 video playback.
 */
public class ReelsFragment extends Fragment {

    private ViewPager2 vpReels;

    public ReelsFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_reels, container, false);
        vpReels = view.findViewById(R.id.vp_reels);
        vpReels.setOrientation(ViewPager2.ORIENTATION_VERTICAL);

        return view;
    }
}
