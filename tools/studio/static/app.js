// Citadel Studio front end. No dependencies; every value from the server goes in as text (never innerHTML).
"use strict";

const ACTIVE = ["queued", "compiling", "rendering", "waiting"];
const STATE_ES = { done: "listo", error: "error", queued: "en cola", compiling: "compilando", rendering: "renderizando", waiting: "esperando", missing: "no existe" };
const $ = (sel, root = document) => root.querySelector(sel);

function h(tag, attrs, ...kids) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs || {})) {
    if (v == null || v === false) continue;
    if (k === "class") el.className = v;
    else if (k.startsWith("on")) el.addEventListener(k.slice(2), v);
    else if (k === "dataset") Object.assign(el.dataset, v);
    else if (k === "style") el.style.cssText = v; // CSSOM: allowed by the page's CSP, style="" is not
    else if (v === true) el.setAttribute(k, "");
    else el.setAttribute(k, String(v));
  }
  for (const kid of kids.flat(Infinity)) {
    if (kid == null || kid === false) continue;
    el.append(kid instanceof Node ? kid : document.createTextNode(String(kid)));
  }
  return el;
}

async function api(path) {
  const r = await fetch(path, { headers: { Accept: "application/json" }, cache: "no-store" });
  const j = await r.json().catch(() => ({}));
  if (!r.ok) throw new Error(j.error || r.statusText);
  return j;
}

async function post(path, body) {
  const r = await fetch(path, {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-Studio": "1" },
    body: JSON.stringify(body),
  });
  const j = await r.json().catch(() => ({}));
  if (!r.ok) throw new Error(j.error || r.statusText);
  return j;
}

const fileUrl = (id, name) => "/files/" + encodeURIComponent(id) + "/" + encodeURIComponent(name);
const agentUrl = (a) => "/agent/" + encodeURIComponent(a);

function ago(ms) {
  if (!ms) return "—";
  const s = Math.max(0, Math.round((Date.now() - ms) / 1000));
  if (s < 60) return "hace " + s + " s";
  if (s < 3600) return "hace " + Math.round(s / 60) + " min";
  if (s < 86400) return "hace " + Math.round(s / 3600) + " h";
  return "hace " + Math.round(s / 86400) + " d";
}
function dur(a, b) {
  if (!a || !b || b < a) return "";
  const s = Math.round((b - a) / 1000);
  return s < 90 ? s + " s" : Math.floor(s / 60) + " min " + (s % 60) + " s";
}
function clock(ms) {
  if (!ms) return "";
  const d = new Date(ms);
  return d.toLocaleString("es", { day: "2-digit", month: "short", hour: "2-digit", minute: "2-digit", second: "2-digit" });
}
function pill(state, text) {
  return h("span", { class: "pill " + (state || "") }, text || STATE_ES[state] || state || "?");
}
function toast(text) {
  const t = h("div", { class: "toast", role: "status" }, text);
  document.body.append(t);
  setTimeout(() => t.remove(), 2600);
}
// view name of a PNG: "before_h0_iso_se.png" -> "iso_se", "x_plan.png" -> "plan"
function viewKey(name) {
  const base = name.replace(/\.png$/, "").replace(/^compare_/, "");
  const m = base.match(/(iso_(?:se|sw|ne|nw))$/);
  if (m) return m[1];
  const parts = base.split("_");
  return parts[parts.length - 1];
}

/* ------------------------------------------------------------ daemon heartbeat */

// daemon.running: array of {id, agent, kind, priority, state, message} (older daemons: one id string)
function runningJobs(d) {
  const r = d && d.running;
  if (Array.isArray(r)) return r.map((e) => (typeof e === "string" ? { id: e } : e)).filter((e) => e && e.id);
  return typeof r === "string" && r ? [{ id: r }] : [];
}

