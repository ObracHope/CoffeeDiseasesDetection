package com.example.coffeediseasesdetection;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class CoffeeCareGuideFragment extends Fragment {

    private RecyclerView recyclerView;
    private CoffeeCareAdapter adapter;
    private List<CoffeeCareItem> careItems;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_coffee_care_guide, container, false);

        recyclerView = view.findViewById(R.id.rvCoffeeCare);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        careItems = new ArrayList<>();
        setupCoffeeCareItems();

        adapter = new CoffeeCareAdapter(careItems, this::showCareDetailDialog);
        recyclerView.setAdapter(adapter);

        return view;
    }

    private void setupCoffeeCareItems() {
        careItems.add(new CoffeeCareItem(
                "Maji ya Kumwagilia",
                "Mwagilia majani ya kahawa mara kwa mara lakini usizidi maji",
                "Mwagilia kwa kina mara moja au mbili kila wiki, ruhusisha udongo ukauke kidogo kati ya maji. Kumwagilia asubuhi ni bora zaidi kuzuia magonjwa ya fangasi.",
                R.drawable.img_healthy_leaf
        ));

        careItems.add(new CoffeeCareItem(
                "Mbolea",
                "Toa mbolea kwa mimea yako ya kahawa kila mwezi wakati wa msimu wa ukuaji",
                "Tumia mbolea iliyosawazika (10-10-10) kila wiki 4-6 wakati wa msimu wa ukuaji. Punguza kulisha wakati wa miezi ya baridi.",
                R.drawable.coffee
        ));

        careItems.add(new CoffeeCareItem(
                "Kukata",
                "Kata mara kwa mara kuendeleza umbo na afya",
                "Ondoa matawi yaliyokufa au yaliyo na maradhi mara moja. Wakati bora wa kukata ni baada ya mavuno. Dumisha matawi makuu 3-5.",
                R.drawable.img_healthy_leaf
        ));

        careItems.add(new CoffeeCareItem(
                "Kudhibiti Wadudu",
                "Angalia na kudhibiti wadudu mara kwa mara",
                "Angalia majani kila wiki kwa dalili za wadudu. Tumia mafuta ya neem au sabuni ya dawa ya wadudu kwa udhibiti wa kikaboni. Ondoa majani yaliyoathirika.",
                R.drawable.img_leaf_miner
        ));

        careItems.add(new CoffeeCareItem(
                "Kuzuia Magonjwa",
                "Kuzuia magonjwa kabla hayajaanza",
                "Hakikisha upepo mzuri kati ya mimea. Epuka kumwagilia maji juu. Ondoa majani yaliyoanguka na takataka. Tumia dawa ya shaba kuzuia kutu na ugonjwa wa beri.",
                R.drawable.img_rust_leaf
        ));

        careItems.add(new CoffeeCareItem(
                "Kuvuna",
                "Vuna matunda ya kahawa wakati wa ukomavu",
                "Chukua matunda mekundu yanapokomaa. Vuna kila wiki 2-3 wakati wa msimu. Tumia ndani ya masaa 24 kwa ubora bora.",
                R.drawable.coffee
        ));
    }

    private void showCareDetailDialog(CoffeeCareItem item) {
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(requireContext());

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_care_detail, null);
        ImageView ivDialogImage = dialogView.findViewById(R.id.ivDialogImage);
        TextView tvDialogTitle = dialogView.findViewById(R.id.tvDialogTitle);
        TextView tvDialogDescription = dialogView.findViewById(R.id.tvDialogDescription);

        ivDialogImage.setImageResource(item.getImageRes());
        tvDialogTitle.setText(item.getTitle());
        tvDialogDescription.setText(item.getDetailedDescription());

        builder.setView(dialogView);
        builder.setPositiveButton("Sawa", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    public static class CoffeeCareItem {
        private final String title;
        private final String description;
        private final String detailedDescription;
        private final int imageRes;

        public CoffeeCareItem(String title, String description, String detailedDescription, int imageRes) {
            this.title = title;
            this.description = description;
            this.detailedDescription = detailedDescription;
            this.imageRes = imageRes;
        }

        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public String getDetailedDescription() { return detailedDescription; }
        public int getImageRes() { return imageRes; }
    }
}
