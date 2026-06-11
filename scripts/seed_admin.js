/**
 * One-time setup: create demo admin in Firebase Auth + Firestore + RTDB.
 *
 * Usage (from project root):
 *   cd functions && npm install
 *   cd .. && node scripts/seed_admin.js
 *
 * Requires: GOOGLE_APPLICATION_CREDENTIALS pointing to a service account JSON,
 * or run: firebase login && firebase use coffee-diseases-detection
 */
const path = require("path");
const admin = require(path.join(__dirname, "..", "functions", "node_modules", "firebase-admin"));

const DEMO_EMAIL = "admin@coffeediseases.com";
const DEMO_PASSWORD = "Admin@123";
const RTDB_URL = "https://coffee-diseases-detection-default-rtdb.firebaseio.com";

const PROJECT_ID = "coffee-diseases-detection";

if (!admin.apps.length) {
  admin.initializeApp({
    projectId: PROJECT_ID,
    databaseURL: RTDB_URL,
  });
}

const auth = admin.auth();
const db = admin.firestore();
const rtdb = admin.database();

async function seedAdmin() {
  let user;
  try {
    user = await auth.getUserByEmail(DEMO_EMAIL);
    console.log("Admin user already exists:", user.uid);
    await auth.updateUser(user.uid, { password: DEMO_PASSWORD, displayName: "Admin" });
    console.log("Password reset to demo value.");
  } catch (e) {
    if (e.code !== "auth/user-not-found") throw e;
    user = await auth.createUser({
      email: DEMO_EMAIL,
      password: DEMO_PASSWORD,
      displayName: "Admin",
      emailVerified: true,
    });
    console.log("Created admin user:", user.uid);
  }

  const profile = {
    uid: user.uid,
    email: DEMO_EMAIL,
    name: "Admin",
    username: "admin",
    role: "admin",
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  };

  await db.collection("users").doc(user.uid).set(profile, { merge: true });
  await rtdb.ref(`users/${user.uid}`).set(profile);
  console.log("Admin profile written to Firestore + RTDB.");
  console.log("\nDemo admin login:");
  console.log("  Email:", DEMO_EMAIL);
  console.log("  Password:", DEMO_PASSWORD);
}

seedAdmin()
  .then(() => process.exit(0))
  .catch((err) => {
    console.error("Seed failed:", err.message || err);
    process.exit(1);
  });
