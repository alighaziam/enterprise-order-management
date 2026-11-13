const NAV = [
  ["desk", "nav.desk"],
  ["checkout", "nav.checkout"],
  ["customers", "nav.customers"],
  ["catalog", "nav.catalog"],
  ["inventory", "nav.inventory"],
  ["orders", "nav.orders"],
  ["payments", "nav.payments"],
  ["shipments", "nav.shipments"],
  ["outbox", "nav.outbox"]
];

const TITLES = {
  desk: ["eyebrow.desk", "heading.desk"],
  checkout: ["eyebrow.checkout", "heading.checkout"],
  customers: ["eyebrow.customers", "heading.customers"],
  catalog: ["eyebrow.catalog", "heading.catalog"],
  inventory: ["eyebrow.inventory", "heading.inventory"],
  orders: ["eyebrow.orders", "heading.orders"],
  payments: ["eyebrow.payments", "heading.payments"],
  shipments: ["eyebrow.shipments", "heading.shipments"],
  outbox: ["eyebrow.outbox", "heading.outbox"]
};

const NEXT_SHIPMENT = {
  PENDING: ["LABEL_CREATED", "CANCELLED"],
  LABEL_CREATED: ["READY_FOR_DISPATCH", "CANCELLED"],
  READY_FOR_DISPATCH: ["DISPATCHED", "CANCELLED"],
  DISPATCHED: ["IN_TRANSIT", "DELIVERY_FAILED"],
  IN_TRANSIT: ["DELIVERED", "DELIVERY_FAILED"],
  DELIVERY_FAILED: ["RETURNED"]
};

const state = {
  route: "desk",
  customers: [], products: [], stock: [], orders: [],
  payments: [], shipments: [], checkouts: [], events: [], health: null,
  draft: emptyDraft(),
  busy: false,
  query: "",
  inspect: null,
  confirm: null,
  attempted: false,
  result: null,
  edit: null,
  rows: { customers: [], products: [], inventory: [], orders: [], payments: [], shipments: [], checkouts: [], outbox: [] },
  meta: {},
  lists: {
    customers: blankList(), products: blankList(), inventory: blankList(), orders: blankList(),
    payments: blankList(), shipments: blankList(), checkouts: blankList(), outbox: blankList()
  },
  lang: localStorage.getItem("eom.lang") === "fa" || localStorage.getItem("eom.lang") === "en"
    ? localStorage.getItem("eom.lang")
    : (navigator.language || "").toLowerCase().startsWith("fa") ? "fa" : "en",
  accessToken: sessionStorage.getItem("eom.accessToken") || ""
};

function blankList() { return { page: 0, size: 8, q: "", filter: "" }; }
function emptyDraft() {
  return {
    customerId: "",
    lines: [{ productId: "", quantity: 1 }],
    recipientName: "", addressLine1: "", addressLine2: "",
    city: "", postalCode: "", countryCode: "DE", phone: "",
    idempotencyKey: `desk-${Date.now()}`
  };
}

const view = document.querySelector("#view");
const nav = document.querySelector("#nav");
const toast = document.querySelector("#toast");

const accessTokenInput = document.querySelector("#access-token");
accessTokenInput.value = state.accessToken;
document.querySelector("#save-token").addEventListener("click", () => {
  state.accessToken = accessTokenInput.value.trim().replace(/^Bearer\\s+/i, "");
  if (state.accessToken) sessionStorage.setItem("eom.accessToken", state.accessToken);
  else sessionStorage.removeItem("eom.accessToken");
  load(true);
});
document.querySelector("#refresh").addEventListener("click", () => load(true));
document.querySelector(".lang").addEventListener("click", (event) => {
  const button = event.target.closest("[data-lang]");
  if (!button || button.dataset.lang === state.lang) return;
  state.lang = button.dataset.lang;
  localStorage.setItem("eom.lang", state.lang);
  applyLocale();
  renderNav();
  paint();
});
document.querySelector("#nav").addEventListener("click", (event) => {
  const link = event.target.closest("a");
  if (!link) return;
  state.route = link.dataset.route;
});
view.addEventListener("click", onClick);
view.addEventListener("submit", onSubmit);
view.addEventListener("change", onChange);
view.addEventListener("input", onInput);
window.addEventListener("hashchange", syncRoute);
window.addEventListener("keydown", (event) => {
  if (event.key === "Escape" && state.inspect) {
    state.inspect = null;
    paint();
  }
});

async function boot() {
  applyLocale();
  renderNav();
  syncRoute();
  await load(false);
}

function syncRoute() {
  const route = (location.hash || "#/desk").replace("#/", "");
  const next = TITLES[route] ? route : "desk";
  if (next !== state.route) {
    state.query = "";
    state.confirm = null;
  }
  state.route = next;
  renderNav();
  paint();
}

function renderNav() {
  nav.innerHTML = NAV.map(([id, label]) =>
    `<a href="#/${id}" data-route="${id}" class="${state.route === id ? "active" : ""}">${esc(t(label))}</a>`
  ).join("") + `<a href="/swagger-ui.html">${esc(t("nav.swagger"))}</a>`;
}

