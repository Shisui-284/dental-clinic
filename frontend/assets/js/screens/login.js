/** N1 (rút gọn) — Đăng nhập nhân sự. Cần cho L1–L3. */
import { api } from "../api/index.js";
import { setStaffLogin } from "../core/session.js";
import { navigate } from "../core/router.js";
import { esc, toast } from "../core/ui.js";
import { config, isMock } from "../config.js";

export default {
  id: "login",
  title: "Đăng nhập nhân sự",
  standalone: true,
  async render(ctx) {
    const next = ctx.params?.get("next") || ctx.next || "l1";
    const demoPw = isMock() ? "demo1234" : "123456";
    return `
      <div style="min-height:100vh;display:grid;grid-template-columns:1fr 520px;background:#fff">
        <div style="background:linear-gradient(135deg,#0F766E,#115E59);color:#fff;padding:64px;display:flex;flex-direction:column;justify-content:space-between">
          <div>
            <div class="row" style="gap:12px">
              <div class="mark" style="width:44px;height:44px;border-radius:12px;background:#fff;color:#0F766E;display:grid;place-items:center;font-weight:800">AT</div>
              <div style="font-size:18px;font-weight:700">${esc(config.clinicName)}</div>
            </div>
            <h1 style="color:#fff;font-size:34px;margin-top:56px;max-width:520px">Cổng nhân sự — Phân hệ đặt lịch</h1>
            <p style="color:#CCFBF1;max-width:520px">Duyệt lịch chờ xác nhận, tạo lịch hộ/vãng lai/cấp cứu và điều phối lịch trong ngày.</p>
          </div>
          <div style="font-size:12.5px;color:#99F6E4">7.2 Frontend · Desktop 1440×900 · Không xây dựng màn hình mobile</div>
        </div>
        <div style="padding:64px 56px;display:flex;flex-direction:column;justify-content:center">
          <h2 style="font-size:22px">Đăng nhập nhân sự</h2>
          <p class="muted small">Dùng tài khoản được quản lý phòng khám cấp.</p>
          <form data-form style="margin-top:18px">
            <label class="field"><span class="lbl">Tên đăng nhập <span class="req">*</span></span>
              <input type="text" name="username" autocomplete="username" placeholder="letan01" autofocus>
            </label>
            <label class="field"><span class="lbl">Mật khẩu <span class="req">*</span></span>
              <input type="password" name="password" autocomplete="current-password" placeholder="${demoPw}">
            </label>
            <div class="banner danger hidden" data-err></div>
            <button class="btn primary block" type="submit">Đăng nhập</button>
          </form>
          <div class="row" style="margin-top:16px">
            <span class="small muted">Quên mật khẩu? Liên hệ quản lý.</span>
            <a class="small right" href="#/k1">← Về cổng khách hàng</a>
          </div>
          <div class="banner info small" style="margin-top:22px">
            Tài khoản demo: <b>letan01</b> / <b>${demoPw}</b> · <b>quanly01</b> / <b>${demoPw}</b>
            ${isMock() ? "" : " · <i>Đang ở Live API (backend 7.1)</i>"}
          </div>
        </div>
      </div>`;
  },
  mount(root, ctx) {
    const next = ctx.params?.get("next") || ctx.next || "l1";
    const form = root.querySelector("[data-form]");
    const err = root.querySelector("[data-err]");
    form.addEventListener("submit", async (e) => {
      e.preventDefault();
      err.classList.add("hidden");
      const fd = new FormData(form);
      const username = String(fd.get("username") || "").trim();
      const password = String(fd.get("password") || "");
      if (!username || !password) {
        err.textContent = "Vui lòng nhập đủ tên đăng nhập và mật khẩu.";
        err.classList.remove("hidden");
        return;
      }
      const btn = form.querySelector("button[type=submit]");
      btn.disabled = true;
      btn.textContent = "Đang đăng nhập...";
      try {
        const data = await api.staffLogin(username, password);
        setStaffLogin(data);
        toast("Đăng nhập thành công.", "ok");
        if (location.hash === `#/${next}` && typeof ctx.refresh === "function") ctx.refresh();
        else navigate(next);
      } catch (ex) {
        err.textContent = ex.message || "Đăng nhập thất bại.";
        err.classList.remove("hidden");
        btn.disabled = false;
        btn.textContent = "Đăng nhập";
      }
    });
    return null;
  },
};
