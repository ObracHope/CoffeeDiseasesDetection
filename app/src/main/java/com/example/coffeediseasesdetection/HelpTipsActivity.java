package com.example.coffeediseasesdetection;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

public class HelpTipsActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help_tips);
        setTitle(R.string.farming_tips);
    }
}