async function load(announce) {
  const healthChip = document.querySelector("#health");
  healthChip.textContent = t("health.refreshing");
  healthChip.className = "health";
  try {
    const [customers, products, stock, orders, payments, shipments, checkouts, events, health, customerChoices, productChoices, stockChoices] = await Promise.all([
      api(listUrl("customers")),
      api(listUrl("products")),
      api(listUrl("inventory")),
      api(listUrl("orders")),
      api(listUrl("payments")),
      api(listUrl("shipments")),
      api(listUrl("checkouts")),
      api(listUrl("outbox")),
      api("/actuator/health").catch(() => null),
      api("/api/v1/customers?page=0&size=100&active=true"),
      api("/api/v1/products?page=0&size=100&active=true"),
      api("/api/v1/inventory?page=0&size=100")
    ]);
    state.rows = {
      customers: customers.content, products: products.content, inventory: stock.content,
      orders: orders.content, payments: payments.content, shipments: shipments.content,
      checkouts: checkouts.content, outbox: events.content
    };
    state.meta = { customers, products, inventory: stock, orders, payments, shipments, checkouts, outbox: events };
    state.customers = mergeById(customerChoices.content, customers.content);
    state.products = mergeById(productChoices.content, products.content);
    state.stock = stockChoices.content;
    state.orders = orders.content;
    state.payments = payments.content;
    state.shipments = shipments.content;
    state.checkouts = checkouts.content;
    state.events = events.content;
    state.health = health;
    if (!state.draft.customerId && state.customers[0]) state.draft.customerId = state.customers[0].id;
    if (announce) notify(t("toast.refreshed"));
  } catch (error) {
    notify(error.message);
  }
  state.busy = false;
  paint();
}

function paint() {
  const [eyebrow, title] = TITLES[state.route];
  document.querySelector("#eyebrow").textContent = t(eyebrow);
  document.querySelector("#title").textContent = t(title);
  const health = document.querySelector("#health");
  const live = state.health?.status === "UP";
  health.textContent = live ? t("health.live") : t("health.offline");
  health.className = live ? "health up" : "health down";
  const pages = { desk: desk, checkout: checkout, customers: customers, catalog: catalog, inventory: inventory, orders: orders, payments: payments, shipments: shipments, outbox: outbox };
  const search = state.route === "checkout" ? "" : searchBox();
  view.innerHTML = search + pages[state.route]() + inspector();
}

function desk() {
  const attention = state.checkouts.filter((item) => item.status === "COMPENSATION_REQUIRED" || item.status === "FULFILLMENT_PENDING");
  const dead = state.events.filter((item) => item.deadLetteredAt);
  const reserved = state.orders.filter((item) => item.status === "RESERVED");
  return `
    <p class="lede">${esc(t("lede.desk"))}</p>
    <section class="grid metrics">
      ${metric(t("metric.checkouts"), state.meta.checkouts?.totalElements ?? state.checkouts.length, t("pager.status", { page: (state.meta.checkouts?.number || 0) + 1, pages: state.meta.checkouts?.totalPages || 1, total: state.meta.checkouts?.totalElements || 0 }))}
      ${metric(t("metric.attention"), state.checkouts.filter((item) => item.status === "COMPENSATION_REQUIRED" || item.status === "FULFILLMENT_PENDING").length, t("metric.attentionNote"))}
      ${metric(t("metric.reserved"), reserved.length, t("metric.reservedNote"))}
      ${metric(t("metric.dead"), dead.length, t("metric.deadNote"))}
    </section>
    <section class="grid split" style="margin-top:16px">
      <article class="card">
        <div class="card-head"><h2>${esc(t("section.attention"))}</h2></div>
        ${attention.length ? table([t("col.workflow"), t("col.status"), t("col.error"), ""], attention.map((item) => [
          openId("checkout", item.workflowId), pill(item.status), ltr(item.lastError || "—"), attentionAction(item)
        ])) : `<p class="empty">${esc(state.query ? t("empty.filter") : t("empty.attention"))}</p>`}
      </article>
      <article class="card">
        <div class="card-head"><h2>${esc(t("section.latest"))}</h2><a href="#/checkout">${esc(t("section.openComposer"))}</a></div>
        ${state.checkouts.length ? table([t("col.workflow"), t("col.status"), t("col.order")], state.checkouts.slice(0, 6).map((item) => [
          openId("checkout", item.workflowId), pill(item.status), openId("order", item.orderId)
        ])) : `<p class="empty">${esc(t("empty.checkout"))}</p>`}
      </article>
    </section>${pager("checkouts")}`;
}

function checkout() {
  const draft = state.draft;
  const priced = draft.lines.map((line) => ({ ...line, product: productById(line.productId) })).filter((line) => line.product);
  const currencies = [...new Set(priced.map((line) => line.product.currency))];
  const total = currencies.length === 1 ? priced.reduce((sum, line) => sum + Number(line.product.unitPrice) * Number(line.quantity || 0), 0) : null;
  const problems = state.attempted ? checkoutProblems(draft, priced) : [];
  return `
    <p class="lede">${esc(t("lede.checkout"))}</p>
    <section class="grid split">
      <form class="card pad stack" data-form="checkout">
        <label class="field">${esc(t("form.customer"))}
          <select name="customerId">${options(state.customers.filter((item) => item.active), draft.customerId, (item) => item.id, (item) => `${item.fullName} · ${item.email}`)}</select>
        </label>
        <div>
          ${draft.lines.map((line, index) => `
            <div class="line" style="margin-bottom:8px">
              <label class="field">${esc(t("form.product"))}
                <select data-line="product" data-index="${index}">${options(state.products.filter((item) => item.active), line.productId, (item) => item.id, (item) => `${item.sku} · ${item.name} · ${availableLabel(item.id)}`)}</select>
              </label>
              <label class="field">${esc(t("form.qty"))}
                <input data-line="quantity" data-index="${index}" type="number" min="1" dir="ltr" value="${esc(line.quantity)}">
              </label>
              <button class="btn quiet" type="button" data-action="remove-line" data-index="${index}">${esc(t("action.remove"))}</button>
            </div>`).join("")}
          <button class="btn" type="button" data-action="add-line">${esc(t("action.addLine"))}</button>
        </div>
        <div class="form-row">
          ${input("recipientName", t("form.recipient"), draft.recipientName)}
          ${input("phone", t("form.phone"), draft.phone, 40, "text", "ltr")}
        </div>
        ${input("addressLine1", t("form.address"), draft.addressLine1)}
        ${input("addressLine2", t("form.address2"), draft.addressLine2)}
        <div class="form-row">
          ${input("city", t("form.city"), draft.city)}
          ${input("postalCode", t("form.postal"), draft.postalCode, 40, "text", "ltr")}
        </div>
        ${input("countryCode", t("form.country"), draft.countryCode, 2, "text", "ltr")}
        ${input("idempotencyKey", t("form.idempotency"), draft.idempotencyKey, 120, "text", "ltr")}
        <button class="btn primary" type="submit" ${state.busy || problems.length ? "disabled" : ""}>${esc(t("action.runCheckout"))}</button>
      </form>
      <aside class="card pad">
        <h2>${esc(t("quote"))}</h2>
        <div class="summary">
          <dl>
            <dt>${esc(t("quote.lines"))}</dt><dd>${priced.length}</dd>
            <dt>${esc(t("quote.currency"))}</dt><dd>${currencies.length === 1 ? ltr(currencies[0]) : currencies.length ? esc(t("mixed")) : "—"}</dd>
            <dt>${esc(t("quote.total"))}</dt><dd>${total == null ? "—" : ltr(money(total, currencies[0]))}</dd>
          </dl>
        </div>
        ${shortages(priced).length ? `<p class="warn">${shortages(priced).map((line) => esc(t("shortage", { sku: line.product.sku, stock: availableLabel(line.product.id) }))).join(" ")}</p>` : ""}
        ${problems.length ? `<ul class="problems">${problems.map((problem) => `<li>${esc(problem)}</li>`).join("")}</ul>` : ""}
        ${state.result ? resultCard(state.result) : ""}
        <p class="lede" style="margin-top:14px">${esc(t("lede.idempotency"))}</p>
      </aside>
    </section>`;
}

