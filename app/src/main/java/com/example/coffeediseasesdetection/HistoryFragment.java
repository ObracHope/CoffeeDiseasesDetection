package com.example.coffeediseasesdetection;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Farmer scan history list with optional search by disease or date. */
public class HistoryFragment extends Fragment {

    private RecyclerView recyclerView;
    private HistoryAdapter adapter;
    private TextView tvEmpty;
    private final List<Map<String, Object>> historyList = new ArrayList<>();
    private final List<Map<String, Object>> fullList = new ArrayList<>();
    private ListenerRegistration historyRegistration;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        recyclerView = view.findViewById(R.id.recyclerHistory);
        tvEmpty = view.findViewById(R.id.tvEmptyHistory);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        adapter = new HistoryAdapter(historyList, item -> {
            Object id = item.get("id");
            if (id != null && getContext() != null) {
                Intent i = new Intent(requireContext(), ScanDetailActivity.class);
                i.putExtra("scanId", id.toString());
                startActivity(i);
            }
        });
        recyclerView.setAdapter(adapter);

        TextInputEditText etSearch = view.findViewById(R.id.etHistorySearch);
        if (etSearch != null) {
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    applyFilter(s != null ? s.toString() : "");
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            });
        }

        loadHistory();
    }

    private void loadHistory() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        if (historyRegistration != null) {
            historyRegistration.remove();
            historyRegistration = null;
        }

        historyRegistration = ScanHistoryLoader.listen(requireContext(), user, new ScanHistoryLoader.Callback() {
            @Override
            public void onLoaded(List<Map<String, Object>> scans) {
                if (!isAdded()) return;
                fullList.clear();
                fullList.addAll(scans);
                TextInputEditText etSearch = getView() != null
                        ? getView().findViewById(R.id.etHistorySearch) : null;
                String q = etSearch != null && etSearch.getText() != null
                        ? etSearch.getText().toString() : "";
                applyFilter(q);
            }

            @Override
            public void onError(Exception e) {
                if (!isAdded()) return;
                fullList.clear();
                applyFilter("");
            }
        });
    }

    private void applyFilter(String query) {
        List<Map<String, Object>> filtered = HistoryFilterUtil.filter(fullList, query);
        historyList.clear();
        historyList.addAll(filtered);
        adapter.notifyDataSetChanged();
        if (tvEmpty != null) {
            if (historyList.isEmpty()) {
                tvEmpty.setVisibility(View.VISIBLE);
                boolean searching = query != null && !query.trim().isEmpty();
                tvEmpty.setText(!fullList.isEmpty() && searching
                        ? getString(R.string.history_no_search_match)
                        : getString(R.string.no_scan_history_yet));
            } else {
                tvEmpty.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (historyRegistration != null) {
            historyRegistration.remove();
            historyRegistration = null;
        }
    }
}
