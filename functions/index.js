/**
 * Cloud Functions — FCM push for geographical disease alerts.
 *
 * Triggers when a diseased scan is saved to Firestore `scan_history`.
 * Finds nearby farmers (50m / 100m / 200m) and sends push via FCM.
 */
const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getAuth } = require("firebase-admin/auth");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();

const db = getFirestore();
const DISEASE_CLASSES = new Set([
  "Rust",
  "BerryDisease",
  "Wilt",
  "LeafMiner",
  "RootRot",
]);
const RADIUS_METERS = [50, 100, 200];

function distanceMeters(lat1, lon1, lat2, lon2) {
  const R = 6371000;
  const dLat = ((lat2 - lat1) * Math.PI) / 180;
  const dLon = ((lon2 - lon1) * Math.PI) / 180;
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos((lat1 * Math.PI) / 180) *
      Math.cos((lat2 * Math.PI) / 180) *
      Math.sin(dLon / 2) ** 2;
  return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

function normalizeRole(role) {
  const r = String(role || "farmer").trim().toLowerCase();
  if (r === "super_admin") return "superadmin";
  return r;
}

function canAdminResetTarget(actorRole, targetRole) {
  const actor = normalizeRole(actorRole);
  const target = normalizeRole(targetRole);
  const privileged = new Set(["system_admin", "superadmin", "main", "it"]);
  if (privileged.has(actor)) return true;
  if (actor === "admin") return target === "farmer";
  return false;
}

function isDiseasedScan(data) {
  if (!data) return false;
  if (data.isHealthy === true) return false;
  const d = String(data.disease || "").trim();
  if (!d || d === "Healthy" || d === "IsNotCoffee") return false;
  return DISEASE_CLASSES.has(d) || (d !== "Healthy" && d !== "IsNotCoffee");
}

async function getFarmerTokens() {
  const snap = await db.collection("users").limit(800).get();
  const farmers = [];
  snap.forEach((doc) => {
    const u = doc.data();
    const role = String(u.role || "farmer").toLowerCase();
    if (role.includes("admin")) return;
    const lat = u.lastLatitude ?? u.latitude;
    const lng = u.lastLongitude ?? u.longitude;
    if (lat == null || lng == null) return;
    const token = u.fcmToken;
    if (!token) return;
    farmers.push({
      uid: doc.id,
      lat: Number(lat),
      lng: Number(lng),
      token: String(token),
    });
  });
  return farmers;
}

async function sendGeoAlerts(scanId, scan) {
  const lat = Number(scan.latitude);
  const lng = Number(scan.longitude);
  const sourceUid = String(scan.userId || "");
  const disease = String(scan.disease || "Unknown");
  const diseaseName = String(scan.diseaseName || disease);

  if (!lat || !lng) {
    console.log("Scan has no GPS, skipping geo FCM", scanId);
    return { sent: 0 };
  }

  const farmers = await getFarmerTokens();
  const notified = new Set();
  let sent = 0;

  for (const farmer of farmers) {
    if (farmer.uid === sourceUid || notified.has(farmer.uid)) continue;

    const dist = distanceMeters(lat, lng, farmer.lat, farmer.lng);
    let matchedRadius = -1;
    for (const r of RADIUS_METERS) {
      if (dist <= r) {
        matchedRadius = r;
        break;
      }
    }
    if (matchedRadius < 0) continue;

    const title = "Tahadhari: Ugonjwa karibu na eneo lako";
    const body = `Tahadhari: Ugonjwa wa kahawa (${diseaseName}) umeonekana ndani ya mita ${matchedRadius}. Tafadhali scan mazao yako mapema.`;

    try {
      await getMessaging().send({
        token: farmer.token,
        notification: { title, body },
        data: {
          type: "geo_alert",
          scanId: String(scanId),
          disease,
          radiusMeters: String(matchedRadius),
          distanceMeters: String(Math.round(dist)),
        },
        android: {
          priority: "high",
          notification: { channelId: "coffee_disease_notifications" },
        },
      });

      await db.collection("user_notifications").add({
        targetUserId: farmer.uid,
        title,
        body,
        type: "geo_alert",
        scanId,
        disease,
        radiusMeters: matchedRadius,
        distanceMeters: Math.round(dist),
        read: false,
        createdAtMs: Date.now(),
        timestamp: FieldValue.serverTimestamp(),
        deliveredBy: "cloud_function",
      });

      notified.add(farmer.uid);
      sent += 1;
    } catch (err) {
      console.warn("FCM failed for", farmer.uid, err.message);
      if (
        err.code === "messaging/registration-token-not-registered" ||
        err.code === "messaging/invalid-registration-token"
      ) {
        await db.collection("users").doc(farmer.uid).update({
          fcmToken: FieldValue.delete(),
        });
      }
    }
  }

  console.log(`Geo FCM sent to ${sent} farmers for scan ${scanId}`);
  return { sent };
}

/**
 * Main trigger: new scan in scan_history
 */
exports.onDiseaseScanCreated = onDocumentCreated(
  {
    document: "scan_history/{scanId}",
    region: "us-central1",
  },
  async (event) => {
    const scan = event.data?.data();
    const scanId = event.params.scanId;
    if (!isDiseasedScan(scan)) {
      return null;
    }
    return sendGeoAlerts(scanId, scan);
  }
);

/**
 * Optional: also handle explicit user_notifications (geo_alert) if written from app
 */
exports.onUserNotificationCreated = onDocumentCreated(
  {
    document: "user_notifications/{notifId}",
    region: "us-central1",
  },
  async (event) => {
    const n = event.data?.data();
    if (!n || n.type !== "geo_alert" || n.deliveredBy === "cloud_function") {
      return null;
    }
    const targetUid = n.targetUserId;
    if (!targetUid) return null;

    const userDoc = await db.collection("users").doc(targetUid).get();
    const token = userDoc.exists ? userDoc.data().fcmToken : null;
    if (!token) return null;

    const title = n.title || "Coffee Disease Alert";
    const body = n.body || "";

    try {
      await getMessaging().send({
        token,
        notification: { title, body },
        data: { type: "geo_alert", scanId: String(n.scanId || "") },
        android: { priority: "high" },
      });
      await event.data.ref.update({ deliveredBy: "cloud_function", fcmSentAt: Date.now() });
    } catch (e) {
      console.warn("onUserNotificationCreated FCM error", e.message);
    }
    return null;
  }
);

/**
 * Admin-only password reset (Firebase Auth Admin SDK).
 * Stores lastSetPassword in Firestore so admin can view previous password on next reset.
 */
exports.adminResetPassword = onCall(
  { region: "us-central1" },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Sign in required.");
    }

    const callerUid = request.auth.uid;
    const targetUid = String(request.data?.targetUid || "").trim();
    const newPassword = String(request.data?.newPassword || "");

    if (!targetUid) {
      throw new HttpsError("invalid-argument", "targetUid is required.");
    }
    if (!newPassword || newPassword.length < 6) {
      throw new HttpsError("invalid-argument", "Password must be at least 6 characters.");
    }

    const callerDoc = await db.collection("users").doc(callerUid).get();
    if (!callerDoc.exists) {
      throw new HttpsError("permission-denied", "Caller profile not found.");
    }
    const callerRole = callerDoc.data().role;

    const targetDoc = await db.collection("users").doc(targetUid).get();
    if (!targetDoc.exists) {
      throw new HttpsError("not-found", "User not found.");
    }
    const targetRole = targetDoc.data().role;

    if (!canAdminResetTarget(callerRole, targetRole)) {
      throw new HttpsError(
        "permission-denied",
        "You are not allowed to reset this user's password."
      );
    }

    const oldPassword = targetDoc.data().lastSetPassword || null;

    await getAuth().updateUser(targetUid, { password: newPassword });

    await db.collection("users").doc(targetUid).update({
      lastSetPassword: newPassword,
      passwordResetBy: callerUid,
      passwordResetAt: FieldValue.serverTimestamp(),
    });

    const targetName = targetDoc.data().name || targetUid;
    await db.collection("admin_activity_logs").add({
      action: "Password Reset",
      detail: `Reset password for ${targetName}`,
      timestamp: FieldValue.serverTimestamp(),
      adminUid: callerUid,
      targetUid,
    });

    return { success: true, oldPassword };
  }
);

