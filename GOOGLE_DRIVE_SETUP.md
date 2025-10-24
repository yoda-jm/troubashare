# Google Drive Setup Guide for TroubaShare

This guide will help you configure Google Cloud Console to enable Google Drive authentication in TroubaShare.

## Prerequisites

- TroubaShare app compiled and installed on your Android device
- Google account with access to Google Cloud Console
- Android device or emulator for testing

## Step 1: Create Google Cloud Project

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Click "Select a project" dropdown at the top
3. Click "New Project"
4. Enter project name: `TroubaShare`
5. Click "Create"

## Step 2: Enable Google Drive API

1. In the Google Cloud Console, navigate to "APIs & Services" > "Library"
2. Search for "Google Drive API"
3. Click on "Google Drive API" result
4. Click "Enable"

## Step 3: Get Your App's SHA-1 Fingerprint

### For Debug Build (Development)

Run this command in your project directory:

```bash
cd /home/yoda/AndroidStudioProjects/TroubaShare
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
```

Look for the SHA1 fingerprint in the output, it will look like:
```
SHA1: AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD
```

### For Release Build (Production)

If you have a release keystore:

```bash
keytool -list -v -keystore /path/to/your/release.keystore -alias your_key_alias
```

## Step 4: Create OAuth 2.0 Client ID

1. In Google Cloud Console, go to "APIs & Services" > "Credentials"
2. Click "+ CREATE CREDENTIALS" > "OAuth client ID"
3. If prompted to configure OAuth consent screen:
   - Choose "External" user type
   - Fill in required fields:
     - App name: `TroubaShare`
     - User support email: your email
     - Developer contact: your email
   - Click "Save and Continue" through the steps
4. Select "Android" as application type
5. Fill in the form:
   - **Name**: `TroubaShare Android Client`
   - **Package name**: `com.troubashare`
   - **SHA-1 certificate fingerprint**: Paste the SHA1 from Step 3

6. Click "Create"

## Step 5: Configure OAuth Consent Screen

1. Go to "APIs & Services" > "OAuth consent screen"
2. Add these scopes:
   - `https://www.googleapis.com/auth/drive`
   - `https://www.googleapis.com/auth/drive.file`
3. Add test users (your email address) if the app is in testing mode
4. Save the configuration

## Step 6: Test the Configuration

1. Make sure your Android device/emulator has the latest TroubaShare app installed
2. Open TroubaShare
3. Go to Settings > Cloud Sync
4. Click "Connect to Google Drive"
5. You should see Google account selection dialog
6. Complete the authentication flow

## Troubleshooting

### Error Code 10 (DEVELOPER_ERROR)

This is the error you're currently experiencing. It means:

- **Package name mismatch**: Verify the package name in Google Cloud Console exactly matches `com.troubashare`
- **SHA-1 fingerprint mismatch**: The SHA-1 fingerprint registered doesn't match your app's signing certificate
- **Missing configuration**: OAuth client ID not properly configured

### Error Code 12 (SIGN_IN_CANCELLED)

User cancelled the sign-in flow.

### Error Code 7 (NETWORK_ERROR)

Network connectivity issues.

## Verification Steps

After completing the setup:

1. Check that Google Drive API is enabled in your project
2. Verify OAuth 2.0 client ID exists with correct package name and SHA-1
3. Confirm OAuth consent screen is configured with Drive scopes
4. Test authentication flow in the app

## Additional Notes

- The debug keystore is automatically created by Android Studio
- For production releases, you'll need to add your release keystore's SHA-1 fingerprint
- You can have multiple SHA-1 fingerprints for the same OAuth client (debug + release)
- Changes in Google Cloud Console may take a few minutes to propagate

## Support

If authentication still fails after following this guide:

1. Double-check all package names and SHA-1 fingerprints
2. Try creating a new OAuth client ID
3. Ensure you're using the correct Google account for both console and device testing
4. Check Google Cloud Console logs for additional error details

---

**Important**: Keep your OAuth client ID and any credentials secure and never commit them to version control.