function renderDaemon(d) {
  const box = $("#daemon");
  box.replaceChildren();
  if (!d) return;
  const load = typeof d.load1 === "number" ? d.load1 : null;
  const limit = typeof d.loadLimit === "number" ? d.loadLimit : 8;
  const pct = load == null ? 0 : Math.min(100, (load / (limit * 1.5)) * 100);
  box.append(
    h("span", {}, "Render ", pill(d.alive ? "running" : "stopped", d.alive ? "activo" : "detenido")),
    h("span", {}, "cola ", h("b", {}, d.queued ?? "—")),
    ...runningJobs(d).map((j) => h("span", {
      class: "mono small",
      title: [j.agent, j.kind, j.priority, j.state, j.message].filter(Boolean).join(" · ") || "renderizando ahora",
    }, j.state === "compiling" ? "⚙ " : "▶ ", j.id)),
    typeof d.workers === "number" ? h("span", { class: "small", title: "trabajos en curso / workers" },
      "en curso ", h("b", {}, runningJobs(d).length + "/" + d.workers)) : null,
    d.loadPaused ? h("span", { class: "small", title: "carga alta: un solo trabajo a la vez" }, "⏸ carga") : null,
    h("span", { title: "carga 1 min / límite de pausa" },
      "carga ", h("b", {}, load == null ? "—" : load.toFixed(2)), " / " + limit + " ",
      h("span", { class: "meter" + (load != null && load > limit ? " hot" : "") }, h("i", { style: "width:" + pct + "%" }))),
  );
}

/* ------------------------------------------------------------ viewer (zoom + drag) */

const viewer = (() => {
  const root = $("#viewer"), stage = $("#viewer-stage"), img = $("#viewer-img");
  let list = [], idx = 0, scale = 1, x = 0, y = 0, onRef = null;
  const pointers = new Map();
  let pinch = null;

  function apply() { img.style.transform = `translate(${x}px, ${y}px) scale(${scale})`; }
  function fit() {
    const w = img.naturalWidth || 1, hgt = img.naturalHeight || 1;
    const sw = stage.clientWidth, sh = stage.clientHeight;
    scale = Math.min(sw / w, sh / hgt, 8);
    x = (sw - w * scale) / 2; y = (sh - hgt * scale) / 2;
    apply();
  }
  function zoomAt(f, cx, cy) {
    const ns = Math.min(32, Math.max(0.05, scale * f));
    x = cx - (cx - x) * (ns / scale); y = cy - (cy - y) * (ns / scale);
    scale = ns; apply();
  }
  function center() { return [stage.clientWidth / 2, stage.clientHeight / 2]; }
  function show(i) {
    if (!list.length) return;
    idx = (i + list.length) % list.length;
    const it = list[idx];
    $("#viewer-title").textContent = it.title || it.name;
    $("#viewer-open").href = it.url;
    img.onload = fit;
    img.src = it.url;
    if (img.complete && img.naturalWidth) fit();
  }
  function open(items, i, refCb) {
    list = items; onRef = refCb || null;
    $('[data-v="ref"]', root).hidden = !onRef;
    root.hidden = false; document.body.style.overflow = "hidden";
    show(i || 0);
  }
  function close() { root.hidden = true; document.body.style.overflow = ""; img.removeAttribute("src"); }

  root.addEventListener("click", (e) => {
    const b = e.target.closest("[data-v]");
    if (!b) return;
    const v = b.dataset.v, [cx, cy] = center();
    if (v === "close") close();
    else if (v === "prev") show(idx - 1);
    else if (v === "next") show(idx + 1);
    else if (v === "in") zoomAt(1.5, cx, cy);
    else if (v === "out") zoomAt(1 / 1.5, cx, cy);
    else if (v === "fit") fit();
    else if (v === "one") { scale = 1; zoomAt(1, cx, cy); x = cx - img.naturalWidth / 2; y = cy - img.naturalHeight / 2; apply(); }
    else if (v === "ref" && onRef) { onRef(list[idx]); close(); }
  });
  stage.addEventListener("wheel", (e) => {
    e.preventDefault();
    const r = stage.getBoundingClientRect();
    zoomAt(Math.exp(-e.deltaY * (e.ctrlKey ? 0.01 : 0.0018)), e.clientX - r.left, e.clientY - r.top);
  }, { passive: false });
  stage.addEventListener("pointerdown", (e) => {
    stage.setPointerCapture(e.pointerId);
    pointers.set(e.pointerId, { x: e.clientX, y: e.clientY });
    stage.classList.add("drag");
    if (pointers.size === 2) {
      const [a, b] = [...pointers.values()];
      pinch = { d: Math.hypot(a.x - b.x, a.y - b.y) };
    }
  });
  stage.addEventListener("pointermove", (e) => {
    const p = pointers.get(e.pointerId);
    if (!p) return;
    if (pointers.size === 2 && pinch) {
      p.x = e.clientX; p.y = e.clientY;
      const [a, b] = [...pointers.values()];
      const d = Math.hypot(a.x - b.x, a.y - b.y);
      const r = stage.getBoundingClientRect();
      zoomAt(d / pinch.d, (a.x + b.x) / 2 - r.left, (a.y + b.y) / 2 - r.top);
      pinch.d = d;
      return;
    }
    x += e.clientX - p.x; y += e.clientY - p.y;
    p.x = e.clientX; p.y = e.clientY;
    apply();
  });
  const up = (e) => {
    pointers.delete(e.pointerId);
    if (pointers.size < 2) pinch = null;
    if (!pointers.size) stage.classList.remove("drag");
  };
  stage.addEventListener("pointerup", up);
  stage.addEventListener("pointercancel", up);
  stage.addEventListener("dblclick", (e) => {
    const r = stage.getBoundingClientRect();
    zoomAt(2, e.clientX - r.left, e.clientY - r.top);
  });
  document.addEventListener("keydown", (e) => {
    if (root.hidden) return;
    const [cx, cy] = center();
    if (e.key === "Escape") close();
    else if (e.key === "ArrowLeft") show(idx - 1);
    else if (e.key === "ArrowRight") show(idx + 1);
    else if (e.key === "+" || e.key === "=") zoomAt(1.5, cx, cy);
    else if (e.key === "-") zoomAt(1 / 1.5, cx, cy);
    else if (e.key === "0") fit();
    else if (e.key === "1") { $('[data-v="one"]', root).click(); }
  });
  window.addEventListener("resize", () => { if (!root.hidden) fit(); });
  return { open };
})();