function customers() {
  const rows = state.rows.customers;
  const editing = state.edit?.kind === "customer" ? state.edit : null;
  return `
    <section class="grid split">
      <div>
        ${collection(t("directory"), rows, [t("col.name"), t("col.email"), t("col.status"), ""], (item) => [
          openId("customer", item.id, item.fullName), ltr(item.email), `<span class="pill ${item.active ? "good" : "bad"}">${esc(item.active ? t("filter.active") : t("filter.inactive"))}</span>`,
          `<div class="row-actions"><button class="btn" type="button" data-action="edit" data-kind="customer" data-id="${item.id}">${esc(t("action.edit"))}</button><button class="btn" type="button" data-action="active" data-resource="customers" data-id="${item.id}" data-active="${item.active ? "false" : "true"}">${esc(item.active ? t("action.disable") : t("action.enable"))}</button></div>`
        ], t("empty.customers"))}
        ${pager("customers")}
      </div>
      <form class="card pad stack" data-form="customer">
        <h2>${esc(editing ? t("action.edit") : t("form.newCustomer"))}</h2>
        ${input("fullName", t("form.fullName"), editing?.fullName || "")}
        ${input("email", t("form.email"), editing?.email || "", 254, "email", "ltr")}
        <div class="row-actions">
          <button class="btn primary" type="submit">${esc(editing ? t("action.save") : t("action.createCustomer"))}</button>
          ${editing ? `<button class="btn" type="button" data-action="cancel-edit">${esc(t("action.cancelEdit"))}</button>` : ""}
        </div>
      </form>
    </section>`;
}

function catalog() {
  const rows = state.rows.products;
  const editing = state.edit?.kind === "product" ? state.edit : null;
  return `
    <section class="grid split">
      <div>
        ${collection(t("products"), rows, [t("col.sku"), t("col.name"), t("col.price"), t("col.status"), ""], (item) => [
          openId("product", item.id, item.sku), bdi(item.name), ltr(money(item.unitPrice, item.currency)),
          `<span class="pill ${item.active ? "good" : "bad"}">${esc(item.active ? t("filter.active") : t("filter.inactive"))}</span>`,
          `<div class="row-actions"><button class="btn" type="button" data-action="edit" data-kind="product" data-id="${item.id}">${esc(t("action.edit"))}</button><button class="btn" type="button" data-action="active" data-resource="products" data-id="${item.id}" data-active="${item.active ? "false" : "true"}">${esc(item.active ? t("action.disable") : t("action.enable"))}</button></div>`
        ], t("empty.products"))}
        ${pager("products")}
      </div>
      <form class="card pad stack" data-form="product">
        <h2>${esc(editing ? t("action.edit") : t("form.newProduct"))}</h2>
        ${editing ? `<p class="note">${esc(t("col.sku"))}: ${ltr(editing.sku)}</p>` : input("sku", t("col.sku"), "", 80, "text", "ltr")}
        ${input("name", t("col.name"), editing?.name || "")}
        <label class="field">${esc(t("form.description"))}<textarea name="description" dir="auto">${esc(editing?.description || "")}</textarea></label>
        <div class="form-row">
          ${input("unitPrice", t("form.unitPrice"), editing?.unitPrice ?? "10.00", 16, "number", "ltr")}
          ${input("currency", t("form.currency"), editing?.currency || "EUR", 3, "text", "ltr")}
        </div>
        <div class="row-actions">
          <button class="btn primary" type="submit">${esc(editing ? t("action.save") : t("action.createProduct"))}</button>
          ${editing ? `<button class="btn" type="button" data-action="cancel-edit">${esc(t("action.cancelEdit"))}</button>` : ""}
        </div>
      </form>
    </section>`;
}

