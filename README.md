# Wallpaper Manager

Native Android application built with Kotlin and Jetpack Compose for Firebase project `wallpaper-manager-5e7c2` and Android package `com.wallpapermanager.app`.

## Finalized architecture

Firebase Authentication and Cloud Firestore remain unchanged. The wallpaper image layer now uses ImageKit account `enpw7tyyr` with endpoint `https://ik.imagekit.io/enpw7tyyr`. New wallpaper documents store the ImageKit `imageUrl` and `fileId` fields.

The Android app never contains the ImageKit private key. Admin uploads first request a short-lived signature from the backend using the current Firebase ID token. The backend verifies `users/{uid}.isAdmin == true`, signs the request with the server-only ImageKit private key, and returns temporary upload authentication values. The Android app then uploads directly to ImageKit using the signed values and public key.

Images are compressed and downsampled before upload. ImageKit downloads are bounded to 15 MB, and wallpaper operations remain on `Dispatchers.IO`.

## ImageKit backend

The minimal backend is in `backend/`. It provides:

- `GET /api/imagekit/auth` — Firebase ID-token authenticated, Admin-only upload signature generation.
- `DELETE /api/imagekit/files/:fileId` — Firebase ID-token authenticated, Admin-only ImageKit deletion proxy.
- `GET /health` — health check.

Set `IMAGEKIT_PRIVATE_KEY`, `IMAGEKIT_PUBLIC_KEY`, and `FIREBASE_SERVICE_ACCOUNT_JSON` only as server environment secrets. Never commit them or put them in Firestore, Android resources, BuildConfig, or the APK. Deploy `backend/` to a persistent HTTPS server before production and set the Android `IMAGEKIT_AUTH_URL`/`IMAGEKIT_DELETE_URL` build fields to that server.

## Build

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_HOME=$PWD/sdk
./gradlew clean assembleDebug --no-daemon -Djdk.lang.Process.launchMechanism=VFORK
```

Firebase Auth and Firestore dependencies remain. Firebase Storage is no longer used by the Android image layer; OkHttp is used for ImageKit HTTP operations.

## Security and migration

The Firestore rules still protect user roles, published wallpaper visibility, VIP visibility, and favorites. The old `storage.rules` file is preserved as a legacy reference and is not used by the Android ImageKit layer. Existing wallpaper documents with Firebase Storage fields require a deliberate migration to ImageKit `imageUrl` and `fileId` before they are treated as migrated content. Do not delete legacy files without a separate review.
