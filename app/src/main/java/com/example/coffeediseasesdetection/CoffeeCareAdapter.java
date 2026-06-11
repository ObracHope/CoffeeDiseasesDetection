package com.example.coffeediseasesdetection;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class CoffeeCareAdapter extends RecyclerView.Adapter<CoffeeCareAdapter.CareViewHolder> {
    
    private List<CoffeeCareGuideFragment.CoffeeCareItem> careItems;
    private OnCareItemClickListener listener;
    
    public interface OnCareItemClickListener {
        void onCareItemClick(CoffeeCareGuideFragment.CoffeeCareItem item);
    }
    
    public CoffeeCareAdapter(List<CoffeeCareGuideFragment.CoffeeCareItem> careItems, OnCareItemClickListener listener) {
        this.careItems = careItems;
        this.listener = listener;
    }
    
    @NonNull
    @Override
    public CareViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_coffee_care, parent, false);
        return new CareViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull CareViewHolder holder, int position) {
        CoffeeCareGuideFragment.CoffeeCareItem item = careItems.get(position);

        holder.ivImage.setImageResource(item.getImageRes());
        holder.tvTitle.setText(item.getTitle());
        holder.tvDescription.setText(item.getDescription());

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onCareItemClick(item);
            }
        });

        holder.btnLearnMore.setOnClickListener(v -> {
            if (listener != null) {
                listener.onCareItemClick(item);
            }
        });
    }
    
    @Override
    public int getItemCount() {
        return careItems.size();
    }
    
    static class CareViewHolder extends RecyclerView.ViewHolder {
        ImageView ivImage;
        TextView tvTitle;
        TextView tvDescription;
        TextView btnLearnMore;

        public CareViewHolder(@NonNull View itemView) {
            super(itemView);
            ivImage = itemView.findViewById(R.id.ivCareImage);
            tvTitle = itemView.findViewById(R.id.tvCareTitle);
            tvDescription = itemView.findViewById(R.id.tvCareDescription);
            btnLearnMore = itemView.findViewById(R.id.btnLearnMore);
        }
    }
}