function inventory() {
  const editing = state.edit?.kind === "stock" ? state.edit : null;
  const rows = state.rows.inventory.map((item) => {
    const product = productById(item.productId);
    return [openId("stock", item.productId, product ? product.sku : t("unknown")), openId("product", item.productId), String(item.onHandQuantity), String(item.reservedQuantity), String(item.availableQuantity), `<button class="btn" type="button" data-action="edit" data-kind="stock" data-id="${item.productId}">${esc(t("action.edit"))}</button>`];
  });
  return `
    <section class="grid split">
      <div>
        ${collection(t("stockTitle"), rows, [t("col.sku"), t("col.product"), t("col.onHand"), t("col.reserved"), t("col.available"), ""], (row) => row, t("empty.stock"))}
        ${pager("inventory")}
      </div>
      <form class="card pad stack" data-form="stock">
        <h2>${esc(editing ? t("action.edit") : t("form.initializeStock"))}</h2>
        ${editing ? `<p class="note">${esc(t("form.product"))}: ${openId("product", editing.productId)}</p><input type="hidden" name="productId" value="${editing.productId}">` : `<label class="field">${esc(t("form.product"))}<select name="productId">${options(state.products, "", (item) => item.id, (item) => `${item.sku} · ${item.name}`)}</select></label>`}
        ${input("quantity", t("form.quantity"), editing ? String(editing.onHandQuantity) : "10", 8, "number", "ltr")}
        <div class="row-actions">
          <button class="btn primary" type="submit">${esc(editing ? t("action.save") : t("action.setStock"))}</button>
          ${editing ? `<button class="btn" type="button" data-action="cancel-edit">${esc(t("action.cancelEdit"))}</button>` : ""}
        </div>
      </form>
    </section>`;
}

function orders() {
  const body = state.rows.orders.map((item) => [
    openId("order", item.orderId),
    bdi(customerName(item.customerId)),
    pill(item.status),
    ltr(money(item.totalAmount, item.currency)),
    item.status === "RESERVED" ? `
      <div class="actions">
        <button class="btn" type="button" data-action="confirm-order" data-id="${item.orderId}">${esc(t("action.confirm"))}</button>
        <button class="btn danger" type="button" data-action="cancel-order" data-id="${item.orderId}" data-confirm="${esc(t("confirm.cancelOrder"))}">${esc(armed("cancel-order", item.orderId) ? t("action.confirmCancel") : t("action.cancel"))}</button>
      </div>` : ""
  ]);
  return collection(t("ordersTitle"), body, [t("col.order"), t("col.customer"), t("col.status"), t("col.total"), ""], (row) => row, t("empty.orders")) + pager("orders");
}

function payments() {
  const body = state.rows.payments.map((item) => [
    openId("payment", item.id),
    openId("order", item.orderId),
    pill(item.status),
    ltr(money(item.amount, item.currency)),
    paymentActions(item)
  ]);
  return collection(t("paymentsTitle"), body, [t("col.payment"), t("col.order"), t("col.status"), t("col.amount"), ""], (row) => row, t("empty.payments")) + pager("payments");
}

function shipments() {
  const body = state.rows.shipments.map((item) => [
    openId("shipment", item.id),
    bdi(item.destination?.recipientName || "—"),
    bdi(item.destination ? `${item.destination.city}, ${item.destination.countryCode}` : "—"),
    pill(item.status),
    shipmentActions(item)
  ]);
  return collection(t("shipmentsTitle"), body, [t("col.shipment"), t("col.recipient"), t("col.destination"), t("col.status"), ""], (row) => row, t("empty.shipments")) + pager("shipments");
}

function outbox() {
  const body = state.rows.outbox.map((item) => [
    esc(item.eventType),
    `${esc(item.aggregateType)} ${openId("event", item.eventId, short(item.aggregateId))}`,
    String(item.attempts),
    item.deadLetteredAt ? pill("DEAD_LETTER", "bad") : item.publishedAt ? pill("PUBLISHED", "good") : pill("PENDING", "warn"),
    ltr(item.lastError || "—"),
    item.deadLetteredAt ? `<button class="btn" type="button" data-action="replay" data-id="${item.eventId}">${esc(t("action.replay"))}</button>` : ""
  ]);
  return `
    <p class="lede">${esc(t("lede.outbox"))}</p>
    ${collection(t("eventsTitle"), body, [t("col.event"), t("col.aggregate"), t("col.attempts"), t("col.state"), t("col.lastError"), ""], (row) => row, t("empty.events"))}
    ${pager("outbox")}`;
}

function paymentActions(item) {
  if (item.status === "AUTHORIZED") {
    return `<div class="actions">
      <button class="btn" type="button" data-action="capture" data-id="${item.id}">${esc(t("action.capture"))}</button>
      <button class="btn quiet" type="button" data-action="void" data-id="${item.id}" data-confirm="${esc(t("confirm.void"))}">${esc(armed("void", item.id) ? t("action.confirmVoid") : t("action.void"))}</button>
    </div>`;
  }
  if (item.status === "CAPTURED") return `<button class="btn danger" type="button" data-action="refund" data-id="${item.id}" data-confirm="${esc(t("confirm.refund"))}">${esc(armed("refund", item.id) ? t("action.confirmRefund") : t("action.refund"))}</button>`;
  return "";
}

function shipmentActions(item) {
  const next = NEXT_SHIPMENT[item.status] || [];
  if (!next.length) return "";
  return `<div class="actions">${next.map((status) => `<button class="btn ${status === "CANCELLED" || status === "DELIVERY_FAILED" ? "danger" : ""}" type="button" data-action="ship" data-id="${item.id}" data-status="${status}" ${status === "CANCELLED" ? `data-confirm="${esc(t("confirm.shipment"))}"` : ""}>${esc(armed("ship", item.id, status) ? t("action.confirmStep") : label(status))}</button>`).join("")}</div>`;
}