/**
 * Admin-only password reset (Firebase Auth Admin SDK).
 * Stores lastSetPassword in Firestore so admin can view previous password on next reset.
 */
exports.adminResetPassword = onCall(
  { region: "us-central1" },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Sign in required.");
    }

    const callerUid = request.auth.uid;
    const targetUid = String(request.data?.targetUid || "").trim();
    const newPassword = String(request.data?.newPassword || "");

    if (!targetUid) {
      throw new HttpsError("invalid-argument", "targetUid is required.");
    }
    if (!newPassword || newPassword.length < 6) {
      throw new HttpsError("invalid-argument", "Password must be at least 6 characters.");
    }

    const callerDoc = await db.collection("users").doc(callerUid).get();
    if (!callerDoc.exists) {
      throw new HttpsError("permission-denied", "Caller profile not found.");
    }
    const callerRole = callerDoc.data().role;

    const targetDoc = await db.collection("users").doc(targetUid).get();
    if (!targetDoc.exists) {
      throw new HttpsError("not-found", "User not found.");
    }
    const targetRole = targetDoc.data().role;

    if (!canAdminResetTarget(callerRole, targetRole)) {
      throw new HttpsError(
        "permission-denied",
        "You are not allowed to reset this user's password."
      );
    }

    const oldPassword = targetDoc.data().lastSetPassword || null;

    await getAuth().updateUser(targetUid, { password: newPassword });

    await db.collection("users").doc(targetUid).update({
      lastSetPassword: newPassword,
      passwordResetBy: callerUid,
      passwordResetAt: FieldValue.serverTimestamp(),
    });

    const targetName = targetDoc.data().name || targetUid;
    await db.collection("admin_activity_logs").add({
      action: "Password Reset",
      detail: `Reset password for ${targetName}`,
      timestamp: FieldValue.serverTimestamp(),
      adminUid: callerUid,
      targetUid,
    });

    return { success: true, oldPassword };
  }
);
