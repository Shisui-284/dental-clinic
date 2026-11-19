/** K1 — Nhập số điện thoại (5.2 §6.1) */
import { api } from "../api/index.js";
import { getDraft, setCustomerPhone, setDraft } from "../core/session.js";
import { navigate } from "../core/router.js";
import { esc } from "../core/ui.js";
import { config } from "../config.js";

export default {
  id: "k1",
  title: "Nhập số điện thoại",
  step: 1,
  async render() {
    const draft = getDraft();
    return `
      <div class="cust-grid">
        <section class="card pad-lg">
          <div class="eyebrow small" style="color:var(--teal);font-weight:700;letter-spacing:.1em">ĐẶT LỊCH KHÁM</div>
          <h1 style="margin:8px 0 6px">Đặt lịch khám trong 2 phút</h1>
          <p class="muted">Chọn dịch vụ, bác sĩ và khung giờ phù hợp. Lễ tân sẽ xác nhận lịch của bạn.</p>

          <form data-form style="margin-top:18px;max-width:460px">
            <label class="field">
              <span class="lbl">Số điện thoại <span class="req">*</span></span>
              <input type="tel" name="phone" inputmode="numeric" maxlength="10" placeholder="0912 345 678"
                     value="${esc(draft.phone || "")}" autocomplete="tel">
              <div class="help">Dùng số bạn đã đăng ký khám (nếu có). Bạn sẽ nhận OTP ở bước cuối.</div>
            </label>
            <div data-alert></div>
            <button class="btn primary block" type="submit" data-submit>Tiếp tục</button>
          </form>

          <div class="banner" style="margin-top:22px">
            <div>
              <b>Cần hỗ trợ đặt lịch?</b> Gọi <b>${esc(config.hotline)}</b> (8:00–18:00).
              <div class="small muted" style="margin-top:4px">Nếu số điện thoại của bạn cần đặt qua lễ tân, hệ thống sẽ hướng dẫn ở bước tiếp theo.</div>
            </div>
          </div>
        </section>

        <aside class="stack">
          <div class="card">
            <div class="card-title">Vì sao cần số điện thoại?</div>
            <ul class="small muted" style="margin:0;padding-left:18px;line-height:1.8">
              <li>Đối chiếu hồ sơ khám cũ của bạn</li>
              <li>Gửi OTP xác thực ở bước cuối</li>
              <li>Gửi xác nhận và nhắc lịch qua Zalo/SMS</li>
            </ul>
          </div>
          <div class="card">
            <div class="card-title">Đã có lịch hẹn?</div>
            <p class="small muted">Tra cứu, đổi hoặc hủy lịch đã đặt (trước giờ hẹn 4 giờ).</p>
            <a class="btn block" href="#/k7">Xem lịch hẹn của tôi</a>
          </div>
        </aside>
      </div>`;
  },
  mount(root) {
    const form = root.querySelector("[data-form]");
    const input = form.querySelector("input[name=phone]");
    const alertBox = root.querySelector("[data-alert]");
    const btn = root.querySelector("[data-submit]");

    input.addEventListener("input", () => {
      input.value = input.value.replace(/\D/g, "").slice(0, 10);
      input.classList.remove("invalid");
    });

    form.addEventListener("submit", async (e) => {
      e.preventDefault();
      alertBox.innerHTML = "";
      const phone = input.value.trim();
      if (!/^0\d{9}$/.test(phone)) {
        input.classList.add("invalid");
        alertBox.innerHTML = `<div class="err">SĐT phải gồm 10 chữ số và bắt đầu bằng 0.</div>`;
        return;
      }
      btn.disabled = true;
      btn.textContent = "Đang kiểm tra...";
      try {
        const res = await api.checkPhone(phone);
        if (res.isBlacklisted) {
          alertBox.innerHTML = `<div class="banner warn"><div><b>Số điện thoại này cần đặt lịch qua lễ tân.</b>
            Vui lòng gọi <b>${esc(config.hotline)}</b> để được hỗ trợ.</div></div>`;
          btn.disabled = false;
          btn.textContent = "Tiếp tục";
          return;
        }
        setCustomerPhone(phone);
        setDraft({ phone, suggestedDoctorId: res.suggestedDoctorId || null });
        navigate("k2");
      } catch (ex) {
        alertBox.innerHTML = `<div class="banner danger">${esc(ex.message || "Không kết nối được, thử lại.")}</div>`;
        btn.disabled = false;
        btn.textContent = "Tiếp tục";
      }
    });

    setTimeout(() => input.focus(), 50);
    return null;
  },
};