/* ------------------------------------------------------------ shared bits */

function argsView(args) {
  return h("div", { class: "args" }, args.map((a) => h("code", { class: a.startsWith("--") ? "flag" : "" }, a)));
}

function imagesView(job, onOpen) {
  if (!job.images.length) return null;
  return h("div", { class: "imgs" }, job.images.map((name, i) =>
    h("figure", { class: name.startsWith("compare_") ? "compare" : "", onclick: () => onOpen(i) },
      h("img", { src: fileUrl(job.id, name), alt: name, loading: "lazy", decoding: "async" }),
      h("figcaption", { title: name }, name.startsWith("compare_") ? name.slice(8) : name))));
}

function jobItems(job) {
  return job.images.map((name) => ({ url: fileUrl(job.id, name), name, id: job.id, title: job.id + " / " + name }));
}

/* ------------------------------------------------------------ render form */

function renderForm(opts) {
  const { forAgent, worktree, baseline, onSubmitted } = opts;
  const msg = h("div", { class: "msg", role: "status" });
  const live = h("div", { class: "stack" });
  const seg = (name, values, checked) => h("div", { class: "seg" }, values.map(([v, label]) =>
    h("label", {}, h("input", { type: "radio", name, value: v, checked: v === checked }), h("span", {}, label))));
  const targetFields = {
    group: h("label", {}, "Grupo (--group)", h("input", { type: "text", name: "group", placeholder: "p. ej. hub", autocomplete: "off" })),
    anchor: h("div", { class: "row" },
      h("label", {}, "Anchor (--anchor)", h("input", { type: "text", name: "anchor", placeholder: "p. ej. walk_hub_0", autocomplete: "off" })),
      h("label", {}, "Radio (--radius)", h("input", { type: "number", name: "radius", value: "12", min: "1", max: "256" }))),
    region: h("label", {}, "Región (--region x1,y1,z1:x2,y2,z2)", h("input", { type: "text", name: "region", placeholder: "-30,-2,-62:30,14,-14", autocomplete: "off" })),
    all: h("p", { class: "muted small", style: "margin:0" }, "Todo el plan, con --report."),
  };
  const targetBox = h("div", {});
  const form = h("form", { class: "stack", autocomplete: "off" },
    h("div", { class: "row" },
      h("label", {}, "Layout", h("input", { type: "text", name: "layout", value: "hybrid-ringcampus" })),
      h("label", {}, "Proyectos", h("input", { type: "text", name: "projects", value: "4,10,20", placeholder: "4,10,20" })),
      h("label", {}, "Nombre (--name, opcional)", h("input", { type: "text", name: "name", placeholder: "p. ej. after" }))),
    h("label", {}, "Objetivo", seg("target", [["all", "Todo"], ["group", "Grupo"], ["anchor", "Anchor + radio"], ["region", "Región"]], "all")),
    targetBox,
    h("div", { class: "row" },
      h("label", {}, "Vistas (--only, opcional)", h("input", { type: "text", name: "only", placeholder: "iso_se,iso_nw,plan" })),
      h("label", {}, "Calidad", seg("priority", [["preview", "preview"], ["final", "final"]], "preview"))),
    h("label", { class: "inline" }, h("input", { type: "checkbox", name: "report", checked: true }), "Agregar --report"),
    h("label", {}, "Worktree", h("input", { type: "text", name: "worktree", value: worktree || baseline || "" }),
      h("span", { class: "small" }, worktree ? "El del agente." : "Por defecto, render-baseline (main).")),
    h("div", { class: "actions" }, h("button", { class: "primary", type: "submit" }, "Pedir render"), msg),
    live);
  const syncTarget = () => {
    const t = form.elements.target.value;
    targetBox.replaceChildren(targetFields[t]);
    form.elements.report.disabled = t === "all";
    if (t === "all") form.elements.report.checked = true;
  };
  form.addEventListener("change", (e) => { if (e.target.name === "target") syncTarget(); });
  syncTarget();
  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    const f = form.elements, btn = form.querySelector("button[type=submit]");
    const body = {
      for: forAgent || "", layout: f.layout.value, projects: f.projects.value, name: f.name.value,
      target: f.target.value, only: f.only.value, priority: f.priority.value, report: f.report.checked,
      worktree: f.worktree.value,
    };
    // the inputs of the other target types are detached: read them from their own nodes
    for (const k of ["group", "anchor", "radius", "region"]) {
      const el = Object.values(targetFields).map((n) => n.querySelector(`[name=${k}]`)).find(Boolean);
      if (el) body[k] = el.value;
    }
    btn.disabled = true; msg.className = "msg"; msg.textContent = "Enviando…";
    try {
      const r = await post("/api/render", body);
      msg.className = "msg ok"; msg.textContent = "Petición " + r.id + " enviada.";
      watchJob(r.id, live);
      if (onSubmitted) onSubmitted(r);
    } catch (err) {
      msg.className = "msg err"; msg.textContent = err.message;
    } finally {
      btn.disabled = false;
    }
  });
  return form;
}

