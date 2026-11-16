# PDF Flow — APK Build & Download Guide

## Option 1: Download Directly Inside Google AI Studio
- Click the **Settings / Export (⚙️)** menu in Google AI Studio and select **Generate / Download APK** (or **AAB**).
- Note: `app/build/` is excluded by `app/.gitignore`, so `app-debug.apk` does not appear inside the Code file tree even though the app compiles on the server.

## Option 2: Auto-Build & Download via GitHub (From AI Studio **Code** View)
1. In Google AI Studio, open **Code** and click **Push to GitHub** (save to a new or existing GitHub repository).
2. Open that GitHub repository in your browser and click the **Actions** tab.
3. Open the latest **Build PDF Flow APK** workflow run.
4. Under **Artifacts**, click **PDF-Flow-APK** to download `PDF-Flow-debug.apk` directly to your phone or PC.

## Option 3: Build Locally After Downloading ZIP From **Code**
1. Click **Code → Download ZIP** in Google AI Studio and extract it.
2. Restore `debug.keystore` in the project root folder (required once because `.gitignore` excludes `debug.keystore`):
   - **macOS / Linux / Git Bash:**
     ```bash
     base64 -d debug.keystore.base64 > debug.keystore
     ```
   - **Windows PowerShell:**
     ```powershell
     [IO.File]::WriteAllBytes("debug.keystore", [Convert]::FromBase64String((Get-Content -Raw debug.keystore.base64)))
     ```
3. Open the folder in **Android Studio** and click **Build → Build Bundle(s) / APK(s) → Build APK(s)**, or run:
   ```bash
   gradle assembleDebug
   ```
4. Your installable APK will be generated at:
   `app/build/outputs/apk/debug/app-debug.apk`

---

## বাংলায় সংক্ষিপ্ত নির্দেশনা
1. **সবচেয়ে সহজ উপায় (GitHub থেকে সরাসরি APK ডাউনলোড):**
   - Google AI Studio-র **Code** অপশন থেকে **Push to GitHub** করুন।
   - GitHub রিপোজিটরির **Actions** ট্যাবে যান → **Build PDF Flow APK** ক্লিক করুন → নিচে **Artifacts** অংশ থেকে **`PDF-Flow-APK`** ডাউনলোড করুন।
2. **ZIP ডাউনলোড করে পিসিতে বিল্ড করলে:**
   - ZIP আনজিপ করে রুট ফোল্ডারে `debug.keystore.base64` থেকে `debug.keystore` তৈরি করুন (উপরের কমান্ড দিয়ে), তারপর Android Studio-তে **Build → Build APK(s)** বা টার্মিনালে `gradle assembleDebug` চালান।
