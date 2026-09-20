# ImageKit authentication backend

This service generates short-lived ImageKit upload signatures only after verifying a Firebase ID token and the caller's `users/{uid}.isAdmin == true` Firestore document. It also provides an admin-only ImageKit delete proxy.

The ImageKit private key and Firebase service-account JSON must be injected as hosting-platform environment secrets. Never commit `.env`, service-account files, PEM files, or private keys. The Android app contains only the ImageKit endpoint/public key and calls `GET /api/imagekit/auth` with a Firebase ID token.

## Required environment variables

```text
PORT=provided by hosting platform
IMAGEKIT_PUBLIC_KEY=public_/gytdHMHubFq6eR9gsR4HPw2Tys=
IMAGEKIT_PRIVATE_KEY=<server-only ImageKit private key>
FIREBASE_SERVICE_ACCOUNT_JSON=<server-only Firebase Admin service account JSON>
```

## Deployment commands

From the `backend/` directory:

- **Build Command:** `pnpm install --frozen-lockfile && pnpm run build`
- **Start Command:** `pnpm start`

The compiled production entrypoint is `dist/server.js`. The server listens on `process.env.PORT` and binds to `0.0.0.0`.

## Endpoints

- `GET /health` returns HTTP 200 and `{ "ok": true, "service": "imagekit-auth" }` when the service is running.
- `GET /api/imagekit/auth` requires `Authorization: Bearer <Firebase ID token>` and an Admin profile in Firestore.
- `DELETE /api/imagekit/files/:fileId` requires the same Admin authorization.

## Local verification

```bash
pnpm install --frozen-lockfile
pnpm run build
PORT=8787 IMAGEKIT_PUBLIC_KEY='public_/gytdHMHubFq6eR9gsR4HPw2Tys=' \
IMAGEKIT_PRIVATE_KEY='server-secret' \
FIREBASE_SERVICE_ACCOUNT_JSON="$FIREBASE_SERVICE_ACCOUNT_JSON" \
pnpm start
curl -i http://127.0.0.1:8787/health
```

Local sandbox hosting is suitable for verification only. Deploy this service to a persistent HTTPS backend before connecting the production Android build. Do not deploy until the hosting platform secrets have been configured.