function watchJob(id, box) {
  const card = h("div", { class: "card job" });
  box.prepend(card);
  let timer = null;
  const tick = async () => {
    let j;
    try { j = await api("/api/job/" + encodeURIComponent(id)); }
    catch (e) { j = { id, state: "waiting", message: e.message, images: [], args: [] }; }
    card.replaceChildren(
      h("div", { class: "job-head" }, pill(j.state), h("span", { class: "job-id" }, id),
        j.position != null && j.state === "queued" ? h("span", { class: "small muted" }, "posición " + j.position) : null,
        j.startedAt && j.finishedAt ? h("span", { class: "small muted" }, dur(j.startedAt, j.finishedAt)) : null),
      j.message ? h("div", { class: "small muted" }, j.message) : null,
      j.args && j.args.length ? argsView(j.args) : null,
      imagesView(j, (i) => viewer.open(jobItems(j), i)),
      j.report ? h("a", { href: fileUrl(id, "report.txt"), target: "_blank", rel: "noopener" }, "report.txt") : null,
      j.state === "error" && j.log ? h("pre", { class: "log" }, j.log) : null);
    if (ACTIVE.includes(j.state)) timer = setTimeout(tick, 3000);
  };
  tick();
  return () => clearTimeout(timer);
}

/* ------------------------------------------------------------ pages */

let pageTimer = null;
function every(ms, fn) {
  clearInterval(pageTimer);
  fn();
  pageTimer = setInterval(() => { if (!document.hidden) fn(); }, ms);
}

