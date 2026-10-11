package com.insta.clone.ui.explore;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.insta.clone.R;

/**
 * Explore & Search Fragment with 3-column media grid and live account search.
 */
public class ExploreFragment extends Fragment {

    private EditText etSearch;
    private RecyclerView rvExploreGrid;

    public ExploreFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_explore, container, false);
        etSearch = view.findViewById(R.id.et_search);
        rvExploreGrid = view.findViewById(R.id.rv_explore_grid);

        setupRecyclerView();
        setupSearchListener();

        return view;
    }

    private void setupRecyclerView() {
        GridLayoutManager layoutManager = new GridLayoutManager(getContext(), 3);
        layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                // Feature every 6th item with a 2x2 span
                return (position % 6 == 0) ? 2 : 1;
            }
        });
        rvExploreGrid.setLayoutManager(layoutManager);
    }

    private void setupSearchListener() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                // Filter search results
            }
        });
    }
}
