package com.example.coffeediseasesdetection;

import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.Toast;
public class SymptomSelectionActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_symptom_selection);
        
        findViewById(R.id.btnAnalyzeSymptoms).setOnClickListener(v -> {
            boolean yellowRange = ((CheckBox) findViewById(R.id.cbYellowSpots)).isChecked();
            boolean brownSpots = ((CheckBox) findViewById(R.id.cbBrownSpots)).isChecked();
            boolean wilted = ((CheckBox) findViewById(R.id.cbWiltedLeaves)).isChecked();
            
            if (yellowRange && brownSpots) {
                Toast.makeText(this, R.string.symptom_rust_hint, Toast.LENGTH_LONG).show();
            } else if (wilted) {
                Toast.makeText(this, R.string.symptom_wilt_hint, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, R.string.symptom_unclear_hint, Toast.LENGTH_LONG).show();
            }
        });
    }
}
