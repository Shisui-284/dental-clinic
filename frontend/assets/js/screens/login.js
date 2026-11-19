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
              <div class="mark" style="width:44px;height:44px;border-radius:12px;background:#fff;color:#0F766E;display:grid;place-items:center;font-weight:800">CH</div>
              <div style="font-size:18px;font-weight:700">${esc(config.clinicName)}</div>
            </div>
            <h1 style="color:#fff;font-size:34px;margin-top:56px;max-width:520px">Cổng nhân sự — Phân hệ đặt lịch</h1>
            <p style="color:#CCFBF1;max-width:520px">Đăng nhập cho Lễ tân (L1–L3), Bác sĩ (B1 — lịch khám cá nhân & xin nghỉ) và Quản lý (Q1–Q2 — duyệt nghỉ & gỡ khóa Blacklist).</p>
          </div>
          <div style="font-size:12.5px;color:#99F6E4">Phân hệ Đặt lịch (7.1 + 7.2) · Desktop 1440×900</div>
        </div>
        <div style="padding:64px 56px;display:flex;flex-direction:column;justify-content:center">
          <h2 style="font-size:22px">Đăng nhập nhân sự</h2>
          <p class="muted small">Dùng tài khoản Lễ tân, Bác sĩ hoặc Quản lý phòng khám.</p>
          <form data-form style="margin-top:18px">
            <label class="field"><span class="lbl">Tên đăng nhập <span class="req">*</span></span>
              <input type="text" name="username" autocomplete="username" placeholder="letan01 / bacsi01 / quanly01" autofocus>
            </label>
            <label class="field"><span class="lbl">Mật khẩu <span class="req">*</span></span>
              <input type="password" name="password" autocomplete="current-password" placeholder="${demoPw}">
            </label>
            <div class="banner danger hidden" data-err></div>
            <button class="btn primary block" type="submit">Đăng nhập</button>
          </form>
          <div class="row" style="margin-top:14px;gap:6px;flex-wrap:wrap">
            <span class="small muted">Điền nhanh:</span>
            <button type="button" class="chip" data-quick="letan01">Lễ tân (letan01)</button>
            <button type="button" class="chip" data-quick="bacsi01">Bác sĩ (bacsi01)</button>
            <button type="button" class="chip" data-quick="quanly01">Quản lý (quanly01)</button>
          </div>
          <div class="row" style="margin-top:16px">
            <span class="small muted">Quên mật khẩu? Liên hệ quản lý.</span>
            <a class="small right" href="#/k1">← Về cổng khách hàng</a>
          </div>
          <div class="banner info small" style="margin-top:18px">
            Tài khoản mẫu (mật khẩu <b>${demoPw}</b>): <b>letan01</b> (Lễ tân) · <b>bacsi01</b> (Bác sĩ) · <b>quanly01</b> (Quản lý)
            ${isMock() ? "" : " · <i>Đang ở Live API (backend 7.1)</i>"}
          </div>
        </div>
      </div>`;
  },
  mount(root, ctx) {
    const requestedNext = ctx.params?.get("next") || ctx.next || "";
    const form = root.querySelector("[data-form]");
    const err = root.querySelector("[data-err]");
    const demoPw = isMock() ? "demo1234" : "123456";

    root.querySelectorAll("[data-quick]").forEach((btn) => {
      btn.addEventListener("click", () => {
        form.querySelector('input[name="username"]').value = btn.dataset.quick;
        form.querySelector('input[name="password"]').value = demoPw;
      });
    });

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
        const role = data.staff?.role;
        let target = requestedNext;
        if (role === "DOCTOR") {
          target = "b1";
        } else if (role === "RECEPTIONIST") {
          target = ["l1", "l2", "l3"].includes(requestedNext) ? requestedNext : "l1";
        } else if (role === "MANAGER") {
          target = ["l1", "l2", "l3", "b1", "q1", "q2"].includes(requestedNext) ? requestedNext : "l1";
        } else {
          target = "l1";
        }
        toast(`Đăng nhập thành công (${data.staff?.name || username}).`, "ok");
        if (location.hash === `#/${target}` && typeof ctx.refresh === "function") ctx.refresh();
        else navigate(target);
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