async function onSubmit(event) {
  const form = event.target.closest("form");
  if (!form) return;
  event.preventDefault();
  const data = Object.fromEntries(new FormData(form).entries());
  try {
    state.busy = true;
    if (form.dataset.form === "customer") {
      if (!data.fullName.trim() || !data.email.includes("@")) throw new Error(t("error.customer"));
      const body = { fullName: data.fullName.trim(), email: data.email.trim() };
      if (state.edit?.kind === "customer") {
        await api(`/api/v1/customers/${state.edit.id}`, { method: "PUT", body });
        state.edit = null;
        notify(t("toast.updated"));
      } else {
        await api("/api/v1/customers", { method: "POST", body });
        notify(t("toast.customer"));
      }
    } else if (form.dataset.form === "product") {
      const editing = state.edit?.kind === "product" ? state.edit : null;
      if ((!editing && !String(data.sku || "").trim()) || !data.name.trim() || !(Number(data.unitPrice) > 0) || !/^[A-Za-z]{3}$/.test(data.currency.trim())) {
        throw new Error(t("error.product"));
      }
      const body = { name: data.name.trim(), description: data.description || null, unitPrice: Number(data.unitPrice), currency: data.currency.trim().toUpperCase() };
      if (editing) {
        await api(`/api/v1/products/${editing.id}`, { method: "PUT", body });
        state.edit = null;
        notify(t("toast.updated"));
      } else {
        await api("/api/v1/products", { method: "POST", body: { ...body, sku: data.sku.trim() } });
        notify(t("toast.product"));
      }
    } else if (form.dataset.form === "stock") {
      const quantity = Number(data.quantity);
      if (!data.productId || !Number.isFinite(quantity) || quantity < 0) throw new Error(t("error.stock"));
      const existing = state.edit?.kind === "stock" || state.stock.some((item) => item.productId === data.productId);
      if (existing) await api(`/api/v1/inventory/${data.productId}`, { method: "PUT", body: { quantity } });
      else await api("/api/v1/inventory/stock", { method: "POST", body: { productId: data.productId, quantity } });
      state.edit = null;
      notify(t("toast.updated"));
    } else if (form.dataset.form === "checkout") {
      readDraft(form);
      const draft = state.draft;
      const priced = draft.lines.map((line) => ({ ...line, product: productById(line.productId) })).filter((line) => line.product);
      const problems = checkoutProblems(draft, priced);
      if (problems.length) {
        state.attempted = true;
        state.busy = false;
        paint();
        return;
      }
      const result = await api("/api/v1/checkouts", {
        method: "POST",
        body: {
          customerId: draft.customerId,
          lines: draft.lines.filter((line) => line.productId).map((line) => ({ productId: line.productId, quantity: Number(line.quantity) })),
          recipientName: draft.recipientName,
          addressLine1: draft.addressLine1,
          addressLine2: draft.addressLine2 || null,
          city: draft.city,
          postalCode: draft.postalCode || null,
          countryCode: draft.countryCode.trim().toUpperCase(),
          phone: draft.phone || null,
          idempotencyKey: draft.idempotencyKey
        }
      });
      state.draft = emptyDraft();
      state.attempted = false;
      state.result = result;
      state.inspect = { kind: "checkout", id: result.workflowId };
      notify(t("toast.checkout", { status: label(result.status) }));
    }
    await load(false);
  } catch (error) {
    notify(error.message);
    state.busy = false;
    paint();
  }
}

function onInput(event) {
  if (event.target.id !== "query") return;
  const key = listKey();
  state.lists[key].q = event.target.value;
  state.lists[key].page = 0;
  clearTimeout(onInput.timer);
  onInput.timer = setTimeout(() => load(false), 250);
}

function onChange(event) {
  if (event.target.id === "facet") {
    const key = listKey();
    state.lists[key].filter = event.target.value;
    state.lists[key].page = 0;
    load(false);
    return;
  }
  const form = event.target.closest("form[data-form='checkout']");
  if (!form) return;
  readDraft(form);
  paint();
}

function readDraft(form) {
  const data = Object.fromEntries(new FormData(form).entries());
  state.draft.customerId = data.customerId || "";
  state.draft.recipientName = data.recipientName || "";
  state.draft.addressLine1 = data.addressLine1 || "";
  state.draft.addressLine2 = data.addressLine2 || "";
  state.draft.city = data.city || "";
  state.draft.postalCode = data.postalCode || "";
  state.draft.countryCode = data.countryCode || "";
  state.draft.phone = data.phone || "";
  state.draft.idempotencyKey = data.idempotencyKey || "";
  form.querySelectorAll("[data-line='product']").forEach((input) => {
    state.draft.lines[Number(input.dataset.index)].productId = input.value;
  });
  form.querySelectorAll("[data-line='quantity']").forEach((input) => {
    state.draft.lines[Number(input.dataset.index)].quantity = Number(input.value || 1);
  });
}

