import crypto from "node:crypto";
import express from "express";
import dotenv from "dotenv";
import admin from "firebase-admin";

// Private values are server-only environment variables. Never bundle them into Android.
dotenv.config();
const app = express();
app.use(express.json({ limit: "32kb" }));

const required = ["IMAGEKIT_PRIVATE_KEY", "IMAGEKIT_PUBLIC_KEY", "FIREBASE_SERVICE_ACCOUNT_JSON"];
for (const name of required) if (!process.env[name]) throw new Error(`${name} is required`);
const serviceAccount = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT_JSON!);
admin.initializeApp({ credential: admin.credential.cert(serviceAccount) });
const db = admin.firestore();
const imageKitPrivateKey = process.env.IMAGEKIT_PRIVATE_KEY!;
const imageKitPublicKey = process.env.IMAGEKIT_PUBLIC_KEY!;

async function requireAdmin(req: express.Request): Promise<string> {
  const header = req.header("authorization") || "";
  if (!header.startsWith("Bearer ")) throw new Error("Missing Firebase ID token");
  const decoded = await admin.auth().verifyIdToken(header.slice("Bearer ".length));
  const snap = await db.doc(`users/${decoded.uid}`).get();
  if (snap.data()?.isAdmin !== true) throw new Error("Admin permission required");
  return decoded.uid;
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
    res.status(401).json({ error: error instanceof Error ? error.message : "Unauthorized" });
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
    if (!response.ok) return res.status(response.status).json({ error: "ImageKit delete failed" });
    res.status(204).end();
  } catch (error) {
    res.status(401).json({ error: error instanceof Error ? error.message : "Unauthorized" });
  }
});

const port = Number(process.env.PORT || 8787);
app.listen(port, "0.0.0.0", () => console.log(`ImageKit backend listening on ${port}`));
