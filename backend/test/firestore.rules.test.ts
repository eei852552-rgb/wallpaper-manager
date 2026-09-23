import assert from "node:assert/strict";
import fs from "node:fs";
import test, { after, before } from "node:test";
import { initializeTestEnvironment, RulesTestEnvironment } from "@firebase/rules-unit-testing";
import { doc, setDoc, updateDoc } from "firebase/firestore";

const emulatorAvailable = Boolean(process.env.FIRESTORE_EMULATOR_HOST);

if (!emulatorAvailable) {
  test("Firestore rules tests (start the Firestore emulator to run)", { skip: "FIRESTORE_EMULATOR_HOST is not set" }, () => {});
} else {
  let env: RulesTestEnvironment;

  before(async () => {
    env = await initializeTestEnvironment({
      projectId: "wallpaper-manager-rules-test",
      firestore: { rules: fs.readFileSync("../../firestore.rules", "utf8") },
    });
  });

  after(async () => env?.cleanup());

  test("users cannot change their admin or VIP flags", async () => {
    const db = env.authenticatedContext("user-1", { email: "user@example.com" }).firestore();
    await assert.rejects(() => updateDoc(doc(db, "users/user-1"), { isAdmin: true }));
  });

  test("users can create only their own safe profile", async () => {
    const db = env.authenticatedContext("user-1").firestore();
    await assert.doesNotReject(() => setDoc(doc(db, "users/user-1"), {
      uid: "user-1", email: "user@example.com", isAdmin: false, isVip: false,
    }));
    await assert.rejects(() => setDoc(doc(db, "users/user-2"), {
      uid: "user-2", email: "other@example.com", isAdmin: false, isVip: false,
    }));
  });

  test("users cannot write another user’s favorites", async () => {
    const db = env.authenticatedContext("user-1").firestore();
    await assert.rejects(() => setDoc(doc(db, "users/user-2/favorites/wallpaper-1"), {
      wallpaperId: "wallpaper-1",
    }));
  });
}