async function onClick(event) {
  const opener = event.target.closest("[data-open]");
  if (opener) {
    state.inspect = { kind: opener.dataset.open, id: opener.dataset.id };
    state.confirm = null;
    paint();
    return;
  }
  const button = event.target.closest("[data-action]");
  if (!button) return;
  const action = button.dataset.action;
  if (action === "add-line") {
    readDraft(button.closest("form"));
    state.draft.lines.push({ productId: "", quantity: 1 });
    paint();
    return;
  }
  if (action === "remove-line") {
    readDraft(button.closest("form"));
    if (state.draft.lines.length > 1) state.draft.lines.splice(Number(button.dataset.index), 1);
    paint();
    return;
  }
  if (action === "close-inspect") {
    state.inspect = null;
    paint();
    return;
  }
  if (action === "copy") {
    try {
      await navigator.clipboard.writeText(button.dataset.id);
      notify(t("toast.copied"));
    } catch (error) {
      notify(t("toast.copyBlocked"));
    }
    return;
  }
  if (action === "page") {
    state.lists[button.dataset.list].page = Number(button.dataset.page);
    await load(false);
    return;
  }
  if (action === "cancel-edit") {
    state.edit = null;
    paint();
    return;
  }
  if (action === "edit") {
    const kind = button.dataset.kind;
    const id = button.dataset.id;
    if (kind === "customer") state.edit = { kind, ...state.rows.customers.find((item) => item.id === id) };
    if (kind === "product") state.edit = { kind, ...state.rows.products.find((item) => item.id === id) };
    if (kind === "stock") state.edit = { kind, productId: id, ...state.rows.inventory.find((item) => item.productId === id) };
    paint();
    return;
  }
  if (action === "active") {
    try {
      await api(`/api/v1/${button.dataset.resource}/${button.dataset.id}/active`, { method: "POST", body: { active: button.dataset.active === "true" } });
      notify(t("toast.updated"));
      await load(false);
    } catch (error) {
      notify(error.message);
    }
    return;
  }
  const id = button.dataset.id;
  if (button.dataset.confirm) {
    const key = `${action}:${id}:${button.dataset.status || ""}`;
    if (state.confirm !== key) {
      state.confirm = key;
      notify(button.dataset.confirm);
      paint();
      return;
    }
  }
  state.confirm = null;
  try {
    if (action === "confirm-order") await api(`/api/v1/orders/${id}/confirm`, { method: "POST" });
    if (action === "cancel-order") await api(`/api/v1/orders/${id}/cancel`, { method: "POST" });
    if (action === "capture") await api(`/api/v1/payments/${id}/capture`, { method: "POST" });
    if (action === "void") await api(`/api/v1/payments/${id}/void`, { method: "POST" });
    if (action === "refund") await api(`/api/v1/payments/${id}/refund`, { method: "POST" });
    if (action === "ship") await api(`/api/v1/shipments/${id}/status`, { method: "POST", body: { status: button.dataset.status } });
    if (action === "replay") await api(`/api/v1/outbox/events/${id}/replay`, { method: "POST" });
    if (action === "reconcile") await api(`/api/v1/checkouts/${id}/reconcile`, { method: "POST" });
    notify(t("toast.saved"));
    await load(false);
  } catch (error) {
    notify(error.message);
  }
}

function collection(title, rows, headers, cells, emptyText) {
  const body = rows;
  const message = state.query && !body.length ? t("empty.filter") : emptyText;
  return `<article class="card"><div class="card-head"><h2>${esc(title)}</h2></div>${
    body.length ? table(headers, body.map(cells)) : `<p class="empty">${esc(message)}</p>`
  }</article>`;
}

function table(headers, rows) {
  return `<div class="scroll"><table><thead><tr>${headers.map((header) => `<th>${esc(header)}</th>`).join("")}</tr></thead><tbody>${
    rows.map((row) => `<tr>${row.map((cell) => `<td>${cell ?? ""}</td>`).join("")}</tr>`).join("")
  }</tbody></table></div>`;
}

function metric(labelText, value, note) {
  return `<article class="card metric"><span>${esc(labelText)}</span><strong>${value}</strong><em>${esc(note)}</em></article>`;
}

function input(name, labelText, value, max, type = "text", dir = "auto") {
  const step = type === "number" ? ` step="0.01"` : "";
  return `<label class="field">${esc(labelText)}<input name="${name}" type="${type}" dir="${dir}" maxlength="${max || 180}" value="${esc(value)}"${step}></label>`;
}

function options(items, selected, value, labelText) {
  const blank = `<option value="">${esc(t("select"))}</option>`;
  return blank + items.map((item) => `<option value="${esc(value(item))}" ${value(item) === selected ? "selected" : ""}>${esc(labelText(item))}</option>`).join("");
}

