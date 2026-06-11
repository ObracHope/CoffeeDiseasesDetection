package com.example.coffeediseasesdetection;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.coffeediseasesdetection.model.CoffeeTip;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;

public class CoffeeTipDetailBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_TITLE = "title";
    private static final String ARG_DESC = "description";
    private static final String ARG_SYMPTOMS = "symptoms";
    private static final String ARG_TREATMENT = "treatment";
    private static final String ARG_IMAGE = "imageRes";

    public static CoffeeTipDetailBottomSheet newInstance(CoffeeTip tip) {
        CoffeeTipDetailBottomSheet sheet = new CoffeeTipDetailBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_TITLE, tip.getTitle());
        args.putString(ARG_DESC, tip.getDescription());
        args.putString(ARG_SYMPTOMS, tip.getSymptoms());
        args.putString(ARG_TREATMENT, tip.getTreatment());
        args.putInt(ARG_IMAGE, tip.getImageRes());
        sheet.setArguments(args);
        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_coffee_tip, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        if (args == null) return;

        ImageView ivImage = view.findViewById(R.id.ivSheetImage);
        TextView tvTitle = view.findViewById(R.id.tvSheetTitle);
        TextView tvDesc = view.findViewById(R.id.tvSheetDescription);
        TextView tvSymptoms = view.findViewById(R.id.tvSheetSymptoms);
        TextView tvTreatment = view.findViewById(R.id.tvSheetTreatment);
        MaterialButton btnClose = view.findViewById(R.id.btnSheetClose);

        tvTitle.setText(args.getString(ARG_TITLE, ""));
        tvDesc.setText(args.getString(ARG_DESC, ""));
        tvSymptoms.setText(args.getString(ARG_SYMPTOMS, ""));
        tvTreatment.setText(args.getString(ARG_TREATMENT, ""));

        int imageRes = args.getInt(ARG_IMAGE, R.drawable.coffee_leaf_sample);
        ivImage.setImageResource(imageRes);

        btnClose.setOnClickListener(v -> dismiss());
    }
}