function dashboard(app) {
  const grid = h("div", { class: "grid" });
  const status = h("span", { class: "small muted" });
  app.replaceChildren(
    h("div", { class: "page-head" },
      h("div", {}, h("h1", {}, "Tablero"), h("div", { class: "muted small" }, "El swarm de diseño de la ciudadela (hybrid-ringcampus).")),
      status),
    grid);
  let last = "";
  every(10000, async () => {
    let s;
    try { s = await api("/api/state"); } catch (e) { status.textContent = "sin conexión: " + e.message; return; }
    renderDaemon(s.daemon);
    status.textContent = "actualizado " + new Date().toLocaleTimeString("es");
    const sig = JSON.stringify(s.agents);
    if (sig === last) { // only the relative times change
      grid.querySelectorAll("[data-ago]").forEach((el) => { el.textContent = ago(+el.dataset.ago); });
      return;
    }
    last = sig;
    if (!s.agents.length) {
      grid.replaceChildren(h("p", { class: "muted" }, "Aún no hay agentes con jobs en el outbox."));
      return;
    }
    grid.replaceChildren(...s.agents.map((a) => {
      const l = a.last;
      const imgs = a.lastImages;
      return h("a", { class: "card", href: agentUrl(a.agent) },
        h("div", { class: "card-head" },
          h("h3", { title: a.agent }, a.title),
          l ? pill(l.state) : pill("", "sin jobs")),
        h("div", { class: "card-meta" },
          h("span", { class: "mono" }, a.agent),
          a.phase ? h("span", {}, a.phase) : null,
          h("span", {}, a.jobs + " jobs · " + a.renders + " imágenes"),
          l ? h("span", { "data-ago": l.when, title: clock(l.when) }, ago(l.when)) : null,
          a.feedback.unread ? h("span", { class: "pill accent", title: "notas sin leer por el agente" }, a.feedback.unread + " sin leer") : null),
        l && l.message ? h("div", { class: "card-msg", title: l.message }, l.message) : null,
        h("div", { class: "thumbs" }, imgs
          ? imgs.images.slice(0, 3).map((n) => h("img", { src: fileUrl(imgs.id, n), alt: n, loading: "lazy", decoding: "async", title: n }))
          : h("div", { class: "empty" }, "sin imágenes todavía")));
    }));
  });
}

function renderPage(app) {
  app.replaceChildren(
    h("h1", {}, "Pedir un render"),
    h("p", { class: "muted small" }, "Se envía al servicio de render compartido como facundo-studio (una petición pendiente a la vez)."),
    h("div", { class: "card" }, renderForm({ forAgent: "", worktree: "", baseline: "/home/user/.t3/worktrees/t3craft/render-baseline" })));
  every(10000, async () => { try { renderDaemon((await api("/api/state")).daemon); } catch (e) { /* header only */ } });
}

