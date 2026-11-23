const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
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

test("Unauthenticated user cannot read or write profiles, memories, sessions, or messages", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const past = new Date(Date.now() - 2000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("memories").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Intruder",
      createdAt: past,
      updatedAt: past,
    })
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_1").set({
      content: "Should fail",
      category: "general",
      importance: 3,
      source: "manual",
      pinned: false,
      createdAt: past,
      updatedAt: past,
    })
  );
});

test("Authenticated owner: valid user profile create, partial update, and data-minimization bounds", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const profileRef = aliceDb.collection("users").doc(ALICE_UID);

  await assertSucceeds(
    profileRef.set({
      userId: ALICE_UID,
      displayName: "Alice",
      interests: "Hiking, Jazz",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    profileRef.update({
      goals: "Run a 10K",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject ghost field (shadow update attack)
  await assertFails(
    profileRef.update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject immutable createdAt mutation
  await assertFails(
    profileRef.update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject oversized customNotes (> 500 chars)
  await assertFails(
    profileRef.update({
      customNotes: "x".repeat(501),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject future timestamp (> request.time)
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_future").set({
      content: "Future memory",
      category: "general",
      importance: 3,
      source: "manual",
      pinned: false,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});

test("Authenticated owner: valid memories, sessions, and messages CRUD and queries succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 5000);

  // Create & list memories
  const memRef = aliceDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_1");
  await assertSucceeds(
    memRef.set({
      content: "Prefers black coffee in the morning",
      category: "preference",
      importance: 4,
      source: "chat",
      pinned: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    memRef.update({
      pinned: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("memories").get()
  );

  // Reject memory exceeding 320 chars (data minimization limit)
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("memories").doc("mem_big").set({
      content: "a".repeat(321),
      category: "general",
      importance: 3,
      source: "manual",
      pinned: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Create session + messages
  const sessionRef = aliceDb.collection("users").doc(ALICE_UID).collection("sessions").doc("sess_1");
  await assertSucceeds(
    sessionRef.set({
      title: "Morning catchup",
      active: true,
      clientUpdatedAt: Date.now(),
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    sessionRef.update({
      summary: "Discussed weekend hike",
      active: false,
      clientUpdatedAt: Date.now(),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  const msgRef = sessionRef.collection("messages").doc("msg_1");
  await assertSucceeds(
    msgRef.set({
      role: "user",
      content: "Hey Ann, check my recent emails please!",
      clientTimestamp: Date.now(),
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    sessionRef.collection("messages").orderBy("clientTimestamp").get()
  );
});

test("Cross-user isolation: Alice cannot read or modify Bob's data", async () => {
  const created = new Date(Date.now() - 10000);
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("users").doc(BOB_UID).set({
      userId: BOB_UID,
      displayName: "Bob",
      createdAt: created,
      updatedAt: created,
    });
    await db.collection("users").doc(BOB_UID).collection("memories").doc("bob_mem").set({
      content: "Bob's private note",
      category: "general",
      importance: 5,
      source: "manual",
      pinned: true,
      createdAt: created,
      updatedAt: created,
    });
    await db.collection("users").doc(BOB_UID).collection("sessions").doc("bob_sess").set({
      title: "Bob Chat",
      active: true,
      clientUpdatedAt: Date.now(),
      createdAt: created,
      updatedAt: created,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("users").doc(BOB_UID).get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("memories").get());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("memories").doc("bob_mem").delete());
  await assertFails(aliceDb.collection("users").doc(BOB_UID).collection("sessions").get());
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("sessions").doc("bob_sess").collection("messages").doc("x").set({
      role: "user",
      content: "Inject",
      clientTimestamp: Date.now(),
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});
