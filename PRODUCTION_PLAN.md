# Wallpaper Manager ImageKit production plan

## Implemented

Firebase Authentication and Firestore remain unchanged. Firebase Storage calls were removed from the Android wallpaper image layer. The Android client compresses gallery images, requests short-lived ImageKit upload authentication from `backend/`, uploads with ImageKit's public key and signed fields, and stores `imageUrl` plus `fileId` in the existing Firestore wallpaper document.

## Required production steps

1. Deploy `backend/` to a persistent HTTPS server. The sandbox can build and locally verify this service but is not a persistent production hosting target.
2. Inject these server-only secrets through the deployment platform's secret manager:

   ```text
   IMAGEKIT_PRIVATE_KEY=<ImageKit private key>
   IMAGEKIT_PUBLIC_KEY=public_/gytdHMHubFq6eR9gsR4HPw2Tys=
   FIREBASE_SERVICE_ACCOUNT_JSON=<Firebase Admin service account JSON>
   ```

   Never put the private key in Android, Git, Firestore, `.env` committed files, or APK resources.
3. Configure the Android build fields `IMAGEKIT_AUTH_URL` and `IMAGEKIT_DELETE_URL` with the deployed HTTPS backend URLs. Do not leave `YOUR_BACKEND_HOST` in a production build.
4. Keep Email/Password Firebase Authentication enabled. Create the first Admin user through Firebase Authentication and set only that user's `users/{uid}.isAdmin` field from a trusted administrative channel. Normal Android clients can create profiles only with both privilege flags false.
5. Deploy the unchanged Firestore rules. The legacy `storage.rules` file is preserved for rollback/reference only and must not be treated as the active ImageKit authorization layer.
6. Migrate legacy wallpaper documents deliberately: upload each source image to ImageKit, write the returned `imageUrl` and `fileId` into the existing Firestore document, verify the app can load it, and only then consider retiring the old Firebase Storage object. Do not bulk-delete without a verified migration report.
7. Build and install:

   ```bash
   export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
   export ANDROID_HOME=$PWD/sdk
   ./gradlew clean assembleDebug --no-daemon -Djdk.lang.Process.launchMechanism=VFORK
   ```

8. Test with a normal Firebase user: free wallpapers and favorites work; VIP metadata is not returned by the free query; upload/delete and role changes fail.
9. Test with an Admin Firebase user: gallery selection, backend signature request, ImageKit upload, Firestore `imageUrl`/`fileId` write, published refresh, delete, download, and Home/Lock/Both wallpaper setting work.
10. Test backend failures: missing token, invalid token, non-admin token, expired/invalid ImageKit signature, oversized/non-image upload, and ImageKit API failure. All must fail closed without returning secrets.
11. Add rate limiting, CORS restrictions for any web callers, request logging without tokens/secrets, HTTPS-only transport, and Firebase App Check before broad release.
