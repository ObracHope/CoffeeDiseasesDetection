package com.example.coffeediseasesdetection;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.core.content.ContextCompat;

public class LandingActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (AuthHelper.tryFastSessionRestore(this)) {
            return;
        }

        setContentView(R.layout.activity_landing);

        Button btnLandingLogin = findViewById(R.id.btnLandingLogin);
        Button btnLandingRegister = findViewById(R.id.btnLandingRegister);
        Button btnLangEn = findViewById(R.id.btnLangEn);
        Button btnLangSw = findViewById(R.id.btnLangSw);

        if (btnLandingLogin != null) {
            btnLandingLogin.setOnClickListener(v ->
                    startActivity(new Intent(LandingActivity.this, MainActivity.class)));
        }

        if (btnLandingRegister != null) {
            btnLandingRegister.setOnClickListener(v ->
                    startActivity(new Intent(LandingActivity.this, RegisterActivity.class)));
        }

        if (btnLangEn != null) btnLangEn.setOnClickListener(v -> switchLanguage("en"));
        if (btnLangSw != null) btnLangSw.setOnClickListener(v -> switchLanguage("sw"));
    }

    private void switchLanguage(String langCode) {
        if (!langCode.equals(LocaleHelper.getLanguage(this))) {
            LocaleHelper.setLanguageAndRestart(this, langCode);
        }
    }
}
