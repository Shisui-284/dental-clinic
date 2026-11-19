/** Bootstrap ứng dụng 7.2: router + guard RBAC + mount màn hình. */
import { parseHash, navigate, onChange } from "./core/router.js";
import { customerFrame, staffFrame } from "./core/shell.js";
import { session, hasStaffRole, setStaffLogout } from "./core/session.js";
import { toast } from "./core/ui.js";
import { config, updateConfig, isMock } from "./config.js";
import { resetMockData } from "./core/mock.js";
import { api } from "./api/index.js";

import login from "./screens/login.js";
import k1 from "./screens/k1.js";
import k2 from "./screens/k2.js";
import k3 from "./screens/k3.js";
import k4 from "./screens/k4.js";
import k5 from "./screens/k5.js";
import k6 from "./screens/k6.js";
import k7 from "./screens/k7.js";
import l1 from "./screens/l1.js";
import l2 from "./screens/l2.js";
import l3 from "./screens/l3.js";
import b1 from "./screens/b1.js";
import q1 from "./screens/q1.js";
import q2 from "./screens/q2.js";

const SCREENS = { login, k1, k2, k3, k4, k5, k6, k7, l1, l2, l3, b1, q1, q2 };
const ROLE_GUARD = {
  l1: ["RECEPTIONIST", "MANAGER"],
  l2: ["RECEPTIONIST", "MANAGER"],
  l3: ["RECEPTIONIST", "MANAGER"],
  b1: ["DOCTOR", "MANAGER"],
  q1: ["MANAGER"],
  q2: ["MANAGER"],
};

const app = document.getElementById("app");
let cleanup = null;

function apiBadge() {
  const b = document.createElement("button");
  b.className = "btn sm";
  b.style.cssText = "position:fixed;right:16px;bottom:16px;z-index:50;opacity:.9";
  b.textContent = isMock() ? "Mock API" : "Live API";
  b.title = "Cấu hình nguồn dữ liệu";
  b.onclick = openSettings;
  return b;
}

function openSettings() {
  const wrap = document.createElement("div");
  wrap.className = "modal-wrap";
  wrap.innerHTML = `
    <div class="modal" role="dialog" aria-modal="true" style="width:520px">
      <h3>Cấu hình nguồn dữ liệu</h3>
      <p class="small muted">Mặc định dùng Mock API (dữ liệu mẫu cục bộ). Chuyển sang Live để gọi backend 7.1 theo API Contract 5.3.</p>
      <label class="field"><span class="lbl">Chế độ</span>
        <select data-mode>
          <option value="mock" ${isMock() ? "selected" : ""}>Mock API (demo, không cần backend)</option>
          <option value="live" ${!isMock() ? "selected" : ""}>Live API (gọi backend thật)</option>
        </select>
      </label>
      <label class="field"><span class="lbl">API Base URL</span>
        <input type="text" data-base value="${config.apiBase}" placeholder="http://localhost:8080">
        <div class="help">Ví dụ: http://localhost:8080 (Spring Boot). Cần bật CORS cho origin này.</div>
      </label>
      <div class="row"><button class="btn" data-reset>Xoá dữ liệu mock</button><div class="spacer"></div>
        <button class="btn" data-close>Đóng</button><button class="btn primary" data-save>Lưu</button></div>
    </div>`;
  wrap.querySelector("[data-close]").onclick = () => wrap.remove();
  wrap.querySelector("[data-save]").onclick = () => {
    updateConfig({
      mode: wrap.querySelector("[data-mode]").value,
      apiBase: wrap.querySelector("[data-base]").value.trim() || "http://localhost:8080",
    });
    wrap.remove();
    toast("Đã lưu cấu hình. Đang tải lại...", "ok");
    setTimeout(() => render(), 200);
  };
  wrap.querySelector("[data-reset]").onclick = () => {
    resetMockData();
    toast("Đã xoá dữ liệu mock và tạo lại dữ liệu mẫu.", "ok");
    wrap.remove();
    setTimeout(() => render(), 200);
  };
  document.getElementById("modal-root").appendChild(wrap);
}

async function render() {
  const { id, params } = parseHash();
  const screen = SCREENS[id] || SCREENS.k1;
  const screenId = SCREENS[id] ? id : "k1";

  if (cleanup) { try { cleanup(); } catch { /* ignore */ } cleanup = null; }

  document.body.dataset.screen = screenId;

  const allowedRoles = ROLE_GUARD[screenId];
  if (allowedRoles && !hasStaffRole(...allowedRoles)) {
    if (session.staff) {
      toast("Tài khoản của bạn không có quyền truy cập màn hình này.", "err");
      const fallback = session.staff.role === "DOCTOR" ? "b1" : "l1";
      navigate(fallback);
      return;
    }
    const loginScreen = SCREENS.login;
    const lctx = { params, next: screenId, refresh: render };
    app.innerHTML = await loginScreen.render(lctx);
    cleanup = loginScreen.mount?.(app, lctx) || null;
    return;
  }

  const ctx = {
    params,
    navigate,
    refresh: render,
    setTitle(t) {
      const n = document.getElementById("topbar-title");
      if (n) n.textContent = t;
    },
  };

  const html = await screen.render(ctx);
  if (screen.standalone) {
    app.innerHTML = html;
  } else if (screen.staff) {
    app.innerHTML = staffFrame({ content: html, active: screenId });
    const t = document.getElementById("topbar-title");
    if (t) t.textContent = screen.title || "";
    const lo = app.querySelector("[data-logout]");
    if (lo) lo.onclick = async () => {
      try { await api.staffLogout(); } catch { /* ignore */ }
      setStaffLogout();
      toast("Đã đăng xuất.");
      navigate("login");
    };
  } else {
    app.innerHTML = customerFrame({
      content: html,
      activeStep: screen.step || 0,
      phone: session.customer?.phone || "",
    });
  }
  app.appendChild(apiBadge());

  if (screen.mount) cleanup = screen.mount(app, ctx) || null;
}

onChange(render);
render();
