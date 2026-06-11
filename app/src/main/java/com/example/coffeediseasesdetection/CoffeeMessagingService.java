package com.example.coffeediseasesdetection;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class CoffeeMessagingService extends FirebaseMessagingService {

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            FcmTokenHelper.saveToken(user.getUid(), token);
        }
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        String title = "Coffee Update";
        String body = "You have a new update.";
        if (remoteMessage.getNotification() != null) {
            if (remoteMessage.getNotification().getTitle() != null) {
                title = remoteMessage.getNotification().getTitle();
            }
            if (remoteMessage.getNotification().getBody() != null) {
                body = remoteMessage.getNotification().getBody();
            }
        }
        if (remoteMessage.getData().size() > 0) {
            String type = remoteMessage.getData().get("type");
            if ("geo_alert".equals(type) && remoteMessage.getData().get("title") != null) {
                title = remoteMessage.getData().get("title");
            }
            if ("geo_alert".equals(type) && remoteMessage.getData().get("body") != null) {
                body = remoteMessage.getData().get("body");
            }
        }
        NotificationHelper.showNotification(this, title, body);
    }
}
