import crypto from "node:crypto";
import express from "express";
import dotenv from "dotenv";
import admin from "firebase-admin";
import { HttpError, toClientError } from "./http-utils.js";

dotenv.config();

const required = ["IMAGEKIT_PRIVATE_KEY", "IMAGEKIT_PUBLIC_KEY", "FIREBASE_SERVICE_ACCOUNT_JSON"];
for (const name of required) if (!process.env[name]) throw new Error(`${name} is required`);

const serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT_JSON!);
admin.initializeApp({ credential: admin.credential.cert(serviceAccount) });
const db = admin.firestore();
const imageKitPrivateKey = process.env.IMAGEKIT_PRIVATE_KEY!;
const imageKitPublicKey = process.env.IMAGEKIT_PUBLIC_KEY!;
const app = express();
app.set("trust proxy", true);
app.use(express.json({ limit: "32kb" }));

const RATE_WINDOW_MS = 60_000;
const RATE_LIMIT = 30;
const rateState = new Map<string, { count: number; resetAt: number }>();
app.use((req, res, next) => {
  if (req.path === "/health") return next();
  const now = Date.now();
  const key = `${req.ip}:${req.path}`;
  const current = rateState.get(key);
  if (!current || current.resetAt <= now) rateState.set(key, { count: 1, resetAt: now + RATE_WINDOW_MS });
  else if (++current.count > RATE_LIMIT) return res.status(429).json({ error: "Too many requests" });
  return next();
});

app.use((req, res, next) => {
  if (process.env.ENFORCE_HTTPS !== "true") return next();
  const forwardedProtocol = req.header("x-forwarded-proto");
  if (req.secure || forwardedProtocol === "https") return next();
  return res.status(400).json({ error: "HTTPS is required" });
});

async function requireAppCheck(req: express.Request): Promise<void> {
  if (process.env.REQUIRE_APP_CHECK !== "true") return;
  const token = req.header("x-firebase-appcheck");
  if (!token) throw new HttpError(401, "App Check required");
  try {
    await admin.appCheck().verifyToken(token);
  } catch {
    throw new HttpError(401, "Invalid App Check token");
  }
}

async function requireAdmin(req: express.Request): Promise<string> {
  await requireAppCheck(req);
  const header = req.header("authorization") || "";
  if (!header.startsWith("Bearer ")) throw new HttpError(401, "Authentication required");
  let decoded: admin.auth.DecodedIdToken;
  try {
    decoded = await admin.auth().verifyIdToken(header.slice("Bearer ".length));
  } catch {
    throw new HttpError(401, "Authentication required");
  }
  const snap = await db.doc(`users/${decoded.uid}`).get();
  if (snap.data()?.isAdmin !== true) throw new HttpError(403, "Admin permission required");
  return decoded.uid;
}

function sendError(res: express.Response, error: unknown): void {
  const clientError = toClientError(error);
  if (clientError.status >= 500) console.error("Request failed", { status: clientError.status });
  res.status(clientError.status).json({ error: clientError.message });
}

app.get("/health", (_req, res) => res.json({ ok: true, service: "imagekit-auth" }));
app.get("/api/imagekit/auth", async (req, res) => {
  try {
    await requireAdmin(req);
    const expire = Math.floor(Date.now() / 1000) + 300;
    const token = crypto.randomBytes(24).toString("hex");
    const signature = crypto.createHmac("sha1", imageKitPrivateKey).update(token + expire).digest("hex");
    res.json({ token, expire, signature, publicKey: imageKitPublicKey });
  } catch (error) {
    sendError(res, error);
  }
});

app.delete("/api/imagekit/files/:fileId", async (req, res) => {
  try {
    await requireAdmin(req);
    const fileId = encodeURIComponent(req.params.fileId);
    const response = await fetch(`https://api.imagekit.io/v1/files/${fileId}`, {
      method: "DELETE",
      headers: { Authorization: `Basic ${Buffer.from(`${imageKitPrivateKey}:`).toString("base64")}` },
    });
    if (!response.ok) throw new HttpError(502, "ImageKit delete failed");
    res.status(204).end();
  } catch (error) {
    sendError(res, error);
  }
});

const port = Number(process.env.PORT || 8787);
if (process.env.NODE_ENV !== "test") {
  app.listen(port, "0.0.0.0", () => console.log(`ImageKit backend listening on ${port}`));
}

export { app, requireAdmin };
