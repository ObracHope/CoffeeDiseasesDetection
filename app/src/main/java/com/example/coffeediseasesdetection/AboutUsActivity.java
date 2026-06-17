package com.example.coffeediseasesdetection;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

public class AboutUsActivity extends BaseActivity {

    private static final String GITHUB_MOBILE = "https://github.com/ObracHope/CoffeeDiseasesDetection";
    private static final String GITHUB_WEB = "https://github.com/ObracHope/coffee-disease-detection-web";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about_us);
        findViewById(R.id.ivBackButton).setOnClickListener(v -> finish());
        bindGithubLink(R.id.tvGithubMobile, GITHUB_MOBILE);
        bindGithubLink(R.id.tvGithubWeb, GITHUB_WEB);
    }

    private void bindGithubLink(int viewId, String url) {
        findViewById(viewId).setOnClickListener(v ->
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))));
    }
}
