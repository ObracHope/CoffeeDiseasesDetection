package com.example.coffeediseasesdetection;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class HelpSupportActivity extends BaseActivity {

    private static final int[][] FAQ = {
            {R.string.faq_q1, R.string.faq_a1},
            {R.string.faq_q2, R.string.faq_a2},
            {R.string.faq_q3, R.string.faq_a3},
            {R.string.faq_q4, R.string.faq_a4}
    };

    private View[] faqViews;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help_support);

        findViewById(R.id.ivBackButton).setOnClickListener(v -> finish());

        LinearLayout container = findViewById(R.id.faqContainer);
        LayoutInflater inflater = LayoutInflater.from(this);
        faqViews = new View[FAQ.length];

        for (int i = 0; i < FAQ.length; i++) {
            View item = inflater.inflate(R.layout.item_faq, container, false);
            ((TextView) item.findViewById(R.id.tvFaqQuestion)).setText(FAQ[i][0]);
            ((TextView) item.findViewById(R.id.tvFaqAnswer)).setText(FAQ[i][1]);
            container.addView(item);
            faqViews[i] = item;
        }

        TextInputEditText etSearch = findViewById(R.id.etHelpSearch);
        if (etSearch != null) {
            etSearch.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    filterFaq(s == null ? "" : s.toString());
                }
                @Override public void afterTextChanged(Editable s) {}
            });
        }

        MaterialButton btnLiveChat = findViewById(R.id.btnLiveChat);
        if (btnLiveChat != null) {
            btnLiveChat.setOnClickListener(v -> openEmail("support@coffee.com", "Live Chat Support"));
        }
    }

    private void filterFaq(String query) {
        String q = query.trim().toLowerCase();
        for (int i = 0; i < faqViews.length; i++) {
            TextView question = faqViews[i].findViewById(R.id.tvFaqQuestion);
            TextView answer = faqViews[i].findViewById(R.id.tvFaqAnswer);
            String text = (question.getText() + " " + answer.getText()).toLowerCase();
            faqViews[i].setVisibility(q.isEmpty() || text.contains(q) ? View.VISIBLE : View.GONE);
        }
    }

    private void openEmail(String address, String subject) {
        Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + address));
        intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, R.string.help_contact_email, Toast.LENGTH_SHORT).show();
        }
    }
}
