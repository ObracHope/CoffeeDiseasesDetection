package com.example.coffeediseasesdetection.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.coffeediseasesdetection.R;
import com.example.coffeediseasesdetection.model.CoffeeTip;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

public class CoffeeTipsAdapter extends RecyclerView.Adapter<CoffeeTipsAdapter.TipViewHolder> {

    public interface OnTipClickListener {
        void onTipClick(CoffeeTip tip);
    }

    private static final float CARD_GAP_DP = 12f;
    private static final float HORIZONTAL_PADDING_DP = 40f;
    private static final float CARD_HEIGHT_RATIO = 1.18f;

    private final List<CoffeeTip> tipsList;
    private final OnTipClickListener listener;
    private final int cardWidthPx;
    private final int cardImageHeightPx;

    public CoffeeTipsAdapter(Context context, List<CoffeeTip> tipsList, OnTipClickListener listener) {
        this.tipsList = tipsList;
        this.listener = listener;
        cardWidthPx = calculateCardWidthPx(context);
        cardImageHeightPx = (int) (cardWidthPx * CARD_HEIGHT_RATIO);
    }

    public static int calculateCardWidthPx(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int padding = (int) (HORIZONTAL_PADDING_DP * density);
        int gap = (int) (CARD_GAP_DP * density);
        return (screenWidth - padding - gap) / 2;
    }

    public static int calculateCardHeightPx(Context context) {
        return (int) (calculateCardWidthPx(context) * CARD_HEIGHT_RATIO);
    }

    @NonNull
    @Override
    public TipViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_coffee_tip, parent, false);

        RecyclerView.LayoutParams cardParams = new RecyclerView.LayoutParams(cardWidthPx, ViewGroup.LayoutParams.WRAP_CONTENT);
        view.setLayoutParams(cardParams);

        TipViewHolder holder = new TipViewHolder(view);
        ViewGroup.LayoutParams imageParams = holder.flImageContainer.getLayoutParams();
        imageParams.height = cardImageHeightPx;
        holder.flImageContainer.setLayoutParams(imageParams);

        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull TipViewHolder holder, int position) {
        CoffeeTip tip = tipsList.get(position);

        holder.tvTitle.setText(tip.getTitle());
        holder.ivImage.setImageResource(tip.getImageRes());
        holder.ivImage.setScaleType(ImageView.ScaleType.CENTER_CROP);

        View.OnClickListener open = v -> {
            if (listener != null) {
                listener.onTipClick(tip);
            }
        };

        holder.cardRoot.setOnClickListener(open);
        if (holder.ivInfo != null) {
            holder.ivInfo.setOnClickListener(open);
        }
    }

    @Override
    public int getItemCount() {
        return tipsList.size();
    }

    static class TipViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardRoot;
        FrameLayout flImageContainer;
        ImageView ivImage;
        ImageView ivInfo;
        TextView tvTitle;

        TipViewHolder(@NonNull View itemView) {
            super(itemView);
            cardRoot = itemView.findViewById(R.id.cardTipRoot);
            flImageContainer = itemView.findViewById(R.id.flTipImageContainer);
            ivImage = itemView.findViewById(R.id.ivTipImage);
            ivInfo = itemView.findViewById(R.id.ivTipInfo);
            tvTitle = itemView.findViewById(R.id.tvTipTitle);
        }
    }
}
