const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

const nowTs = () => new Date(Date.now() - 2000);

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const DATABASE_ID = "ai-studio-android-pdfflow-4e87886e-c604-4ecf-be9e-d6a5cb276ce5";
const ALICE_UID = "alice_pdfflow_123";
const BOB_UID = "bob_pdfflow_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function getDb(context) {
  return context.firestore({ databaseId: DATABASE_ID });
}

test("Unauthenticated user: cannot read or write any user documents", async () => {
  const unauthDb = getDb(testEnv.unauthenticatedContext());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("documents").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("profile").doc("main").set({
      userId: ALICE_UID,
      displayName: "Unauth",
      planTier: "free",
      authProvider: "google",
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );
});

test("Owner can create and read their own profile, settings, document, favorite, and backup", async () => {
  const aliceDb = getDb(testEnv.authenticatedContext(ALICE_UID));

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("profile").doc("main").set({
      userId: ALICE_UID,
      displayName: "Alice Flow",
      email: "alice@example.com",
      planTier: "free",
      authProvider: "google",
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("settings").doc("preferences").set({
      userId: ALICE_UID,
      languageCode: "en",
      themeMode: "system",
      defaultViewerMode: "continuous",
      defaultPageLayout: "fit_width",
      autoBackupEnabled: false,
      appLockEnabled: false,
      biometricEnabled: false,
      notificationsEnabled: true,
      downloadSubfolder: "PDF Flow",
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("documents").doc("doc_1").set({
      userId: ALICE_UID,
      title: "Annual Report",
      fileName: "annual_report.pdf",
      mimeType: "application/pdf",
      sizeBytes: 204800,
      pageCount: 12,
      folderName: "Work",
      isFavorite: false,
      isTrashed: false,
      isEncrypted: false,
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("favorites").doc("fav_1").set({
      userId: ALICE_UID,
      documentId: "doc_1",
      title: "Annual Report",
      fileName: "annual_report.pdf",
      sizeBytes: 204800,
      pageCount: 12,
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("backups").doc("bak_1").set({
      userId: ALICE_UID,
      backupName: "Manual Backup",
      documentCount: 1,
      totalBytes: 204800,
      status: "in_progress",
      deviceName: "Pixel 8",
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("documents").get()
  );
});

test("Cross-user isolation: Bob cannot read, list, create, or delete Alice's documents", async () => {
  const aliceDb = getDb(testEnv.authenticatedContext(ALICE_UID));
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("documents").doc("doc_alice").set({
      userId: ALICE_UID,
      title: "Private Contract",
      fileName: "contract.pdf",
      mimeType: "application/pdf",
      sizeBytes: 1024,
      pageCount: 2,
      folderName: "Root",
      isFavorite: false,
      isTrashed: false,
      isEncrypted: true,
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );

  const bobDb = getDb(testEnv.authenticatedContext(BOB_UID));
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("documents").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("documents").doc("doc_alice").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("documents").doc("doc_alice").delete());
});

test("Shadow update & schema poisoning rejected", async () => {
  const aliceDb = getDb(testEnv.authenticatedContext(ALICE_UID));
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("profile").doc("main").set({
      userId: ALICE_UID,
      displayName: "Alice",
      planTier: "free",
      authProvider: "google",
      isAdmin: true,
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("documents").doc("bad_doc").set({
      userId: BOB_UID,
      title: "Spoof",
      fileName: "spoof.pdf",
      mimeType: "application/pdf",
      sizeBytes: 100,
      pageCount: 1,
      folderName: "Root",
      isFavorite: false,
      isTrashed: false,
      isEncrypted: false,
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );
});

test("Terminal state locking on BackupRecord once completed", async () => {
  const aliceDb = getDb(testEnv.authenticatedContext(ALICE_UID));
  const bakRef = aliceDb.collection("users").doc(ALICE_UID).collection("backups").doc("bak_terminal");
  await assertSucceeds(
    bakRef.set({
      userId: ALICE_UID,
      backupName: "Nightly",
      documentCount: 3,
      totalBytes: 50000,
      status: "in_progress",
      deviceName: "Pixel",
      createdAt: nowTs(),
      updatedAt: nowTs(),
    })
  );
  await assertSucceeds(
    bakRef.update({
      status: "completed",
      updatedAt: nowTs(),
    })
  );
  await assertFails(
    bakRef.update({
      status: "failed",
      updatedAt: nowTs(),
    })
  );
});
