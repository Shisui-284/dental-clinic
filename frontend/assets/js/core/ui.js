/** Helper DOM/UI dùng chung: tạo element, toast, modal, drawer. */

export function esc(s) {
  return String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}

export function el(html) {
  const t = document.createElement("template");
  t.innerHTML = html.trim();
  return t.content.firstElementChild;
}

export function qs(sel, root = document) {
  return root.querySelector(sel);
}

export function qsa(sel, root = document) {
  return Array.from(root.querySelectorAll(sel));
}

export function on(root, evt, sel, fn) {
  root.addEventListener(evt, (e) => {
    const t = e.target.closest(sel);
    if (t && root.contains(t)) fn(e, t);
  });
}

export function toast(message, kind = "") {
  const root = document.getElementById("toast-root");
  const n = el(`<div class="toast ${kind}">${esc(message)}</div>`);
  root.appendChild(n);
  setTimeout(() => {
    n.style.opacity = "0";
    n.style.transition = "opacity .2s";
    setTimeout(() => n.remove(), 220);
  }, 3200);
}

/** Modal tuỳ biến, trả Promise resolve khi đóng. actions: [{label, value, kind}] */
export function modal({ title, body = "", actions = [{ label: "Đóng", value: null }], width }) {
  return new Promise((resolve) => {
    const root = document.getElementById("modal-root");
    const wrap = el(`
      <div class="modal-wrap">
        <div class="modal" role="dialog" aria-modal="true" ${width ? `style="width:${width}px"` : ""}>
          <h3>${esc(title || "")}</h3>
          <div class="modal-body">${body}</div>
          <div class="foot"></div>
        </div>
      </div>`);
    const foot = wrap.querySelector(".foot");
    actions.forEach((a) => {
      const b = el(`<button class="btn ${a.kind || ""}">${esc(a.label)}</button>`);
      b.onclick = () => {
        close();
        resolve(a.value);
      };
      foot.appendChild(b);
    });
    function close() {
      wrap.remove();
      document.removeEventListener("keydown", onKey);
    }
    function onKey(e) { if (e.key === "Escape") { close(); resolve(null); } }
    document.addEventListener("keydown", onKey);
    wrap.addEventListener("click", (e) => { if (e.target === wrap) { close(); resolve(null); } });
    root.appendChild(wrap);
  });
}

/** Hộp xác nhận hành động phá huỷ (5.2 §7.3) */
export function confirmDanger({ title, text, confirmLabel = "Xác nhận", requireReason = false, reasonLabel = "Lý do" }) {
  return new Promise((resolve) => {
    const body = `
      <p class="muted">${esc(text || "")}</p>
      ${requireReason ? `<label class="field"><span class="lbl">${esc(reasonLabel)} <span class="req">*</span></span>
        <textarea data-reason placeholder="Nhập lý do..."></textarea>
        <div class="err hidden" data-err>Bắt buộc nhập lý do.</div></label>` : ""}`;
    const root = document.getElementById("modal-root");
    const wrap = el(`
      <div class="modal-wrap"><div class="modal" role="dialog" aria-modal="true">
        <h3>${esc(title)}</h3>${body}
        <div class="foot">
          <button class="btn" data-cancel>Không</button>
          <button class="btn danger" data-ok>${esc(confirmLabel)}</button>
        </div>
      </div></div>`);
    const ta = wrap.querySelector("[data-reason]");
    const err = wrap.querySelector("[data-err]");
    wrap.querySelector("[data-cancel]").onclick = () => { wrap.remove(); resolve(null); };
    wrap.querySelector("[data-ok]").onclick = () => {
      if (requireReason && !ta.value.trim()) {
        err.classList.remove("hidden");
        ta.classList.add("invalid");
        return;
      }
      const out = requireReason ? ta.value.trim() : true;
      wrap.remove();
      resolve(out);
    };
    if (ta) setTimeout(() => ta.focus(), 30);
    root.appendChild(wrap);
  });
}

export function drawer({ title, sub = "", body = "", footer = "" }) {
  const root = document.getElementById("modal-root");
  const wrap = el(`
    <div class="drawer-wrap">
      <aside class="drawer" role="dialog" aria-modal="true">
        <div class="drawer-head">
          <div class="grow"><h3>${title}</h3>${sub ? `<div class="small muted">${sub}</div>` : ""}</div>
          <button class="btn sm" data-close>Đóng</button>
        </div>
        <div class="drawer-body">${body}</div>
        ${footer ? `<div class="drawer-foot">${footer}</div>` : ""}
      </aside>
    </div>`);
  const close = () => { wrap.remove(); document.removeEventListener("keydown", onKey); };
  function onKey(e) { if (e.key === "Escape") close(); }
  document.addEventListener("keydown", onKey);
  wrap.addEventListener("click", (e) => { if (e.target === wrap) close(); });
  wrap.querySelector("[data-close]").onclick = close;
  root.appendChild(wrap);
  return { root: wrap, close };
}

export function skeleton(rows = 4) {
  return `<div class="stack">${Array.from({ length: rows }, (_, i) => `<div class="skel" style="height:${i === 0 ? 22 : 16}px;width:${i % 2 ? 60 : 90}%"></div>`).join("")}</div>`;
}

/** Đăng ký ticker dùng chung, tự huỷ khi rời màn. */
export function ticker(fn, ms = 1000) {
  const id = setInterval(fn, ms);
  return () => clearInterval(id);
}

export function todayKey(d = new Date()) {
  const p = (n) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

export function readJSON(key, fallback) {
  try { return JSON.parse(localStorage.getItem(key)) ?? fallback; } catch { return fallback; }
}

export function writeJSON(key, v) {
  try { localStorage.setItem(key, JSON.stringify(v)); } catch { /* ignore */ }
}