function agentPage(app, agent) {
  const head = h("div", { class: "page-head" });
  const tabsEl = h("div", { class: "tabs", role: "tablist" });
  const panes = {
    history: h("div", {}),
    compare: h("div", {}),
    feedback: h("div", {}),
    render: h("div", {}),
  };
  const labels = { history: "Historial", compare: "Comparar", feedback: "Feedback", render: "Pedir render" };
  let tab = (location.hash || "#history").slice(1);
  if (!panes[tab]) tab = "history";
  const counts = {};
  function drawTabs() {
    tabsEl.replaceChildren(...Object.keys(panes).map((k) => h("button", {
      type: "button", class: k === tab ? "on" : "", role: "tab",
      onclick: () => { tab = k; history.replaceState(null, "", "#" + k); drawTabs(); },
    }, labels[k], counts[k] ? h("span", { class: "count" }, counts[k]) : null)));
    for (const [k, p] of Object.entries(panes)) p.hidden = k !== tab;
  }
  app.replaceChildren(head, tabsEl, ...Object.values(panes));
  drawTabs();

  let data = null, lastJobs = "", lastNotes = "", formMade = false;
  // feedback state that survives refreshes
  const fb = { ref: "" };
  const refBox = h("div", {});
  const noteText = h("textarea", { name: "text", placeholder: "Indicaciones para el agente: qué cambiar, qué mantener…", maxlength: "8000" });
  const fbMsg = h("div", { class: "msg", role: "status" });
  const notesBox = h("div", { class: "notes" });
  function setRef(item) {
    fb.ref = item ? item.url : "";
    refBox.replaceChildren(item ? h("span", { class: "ref-chip" },
      h("img", { src: item.url, alt: "" }), h("span", { class: "mono" }, item.title || item.name),
      h("button", { type: "button", onclick: () => setRef(null) }, "quitar")) : h("span", { class: "small muted" }, "Sin imagen de referencia. Ábrela en el visor y pulsa «Nota», o elige abajo."));
  }
  const refSelect = h("select", { onchange: (e) => {
    const v = e.target.value; if (!v) return;
    const [id, name] = v.split("\u0000");
    setRef({ url: fileUrl(id, name), name, title: id + " / " + name });
    e.target.value = "";
  } });
  const refToNote = (item) => { setRef(item); tab = "feedback"; history.replaceState(null, "", "#feedback"); drawTabs(); noteText.focus(); };
  setRef(null);
  panes.feedback.append(
    h("div", { class: "card stack" },
      h("h3", {}, "Nueva nota"),
      noteText,
      refBox,
      h("label", {}, "Imagen de referencia", refSelect),
      h("div", { class: "actions" },
        h("button", { class: "primary", type: "button", onclick: async (e) => {
          const btn = e.currentTarget;
          btn.disabled = true; fbMsg.className = "msg"; fbMsg.textContent = "Guardando…";
          try {
            await post("/api/feedback", { agent, text: noteText.value, ref: fb.ref });
            noteText.value = ""; setRef(null);
            fbMsg.className = "msg ok"; fbMsg.textContent = "Nota guardada en feedback/" + agent + ".md";
            refresh();
          } catch (err) { fbMsg.className = "msg err"; fbMsg.textContent = err.message; }
          finally { btn.disabled = false; }
        } }, "Guardar nota"),
        fbMsg)),
    h("div", { class: "section-title" }, h("h2", {}, "Notas"),
      h("a", { class: "small", href: "/feedback/" + encodeURIComponent(agent) + ".md", target: "_blank", rel: "noopener" }, "ver archivo")),
    notesBox);

  const cmp = { a: "", b: "", ia: "", ib: "", ibManual: false, mode: "side", pos: 50 };
  function drawCompare() {
    const jobs = (data?.jobs || []).filter((j) => j.images.length);
    const box = panes.compare;
    if (jobs.length < 1) { box.replaceChildren(h("p", { class: "muted" }, "Aún no hay jobs con imágenes.")); return; }
    if (!jobs.find((j) => j.id === cmp.a)) cmp.a = (jobs[1] || jobs[0]).id;
    if (!jobs.find((j) => j.id === cmp.b)) cmp.b = jobs[0].id;
    const ja = jobs.find((j) => j.id === cmp.a), jb = jobs.find((j) => j.id === cmp.b);
    const plain = (j) => j.images.filter((n) => !n.startsWith("compare_"));
    if (!plain(ja).includes(cmp.ia)) cmp.ia = plain(ja)[0] || ja.images[0];
    // B: same view name first
    const key = viewKey(cmp.ia);
    const bList = [...plain(jb)].sort((x, y) => (viewKey(y) === key) - (viewKey(x) === key));
    if (!bList.includes(cmp.ib) || (!cmp.ibManual && viewKey(cmp.ib) !== key && bList.some((n) => viewKey(n) === key))) cmp.ib = bList[0] || jb.images[0];
    const jobSel = (val, set) => h("select", { onchange: (e) => { set(e.target.value); drawCompare(); } },
      jobs.map((j) => h("option", { value: j.id, selected: j.id === val }, clock(j.when) + " · " + j.id)));
    const imgSel = (list, val, set) => h("select", { onchange: (e) => { set(e.target.value); drawCompare(); } },
      list.map((n) => h("option", { value: n, selected: n === val }, n + (viewKey(n) === key ? "" : "  (otra vista)"))));
    const ua = fileUrl(cmp.a, cmp.ia), ub = fileUrl(cmp.b, cmp.ib);
    let view;
    if (cmp.mode === "side") {
      view = h("div", { class: "cmp-side" },
        h("figure", { onclick: () => viewer.open([{ url: ua, name: cmp.ia, title: "antes · " + cmp.a + " / " + cmp.ia }, { url: ub, name: cmp.ib, title: "después · " + cmp.b + " / " + cmp.ib }], 0, refToNote) },
          h("img", { src: ua, alt: "antes" }), h("figcaption", {}, "antes")),
        h("figure", { onclick: () => viewer.open([{ url: ua, name: cmp.ia, title: "antes · " + cmp.a + " / " + cmp.ia }, { url: ub, name: cmp.ib, title: "después · " + cmp.b + " / " + cmp.ib }], 1, refToNote) },
          h("img", { src: ub, alt: "después" }), h("figcaption", {}, "después")));
    } else {
      const topWrap = h("div", { class: "top-img" }, h("img", { src: ub, alt: "después" }));
      const handle = h("div", { class: "handle" });
      const slide = h("div", { class: "cmp-slide" }, h("img", { src: ua, alt: "antes" }), topWrap, handle,
        h("span", { class: "tag", style: "left:6px" }, "antes"), h("span", { class: "tag", style: "right:6px" }, "después"));
      const setPos = (p) => {
        cmp.pos = Math.max(0, Math.min(100, p));
        topWrap.style.clipPath = `inset(0 0 0 ${cmp.pos}%)`;
        handle.style.left = cmp.pos + "%";
        range.value = cmp.pos;
      };
      const range = h("input", { type: "range", min: "0", max: "100", step: "0.5", value: String(cmp.pos), oninput: (e) => setPos(+e.target.value), "aria-label": "deslizador antes/después" });
      const drag = (e) => { const r = slide.getBoundingClientRect(); setPos(((e.clientX - r.left) / r.width) * 100); };
      slide.addEventListener("pointerdown", (e) => { slide.setPointerCapture(e.pointerId); drag(e); slide.onpointermove = drag; });
      slide.addEventListener("pointerup", () => { slide.onpointermove = null; });
      view = h("div", { class: "stack" }, slide, range);
      setPos(cmp.pos);
    }
    box.replaceChildren(h("div", { class: "card stack" },
      h("div", { class: "cmp-pickers" },
        h("div", { class: "stack" }, h("label", {}, "Antes: job", jobSel(cmp.a, (v) => { cmp.a = v; cmp.ibManual = false; }))
          , h("label", {}, "Imagen", imgSel(plain(ja).length ? plain(ja) : ja.images, cmp.ia, (v) => { cmp.ia = v; cmp.ibManual = false; }))),
        h("div", { class: "stack" }, h("label", {}, "Después: job", jobSel(cmp.b, (v) => { cmp.b = v; cmp.ibManual = false; }))
          , h("label", {}, "Imagen", imgSel(bList.length ? bList : jb.images, cmp.ib, (v) => { cmp.ib = v; cmp.ibManual = true; })))),
      h("div", { class: "seg" }, [["side", "Lado a lado"], ["slide", "Deslizador"]].map(([v, l]) =>
        h("label", {}, h("input", { type: "radio", name: "cmpmode", value: v, checked: cmp.mode === v, onchange: () => { cmp.mode = v; drawCompare(); } }), h("span", {}, l)))),
      view,
      jobs.length < 2 ? h("p", { class: "small muted" }, "Solo hay un job con imágenes: compara dos vistas del mismo job o espera al siguiente.") : null));
  }

  function drawHistory() {
    const jobs = data.jobs;
    if (!jobs.length) { panes.history.replaceChildren(h("p", { class: "muted" }, "Sin jobs en el outbox (se borran a las 24 h).")); return; }
    panes.history.replaceChildren(...jobs.map((j) => h("div", { class: "card job" },
      h("div", { class: "job-head" },
        pill(j.state),
        h("b", { title: clock(j.when) }, clock(j.when)),
        h("span", { class: "small muted", "data-ago": j.when }, ago(j.when)),
        j.startedAt && j.finishedAt ? h("span", { class: "small muted", title: "duración del render" }, "⏱ " + dur(j.startedAt, j.finishedAt)) : null,
        j.queuedAt && j.startedAt ? h("span", { class: "small muted", title: "espera en cola" }, "cola " + dur(j.queuedAt, j.startedAt)) : null,
        h("span", { class: "pill" }, j.priority),
        j.cached ? h("span", { class: "pill" }, "caché") : null,
        h("span", { class: "grow" }),
        h("span", { class: "job-id" }, j.id)),
      j.message ? h("div", { class: "small muted" }, j.message) : null,
      j.args.length ? argsView(j.args) : null,
      j.worktree ? h("div", { class: "small muted mono" }, j.worktree) : null,
      imagesView(j, (i) => viewer.open(jobItems(j), i, refToNote)),
      h("div", { class: "actions small", style: "margin-top:8px" },
        j.report ? h("a", { href: fileUrl(j.id, "report.txt"), target: "_blank", rel: "noopener" }, "report.txt") : null,
        ...j.files.filter((n) => n !== "report.txt" && n !== "status.json").map((n) => h("a", { href: fileUrl(j.id, n), target: "_blank", rel: "noopener", class: "muted" }, n))),
      j.log && j.state === "error" ? h("details", { class: "log" }, h("summary", {}, "log"), h("pre", { class: "log" }, j.log)) : null)));
  }

  function drawNotes() {
    const notes = data.notes.slice().reverse();
    counts.feedback = data.notes.filter((n) => !n.read).length;
    drawTabs();
    notesBox.replaceChildren(...(notes.length ? notes.map((n) => h("div", { class: "note" + (n.read ? " read" : "") },
      h("div", { class: "note-head" }, h("b", {}, n.at), n.read ? pill("done", "leída " + n.read) : pill("queued", "sin leer")),
      h("div", { class: "note-text" }, n.text),
      n.refUrl ? h("img", { src: n.refUrl, alt: n.ref, title: n.ref, onclick: () => viewer.open([{ url: n.refUrl, name: n.ref, title: n.ref }], 0) })
        : n.ref ? h("div", { class: "small muted mono" }, n.ref + " (ya no está en el outbox)") : null))
      : [h("p", { class: "muted" }, "Todavía no hay notas.")]));
  }

  function drawRefSelect() {
    refSelect.replaceChildren(h("option", { value: "" }, "— elegir una imagen —"),
      ...data.jobs.filter((j) => j.images.length).slice(0, 15).map((j) => h("optgroup", { label: clock(j.when) + " · " + j.id },
        j.images.map((n) => h("option", { value: j.id + "\u0000" + n }, n)))));
  }

  async function refresh() {
    try { data = await api("/api/agent/" + encodeURIComponent(agent)); }
    catch (e) { head.replaceChildren(h("h1", {}, agent), h("p", { class: "msg err" }, e.message)); return; }
    renderDaemon(data.daemon);
    head.replaceChildren(
      h("div", {},
        h("div", { class: "small" }, h("a", { href: "/" }, "← Tablero")),
        h("h1", {}, data.title),
        h("div", { class: "muted small" }, h("span", { class: "mono" }, agent), data.phase ? " · " + data.phase : "",
          data.worktree ? h("span", { class: "mono" }, " · " + data.worktree) : "")),
      h("div", { class: "actions" },
        data.brief ? h("a", { class: "btn", href: "/brief/" + encodeURIComponent(agent), target: "_blank", rel: "noopener" }, "Brief") : null));
    const js = JSON.stringify(data.jobs);
    if (js !== lastJobs) { lastJobs = js; drawHistory(); drawCompare(); drawRefSelect(); }
    else panes.history.querySelectorAll("[data-ago]").forEach((el) => { el.textContent = ago(+el.dataset.ago); });
    const ns = JSON.stringify(data.notes);
    if (ns !== lastNotes) { lastNotes = ns; drawNotes(); }
    if (!formMade) {
      formMade = true;
      panes.render.append(h("div", { class: "card" },
        h("p", { class: "muted small", style: "margin-top:0" }, "Se envía como ", h("code", {}, agent.startsWith("facundo-") ? agent : "facundo-" + agent.replace(/^citadel-/, "")),
          " (una petición pendiente a la vez). El agente no se entera: déjale una nota si quieres que lo vea."),
        renderForm({ forAgent: agent, worktree: data.worktree, baseline: data.baselineWorktree })));
    }
  }
  every(10000, refresh);
}

/* ------------------------------------------------------------ router */

function route() {
  const app = $("#app");
  const p = location.pathname;
  document.querySelectorAll("[data-nav]").forEach((a) => a.classList.toggle("on", a.dataset.nav === p));
  if (p === "/render") { document.title = "Pedir render · Citadel Studio"; return renderPage(app); }
  const m = p.match(/^\/agent\/([A-Za-z0-9][A-Za-z0-9._-]*)$/);
  if (m) { document.title = m[1] + " · Citadel Studio"; return agentPage(app, decodeURIComponent(m[1])); }
  document.title = "Citadel Studio";
  return dashboard(app);
}
route();