function pill(status, tone) {
  return `<span class="pill ${tone || toneFor(status)}">${esc(label(status))}</span>`;
}
function toneFor(status) {
  if (["COMPLETED", "CONFIRMED", "CAPTURED", "DELIVERED", "AUTHORIZED"].includes(status)) return status === "AUTHORIZED" ? "info" : "good";
  if (["FAILED", "COMPENSATION_REQUIRED", "CANCELLED", "VOIDED", "DELIVERY_FAILED"].includes(status)) return status === "CANCELLED" || status === "VOIDED" ? "muted" : "bad";
  if (["FULFILLMENT_PENDING", "REFUNDED", "REFUND_PENDING", "RETURNED"].includes(status)) return "warn";
  return "info";
}
function label(value) {
  const translated = TEXT[state.lang]?.["status." + value];
  return translated || String(value || "").replaceAll("_", " ");
}
function money(amount, currency) { return `${currency || ""} ${Number(amount).toFixed(2)}`.trim(); }
function short(value) { return value ? String(value).slice(0, 8) : "—"; }
function mono(value) { return `<span class="id" title="${esc(value || "")}">${esc(short(value))}</span>`; }
function productById(id) { return state.products.find((item) => item.id === id); }
function availableLabel(productId) {
  const stock = state.stock.find((item) => item.productId === productId);
  return stock ? t("available", { n: stock.availableQuantity }) : t("noStock");
}
function shortages(lines) {
  return lines.filter((line) => {
    const stock = state.stock.find((item) => item.productId === line.productId);
    return !stock || Number(stock.availableQuantity) < Number(line.quantity || 0);
  });
}
function attentionAction(item) {
  if (item.status === "COMPENSATION_REQUIRED") {
    return `<button class="btn" type="button" data-action="reconcile" data-id="${item.workflowId}" data-confirm="${esc(t("confirm.reconcile"))}">${esc(armed("reconcile", item.workflowId) ? t("action.confirmReconcile") : t("action.reconcile"))}</button>`;
  }
  if (item.status === "FULFILLMENT_PENDING") return `<span class="note">${esc(t("action.retrying"))}</span>`;
  return "";
}
function searchBox() {
  const key = listKey();
  const list = state.lists[key];
  const facets = FACETS[state.route] || [];
  const filter = facets.map(([value, labelKey]) => `<option value="${esc(value)}" ${list.filter === value ? "selected" : ""}>${esc(t(labelKey))}</option>`).join("");
  return `<div class="toolbar"><label class="search">${esc(t("filter"))}<input id="query" value="${esc(list.q)}" placeholder="${esc(t("filter.placeholder"))}" autocomplete="off" dir="auto"></label><label class="search">${esc(t("col.status"))}<select id="facet">${filter}</select></label></div>`;
}
function listKey() {
  if (state.route === "catalog") return "products";
  if (state.route === "desk") return "checkouts";
  return state.route;
}
function listUrl(name) {
  const list = state.lists[name];
  const params = new URLSearchParams({ page: String(list.page), size: String(list.size) });
  if (list.q) params.set("q", list.q);
  if (list.filter) {
    if (name === "customers" || name === "products") params.set("active", list.filter);
    else if (name === "inventory") params.set("availability", list.filter);
    else if (name === "outbox") params.set("state", list.filter);
    else params.set("status", list.filter);
  }
  return `/api/v1/${name === "outbox" ? "outbox/events" : name}?${params}`;
}
function mergeById(primary, extra) {
  const map = new Map();
  for (const item of [...(primary || []), ...(extra || [])]) if (item && item.id) map.set(item.id, item);
  return [...map.values()];
}
function pager(name) {
  const meta = state.meta[name] || { number: 0, totalPages: 1, totalElements: 0 };
  const pages = Math.max(meta.totalPages || 1, 1);
  const number = meta.number || 0;
  return `<nav class="pager"><button class="btn" type="button" data-action="page" data-list="${name}" data-page="${number - 1}" ${number <= 0 ? "disabled" : ""}>${esc(t("pager.prev"))}</button><span>${esc(t("pager.status", { page: number + 1, pages, total: meta.totalElements || 0 }))}</span><button class="btn" type="button" data-action="page" data-list="${name}" data-page="${number + 1}" ${number + 1 >= pages ? "disabled" : ""}>${esc(t("pager.next"))}</button></nav>`;
}
const FACETS = {
  customers: [["", "filter.all"], ["true", "filter.active"], ["false", "filter.inactive"]],
  catalog: [["", "filter.all"], ["true", "filter.active"], ["false", "filter.inactive"]],
  inventory: [["", "filter.all"], ["available", "filter.available"], ["reserved", "filter.reserved"], ["empty", "filter.empty"]],
  orders: [["", "filter.all"], ["RESERVED", "status.RESERVED"], ["CONFIRMED", "status.CONFIRMED"], ["CANCELLED", "status.CANCELLED"]],
  payments: [["", "filter.all"], ["AUTHORIZED", "status.AUTHORIZED"], ["CAPTURED", "status.CAPTURED"], ["VOIDED", "status.VOIDED"], ["REFUNDED", "status.REFUNDED"], ["FAILED", "status.FAILED"]],
  shipments: [["", "filter.all"], ["PENDING", "status.PENDING"], ["LABEL_CREATED", "status.LABEL_CREATED"], ["DISPATCHED", "status.DISPATCHED"], ["IN_TRANSIT", "status.IN_TRANSIT"], ["DELIVERED", "status.DELIVERED"], ["CANCELLED", "status.CANCELLED"]],
  desk: [["", "filter.all"], ["COMPENSATION_REQUIRED", "status.COMPENSATION_REQUIRED"], ["FULFILLMENT_PENDING", "status.FULFILLMENT_PENDING"], ["COMPLETED", "status.COMPLETED"], ["FAILED", "status.FAILED"]],
  outbox: [["", "filter.all"], ["pending", "status.PENDING"], ["published", "status.PUBLISHED"], ["dead", "status.DEAD_LETTER"]]
};
function openId(kind, id, text) {
  if (!id) return "—";
  return `<button type="button" class="id-btn" data-open="${kind}" data-id="${esc(id)}" title="${esc(id)}">${esc(text || short(id))}</button>`;
}
function armed(action, id, status = "") {
  return state.confirm === `${action}:${id}:${status}`;
}
function checkoutProblems(draft, priced) {
  const problems = [];
  if (!draft.customerId) problems.push(t("problem.customer"));
  if (!priced.length) problems.push(t("problem.product"));
  if (shortages(priced).length) problems.push(t("problem.stock"));
  if (!draft.recipientName.trim()) problems.push(t("problem.recipient"));
  if (!draft.addressLine1.trim()) problems.push(t("problem.address"));
  if (!draft.city.trim()) problems.push(t("problem.city"));
  if (!/^[A-Za-z]{2}$/.test(draft.countryCode.trim())) problems.push(t("problem.country"));
  if (!draft.idempotencyKey.trim()) problems.push(t("problem.key"));
  return problems;
}
function resultCard(result) {
  return `<div class="result"><strong>${esc(t("lastCheckout"))}</strong> ${pill(result.status)}
    <dl>
      <dt>${esc(t("f.workflow"))}</dt><dd>${openId("checkout", result.workflowId)}</dd>
      <dt>${esc(t("f.order"))}</dt><dd>${openId("order", result.orderId)}</dd>
      <dt>${esc(t("f.payment"))}</dt><dd>${openId("payment", result.paymentId)}</dd>
      <dt>${esc(t("f.shipment"))}</dt><dd>${openId("shipment", result.shipmentId)}</dd>
    </dl></div>`;
}
function inspector() {
  if (!state.inspect) return "";
  const item = findRecord(state.inspect.kind, state.inspect.id);
  if (!item) return "";
  const facts = factsFor(state.inspect.kind, item);
  return `<article class="card inspector">
    <div class="card-head"><h2>${esc(t("kind." + state.inspect.kind))} ${esc(t("detail"))}</h2>
      <button class="btn quiet" type="button" data-action="close-inspect">${esc(t("action.close"))}</button></div>
    <div class="pad">
      <dl class="facts">${facts.map(([name, value]) => `<dt>${esc(name)}</dt><dd>${value}</dd>`).join("")}</dl>
      ${item.lines ? lineTable(item.lines) : ""}
      ${item.destination ? addressBlock(item.destination) : ""}
    </div>
  </article>`;
}
function findRecord(kind, id) {
  const sources = {
    customer: [state.customers, "id"],
    product: [state.products, "id"],
    stock: [state.stock, "productId"],
    order: [state.orders, "orderId"],
    payment: [state.payments, "id"],
    shipment: [state.shipments, "id"],
    checkout: [state.checkouts, "workflowId"],
    event: [state.events, "eventId"]
  };
  const source = sources[kind];
  return source ? source[0].find((row) => row[source[1]] === id) : null;
}
function factsFor(kind, item) {
  if (kind === "customer") return [
    [t("f.name"), bdi(item.fullName)],
    [t("f.email"), ltr(item.email)],
    [t("f.id"), copyValue(item.id)],
    [t("f.created"), when(item.createdAt)]
  ];
  if (kind === "product") return [
    [t("f.sku"), ltr(item.sku)],
    [t("f.name"), bdi(item.name)],
    [t("f.price"), ltr(money(item.unitPrice, item.currency))],
    [t("f.description"), bdi(item.description || "—")],
    [t("f.active"), esc(item.active ? t("yes") : t("no"))],
    [t("f.id"), copyValue(item.id)],
    [t("f.created"), when(item.createdAt)]
  ];
  if (kind === "stock") return [
    [t("f.product"), openId("product", item.productId, productById(item.productId)?.sku || short(item.productId))],
    [t("f.onHand"), String(item.onHandQuantity)],
    [t("f.reserved"), String(item.reservedQuantity)],
    [t("f.available"), String(item.availableQuantity)],
    [t("f.updated"), when(item.updatedAt)]
  ];
  if (kind === "order") return [
    [t("f.id"), copyValue(item.orderId)],
    [t("f.customer"), openId("customer", item.customerId, customerName(item.customerId))],
    [t("f.status"), pill(item.status)],
    [t("f.total"), ltr(money(item.totalAmount, item.currency))],
    [t("f.created"), when(item.createdAt)],
    [t("f.updated"), when(item.updatedAt)]
  ];
  if (kind === "payment") return [
    [t("f.id"), copyValue(item.id)],
    [t("f.order"), openId("order", item.orderId)],
    [t("f.status"), pill(item.status)],
    [t("f.amount"), ltr(money(item.amount, item.currency))],
    [t("f.provider"), ltr(item.providerReference || "—")],
    [t("f.failure"), ltr(item.failureReason || "—")],
    [t("f.created"), when(item.createdAt)]
  ];
  if (kind === "shipment") return [
    [t("f.id"), copyValue(item.id)],
    [t("f.order"), openId("order", item.orderId)],
    [t("f.status"), pill(item.status)],
    [t("f.carrier"), ltr(item.carrierReference || "—")],
    [t("f.updated"), when(item.updatedAt)]
  ];
  if (kind === "checkout") return [
    [t("f.workflow"), copyValue(item.workflowId)],
    [t("f.status"), pill(item.status)],
    [t("f.order"), openId("order", item.orderId)],
    [t("f.payment"), openId("payment", item.paymentId)],
    [t("f.shipment"), openId("shipment", item.shipmentId)],
    [t("f.lastError"), ltr(item.lastError || "—")]
  ];
  return [
    [t("f.event"), copyValue(item.eventId)],
    [t("f.type"), ltr(item.eventType)],
    [t("f.aggregate"), ltr(`${item.aggregateType} ${item.aggregateId}`)],
    [t("f.attempts"), String(item.attempts)],
    [t("f.state"), item.deadLetteredAt ? pill("DEAD_LETTER", "bad") : item.publishedAt ? pill("PUBLISHED", "good") : pill("PENDING", "warn")],
    [t("f.lastError"), ltr(item.lastError || "—")],
    [t("f.created"), when(item.createdAt)],
    [t("f.published"), when(item.publishedAt)],
    [t("f.dead"), when(item.deadLetteredAt)]
  ];
}
function lineTable(lines) {
  return `<h3>${esc(t("lines"))}</h3>${table([t("col.sku"), t("col.product"), t("col.qty"), t("col.price")], lines.map((line) => [
    ltr(line.sku), bdi(line.productName), String(line.quantity), ltr(money(line.subtotal, line.currency))
  ]))}`;
}
function addressBlock(address) {
  return `<h3>${esc(t("destination"))}</h3><p>${bdi([address.recipientName, address.addressLine1, address.addressLine2, address.city, address.postalCode, address.countryCode, address.phone].filter(Boolean).join("، "))}</p>`;
}
function copyValue(id) {
  if (!id) return "—";
  return `<span class="copy"><code>${esc(id)}</code><button class="btn quiet" type="button" data-action="copy" data-id="${esc(id)}">${esc(t("action.copy"))}</button></span>`;
}
function when(value) {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return ltr(value);
  const locale = state.lang === "fa" ? "fa-IR-u-ca-gregory-nu-latn" : "en-GB";
  return ltr(date.toLocaleString(locale, { dateStyle: "medium", timeStyle: "short" }));
}
function bdi(value) { return `<bdi dir="auto">${esc(value)}</bdi>`; }
function ltr(value) { return `<bdi dir="ltr">${esc(value)}</bdi>`; }
function customerName(id) { return state.customers.find((item) => item.id === id)?.fullName || short(id); }
function esc(value) {
  return String(value ?? "").replace(/[&<>"']/g, (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[char]));
}

async function api(path, options = {}) {
  const headers = { Accept: "application/json" };
  if (state.accessToken) headers.Authorization = "Bearer " + state.accessToken;
  const init = { method: options.method || "GET", headers };
  if (options.body !== undefined) {
    headers["Content-Type"] = "application/json";
    init.body = JSON.stringify(options.body);
  }
  const response = await fetch(path, init);
  const text = await response.text();
  const body = text ? JSON.parse(text) : null;
  if (!response.ok) throw new Error(body?.detail || body?.title || `Request failed (${response.status})`);
  return body;
}

let toastTimer;
function notify(message) {
  toast.hidden = false;
  toast.textContent = message;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { toast.hidden = true; }, 4200);
}

boot();
