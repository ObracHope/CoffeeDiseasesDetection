/**
 * Sync values-sw/strings.xml with values/strings.xml — all keys present in Swahili.
 */
const fs = require("fs");
const path = require("path");

const EN_PATH = path.join(__dirname, "../app/src/main/res/values/strings.xml");
const SW_PATH = path.join(__dirname, "../app/src/main/res/values-sw/strings.xml");

const EN_SW_MAP = {
  "Coffee Disease Detection": "Uchunguzi wa Magonjwa ya Kahawa",
  "Admin": "Msimamizi",
  "Farmer": "Mkulima",
  "Settings": "Mipangilio",
  "Logout": "Toka",
  "Login": "Ingia",
  "Register": "Jisajili",
  "Save": "Hifadhi",
  "Cancel": "Ghairi",
  "Delete": "Futa",
  "Edit": "Hariri",
  "Remove": "Ondoa",
  "Search": "Tafuta",
  "Export": "Hamisha",
  "Report": "Ripoti",
  "Reports": "Ripoti",
  "Dashboard": "Dashibodi",
  "Home": "Nyumbani",
  "History": "Historia",
  "Profile": "Wasifu",
  "Notifications": "Arifa",
  "Password": "Nenosiri",
  "Email": "Barua Pepe",
  "Phone": "Simu",
  "Name": "Jina",
  "Region": "Mkoa",
  "District": "Wilaya",
  "Ward": "Kata",
  "Male": "Mwanaume",
  "Female": "Mwanamke",
  "Healthy": "Afya",
  "Disease": "Ugonjwa",
  "Diseases": "Magonjwa",
  "Scan": "Skani",
  "Upload": "Pakia",
  "Camera": "Kamera",
  "Total": "Jumla",
  "Today": "Leo",
  "Yes": "Ndiyo",
  "No": "Hapana",
  "OK": "Sawa",
  "Error": "Hitilafu",
  "Success": "Imefanikiwa",
  "Loading": "Inapakia",
  "Required": "Inahitajika",
  "Back": "Rudi",
  "Continue": "Endelea",
  "Submit": "Tuma",
  "View": "Angalia",
  "Details": "Maelezo",
  "All": "Zote",
  "None": "Hakuna",
  "Low": "Chini",
  "High": "Juu",
  "Medium": "Wastani",
  "Risk": "Hatari",
  "Status": "Hali",
  "English": "Kiingereza",
  "Swahili": "Kiswahili",
  "Light": "Mwangaza",
  "Dark": "Giza",
  "Theme": "Muonekano",
  "Language": "Lugha",
  "Reset Password": "Weka Upya Nenosiri",
  "Forgot Password": "Umesahau Nenosiri",
  "Change Password": "Badilisha Nenosiri",
  "Manage Users": "Dhibiti Watumiaji",
  "Activity Log": "Kumbukumbu za Shughuli",
  "Overview": "Muhtasari",
  "Statistics": "Takwimu",
  "Treatment": "Matibabu",
  "Symptoms": "Dalili",
  "Confidence": "Uhakika",
  "Location": "Eneo",
  "Date": "Tarehe",
  "Time": "Muda",
  "User": "Mtumiaji",
  "Users": "Watumiaji",
  "Farmers": "Wakulima",
  "Technician": "Mtaalamu",
  "System Admin": "Msimamizi Mkuu",
  "Continue with Google": "Endelea na Google",
  "Register New User": "Sajili Mtumiaji Mpya",
  "Create Account": "Unda Akaunti",
  "Select Role": "Chagua Jukumu",
  "First Name": "Jina la Kwanza",
  "Last Name": "Jina la Mwisho",
  "Repeat Password": "Rudia Nenosiri",
  "I agree to the terms and conditions": "Nakubali sheria na masharti",
  "Back to Dashboard": "Rudi kwenye Dashibodi",
  "Back to Login": "Rudi kwenye Kuingia",
  "Send Reset Link": "Tuma Kiungo cha Kuweka Upya",
  "Upload Image": "Pakia Picha",
  "Capture Leaf": "Piga Picha ya Jani",
  "Detect Disease": "Gundua Ugonjwa",
  "View Treatment Guide": "Angalia Mwongozo wa Matibabu",
  "Help & Tips": "Msaada na Vidokezo",
  "About Us": "Kutuhusu",
  "Privacy Policy": "Sera ya Faragha",
  "Terms and Conditions": "Sheria na Masharti",
};

function parseStrings(xml) {
  const map = new Map();
  const re = /<string\s+name="([^"]+)"([^>]*)>([\s\S]*?)<\/string>/g;
  let m;
  while ((m = re.exec(xml)) !== null) {
    map.set(m[1], m[3].replace(/\\'/g, "'").replace(/\\"/g, '"'));
  }
  return map;
}

function translateToSw(text) {
  if (!text) return text;
  if (EN_SW_MAP[text]) return EN_SW_MAP[text];
  let out = text;
  for (const [en, sw] of Object.entries(EN_SW_MAP)) {
    out = out.split(en).join(sw);
  }
  return out;
}

function escapeXml(s) {
  return s
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/'/g, "\\'")
    .replace(/"/g, '\\"');
}

const enMap = parseStrings(fs.readFileSync(EN_PATH, "utf8"));
const swMap = parseStrings(fs.readFileSync(SW_PATH, "utf8"));

for (const [key, enVal] of enMap) {
  if (!swMap.has(key)) {
    swMap.set(key, translateToSw(enVal));
  }
}

let out = '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n';
for (const [key, val] of swMap) {
  if (!enMap.has(key)) continue;
  out += `    <string name="${key}">${escapeXml(val)}</string>\n`;
}
out += "</resources>\n";
fs.writeFileSync(SW_PATH, out, "utf8");
console.log("Synced", swMap.size, "Swahili strings");
