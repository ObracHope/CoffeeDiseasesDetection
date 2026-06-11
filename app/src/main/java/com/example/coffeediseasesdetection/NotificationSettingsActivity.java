package com.example.coffeediseasesdetection;

import android.content.Intent;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;
import com.google.android.material.materialswitch.MaterialSwitch;
import android.content.SharedPreferences;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

public class NotificationSettingsActivity extends BaseActivity {

    private SharedPreferences prefs;
    private static final String PREF_NAME = "notification_settings_prefs";
    private String currentPickingKey;
    private TextView currentPickingTextView;

    private final ActivityResultLauncher<Intent> ringtonePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri uri;
                    if (android.os.Build.VERSION.SDK_INT >= 33) {
                        uri = result.getData().getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri.class);
                    } else {
                        uri = result.getData().getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                    }
                    if (uri != null) {
                        prefs.edit().putString(currentPickingKey, uri.toString()).apply();
                        updateRingtoneName(uri, currentPickingTextView);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification_settings);

        prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);

        if (findViewById(R.id.btnBack) != null) {
            findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        }

        setupSwitch(R.id.itemMsgShow, "show_notifications_label", "pref_disease_show", true);
        setupLink(R.id.itemMsgSound, "sound_label", "pref_disease_sound_val", "");
        setupSwitch(R.id.itemMsgReaction, "reaction_notifications_label", "pref_disease_reaction", true);

        setupSwitch(R.id.itemGrpShow, "show_notifications_label", "pref_results_show", true);
        setupLink(R.id.itemGrpSound, "sound_label", "pref_results_sound_val", "");
        setupSwitch(R.id.itemGrpReaction, "reaction_notifications_label", "pref_results_reaction", true);

        setupSwitch(R.id.itemStatusShow, "show_notifications_label", "pref_tips_show", true);
        setupLink(R.id.itemStatusSound, "sound_label", "pref_tips_sound_val", "");
        setupSwitch(R.id.itemStatusReaction, "reaction_notifications_label", "pref_tips_reaction", true);
    }

    private void updateRingtoneName(Uri uri, TextView tvValue) {
        if (tvValue == null) return;
        if (uri == null) {
            tvValue.setText("None");
            return;
        }
        try {
            Ringtone ringtone = RingtoneManager.getRingtone(this, uri);
            if (ringtone != null) {
                tvValue.setText(ringtone.getTitle(this));
            } else {
                tvValue.setText("Unknown");
            }
        } catch (Exception e) {
            tvValue.setText("Default");
        }
    }

    private void setupSwitch(int containerId, String labelRes, String prefKey, boolean defaultVal) {
        android.view.View container = findViewById(containerId);
        if (container == null) return;
        
        TextView tvLabel = container.findViewById(R.id.tvLabel);
        MaterialSwitch switchControl = container.findViewById(R.id.switchControl);

        if (tvLabel != null) {
            try {
                int resId = getResources().getIdentifier(labelRes, "string", getPackageName());
                tvLabel.setText(getString(resId));
            } catch (Exception e) {
                tvLabel.setText(labelRes.replace("_", " "));
            }
        }

        if (switchControl != null) {
            switchControl.setChecked(prefs.getBoolean(prefKey, defaultVal));
            switchControl.setOnCheckedChangeListener((buttonView, isChecked) -> {
                prefs.edit().putBoolean(prefKey, isChecked).apply();
            });
        }
    }

    private void setupLink(int containerId, String labelRes, String prefKey, String defaultVal) {
        android.view.View container = findViewById(containerId);
        if (container == null) return;
        
        TextView tvLabel = container.findViewById(R.id.tvLabel);
        TextView tvValue = container.findViewById(R.id.tvValue);

        if (tvLabel != null) {
            try {
                int resId = getResources().getIdentifier(labelRes, "string", getPackageName());
                tvLabel.setText(getString(resId));
            } catch (Exception e) {
                tvLabel.setText(labelRes.replace("_", " "));
            }
        }

        String uriString = prefs.getString(prefKey, defaultVal);
        if (tvValue != null) {
            if (!uriString.isEmpty()) {
                updateRingtoneName(Uri.parse(uriString), tvValue);
            } else {
                tvValue.setText("Default");
            }
        }

        container.setOnClickListener(v -> {
            currentPickingKey = prefKey;
            currentPickingTextView = tvValue;
            Intent pickerIntent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
            pickerIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Tone");
            pickerIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
            pickerIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
            pickerIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
            ringtonePickerLauncher.launch(pickerIntent);
        });
    }
}
