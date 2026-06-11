# FCM Geo-Alerts — Model Accuracy & Deployment

## Model validation accuracy (`val_accuracy`)

| Model | File | Classes | val_accuracy | val_loss | Notes |
|-------|------|---------|----------------|----------|-------|
| **Stage 1 — Coffee Gate** | `coffee_gate.tflite` | Coffee / NotCoffee | **99.18%** (0.9918) | — | Binary gate from `dataset/train` + `dataset/validation` |
| **Stage 2 — Disease** | `coffee_disease_model.tflite` | 7 classes | **86.50%** (0.8650) | 0.331 | Healthy, Rust, BerryDisease, Wilt, LeafMiner, RootRot, IsNotCoffee |

Metadata files:
- `training/output/coffee_gate_metadata.json`
- `training/output/model_metadata.json`

Copy to app assets:
```powershell
Copy-Item training\output\coffee_gate.tflite app\src\main\assets\ -Force
Copy-Item training\output\coffee_disease_model.tflite app\src\main\assets\ -Force
```

---

## FCM Geo-Alerts (Cloud Functions)

When a farmer saves a **diseased scan with GPS**, Cloud Function `onDiseaseScanCreated`:
1. Finds other farmers within **50m, 100m, or 200m**
2. Sends **FCM push** to their devices (even if app is closed)
3. Writes `user_notifications` in Firestore

### Prerequisites

1. [Firebase CLI](https://firebase.google.com/docs/cli): `npm install -g firebase-tools`
2. Login: `firebase login`
3. Project: `coffee-diseases-detection` (see `.firebaserc`)

### Deploy

```powershell
cd "E:\Andriod Project\CoffeeDiseasesDetection\CoffeeDiseasesDetection"
cd functions
npm install
cd ..
firebase deploy --only functions
```

### Enable APIs (Firebase Console)

- Cloud Functions
- Cloud Messaging (FCM)
- Blaze plan (pay-as-you-go) required for Functions — free tier usually enough for testing

### Firestore rules (allow Functions admin — server uses Admin SDK, no rule change needed for Functions)

Ensure app users can read their notifications:
```
match /user_notifications/{id} {
  allow read: if request.auth != null && resource.data.targetUserId == request.auth.uid;
  allow create: if request.auth != null;
}
```

### How tokens are stored

App saves `fcmToken` on:
- Login / app start (`FcmTokenHelper`)
- Token refresh (`CoffeeMessagingService.onNewToken`)

Stored in:
- Firestore `users/{uid}.fcmToken`
- RTDB `users/{uid}.fcmToken`

### Test

1. Two devices/emulators logged in as different farmers
2. Enable GPS on both; update profile or scan once to set `lastLatitude` / `lastLongitude`
3. Device A: scan diseased leaf near Device B
4. Device B should receive push: *"Tahadhari: Ugonjwa karibu na eneo lako..."*

### Logs

```powershell
firebase functions:log
```
