const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_merchant_01";
const BOB_UID = "bob_merchant_02";

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

function validShopPayload(created) {
  return {
    shopName: "Narendra Enterprises",
    gstin: "09ABCDE1234F1Z5",
    phone: "9876543210",
    address: "Golghar, Gorakhpur, UP",
    stateName: "Uttar Pradesh",
    invoicePrefix: "NRB-",
    nextInvoiceNumber: 1,
    termsAndConditions: "Subject to Gorakhpur jurisdiction.",
    stampBase64: "iVBORw0KGgo=",
    signatureBase64: "iVBORw0KGgo=",
    defaultActive: true,
    createdAt: created,
    updatedAt: created,
  };
}

function validProductPayload(created) {
  return {
    name: "Basmati Rice 25kg",
    hsnCode: "1006",
    unit: "BAG",
    purchaseRate: 1850.0,
    sellingRate: 2150.0,
    gstRate: 5.0,
    taxInclusive: false,
    stockQty: 40.0,
    createdAt: created,
    updatedAt: created,
  };
}

function validCustomerPayload(created) {
  return {
    name: "Ramesh Kumar",
    phone: "9988776655",
    address: "Civil Lines, Gorakhpur",
    gstin: "",
    stateName: "Uttar Pradesh",
    createdAt: created,
    updatedAt: created,
  };
}

function validInvoicePayload(created) {
  return {
    invoiceNumber: "NRB-0001",
    shopId: "shop_1",
    shopName: "Narendra Enterprises",
    shopGstin: "09ABCDE1234F1Z5",
    shopPhone: "9876543210",
    shopAddress: "Golghar, Gorakhpur, UP",
    customerId: "cust_1",
    customerName: "Ramesh Kumar",
    customerPhone: "9988776655",
    customerAddress: "Civil Lines, Gorakhpur",
    customerGstin: "",
    interState: false,
    includeStamp: true,
    includeSignature: true,
    items: [
      {
        productId: "prod_1",
        name: "Basmati Rice 25kg",
        hsnCode: "1006",
        unit: "BAG",
        quantity: 2.0,
        sellingRate: 2150.0,
        gstRate: 5.0,
        taxInclusive: false,
        taxableAmount: 4300.0,
        gstAmount: 215.0,
        lineTotal: 4515.0,
      },
    ],
    subtotalTaxable: 4300.0,
    cgstAmount: 107.5,
    sgstAmount: 107.5,
    igstAmount: 0.0,
    totalGstAmount: 215.0,
    grandTotal: 4515.0,
    estimatedProfit: 600.0,
    paymentMode: "UPI",
    notes: "Delivered",
    issuedAtMillis: Date.now(),
    createdAt: created,
    updatedAt: created,
  };
}

test("Unauthenticated users cannot read or write any billing collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const created = new Date(Date.now() - 2000);
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("shops").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("products").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("customers").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("invoices").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("shops").doc("shop_1").set(validShopPayload(created))
  );
});

test("Owner can create, list, update, and delete shops; cross-user & ghost fields rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);

  const shopRef = aliceDb.collection("users").doc(ALICE_UID).collection("shops").doc("shop_1");
  await assertSucceeds(shopRef.set(validShopPayload(created)));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("shops").orderBy("createdAt", "asc").get());

  // Partial update on nextInvoiceNumber + updatedAt
  await assertSucceeds(
    shopRef.update({
      nextInvoiceNumber: 2,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject immutable createdAt modification
  await assertFails(
    shopRef.update({
      createdAt: new Date(Date.now() - 100),
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Reject @DocumentId "id" ghost field
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("shops").doc("shop_2").set({
      ...validShopPayload(created),
      id: "shop_2",
    })
  );

  // Cross-user isolation
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("shops").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("shops").doc("shop_1").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("shops").doc("shop_1").delete());

  await assertSucceeds(shopRef.delete());
});

test("Owner can manage products with Khareed and Bikri rates; invalid bounds rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 5000);
  const prodRef = aliceDb.collection("users").doc(ALICE_UID).collection("products").doc("prod_1");

  await assertSucceeds(prodRef.set(validProductPayload(created)));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("products").orderBy("name", "asc").get());

  await assertSucceeds(
    prodRef.update({
      sellingRate: 2200.0,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Negative sellingRate rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("products").doc("prod_bad").set({
      ...validProductPayload(created),
      sellingRate: -10.0,
    })
  );

  // Future timestamp rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("products").doc("prod_future").set(
      validProductPayload(new Date(Date.now() + 120000))
    )
  );
});

test("Owner can manage customers and invoices; cross-user & invalid paymentMode rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);

  const custRef = aliceDb.collection("users").doc(ALICE_UID).collection("customers").doc("cust_1");
  await assertSucceeds(custRef.set(validCustomerPayload(created)));
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("customers").orderBy("name", "asc").get());

  const invRef = aliceDb.collection("users").doc(ALICE_UID).collection("invoices").doc("inv_1");
  await assertSucceeds(invRef.set(validInvoicePayload(created)));
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("invoices").orderBy("issuedAtMillis", "desc").get()
  );

  // Update allowed stamp/signature/paymentMode options
  await assertSucceeds(
    invRef.update({
      includeStamp: false,
      includeSignature: true,
      paymentMode: "CASH",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Disallow tampering with grandTotal on existing invoice
  await assertFails(
    invRef.update({
      grandTotal: 1.0,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Disallow invalid paymentMode
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("invoices").doc("inv_bad").set({
      ...validInvoicePayload(created),
      paymentMode: "CRYPTO",
    })
  );

  // Bob cannot read Alice's invoices or customers
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("invoices").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("customers").doc("cust_1").get());
});
