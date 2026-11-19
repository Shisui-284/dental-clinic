/** K5 — OTP + đồng ý dữ liệu, tạo lịch hẹn */
import { api } from "../api/index.js";
import { getDraft, setDraft, setCustomerAuth, session, setCustomerPhone } from "../core/session.js";
import { navigate } from "../core/router.js";
import { esc, toast, ticker } from "../core/ui.js";
import { fmtDate, fmtTime, fmtRange, money } from "../core/format.js";
import { config } from "../config.js";

export default {
  id: "k5",
  title: "Xác thực OTP",
  step: 5,
  async render() {
    const d = getDraft();
    const phone = d.phone || session.customer?.phone || "";
    if (!d.start || !d.serviceIds?.length) {
      return `<div class="empty"><b>Thiếu thông tin đặt lịch</b>Vui lòng chọn lại khung giờ. <div style="margin-top:12px"><a class="btn primary" href="#/k4">Chọn ngày giờ</a></div></div>`;
    }
    const services = await api.services();
    const doctors = await api.doctors();
    const chosen = services.filter((s) => d.serviceIds.includes(s.id));
    const doc = doctors.find((x) => x.id === d.doctorId);
    const dur = chosen.reduce((t, s) => t + s.duration, 0) + 10;
    const price = chosen.reduce((t, s) => t + s.price, 0);

    return `
      <div class="cust-grid">
        <section class="card pad-lg">
          <h2>Xác thực số điện thoại</h2>
          <p class="muted small">Mã OTP 6 chữ số đã gửi tới <b>${esc(phone)}</b> qua Zalo ZNS/SMS. Mã hết hạn sau 5 phút.</p>

          <form data-form style="max-width:520px">
            <div class="field">
              <span class="lbl">Mã OTP <span class="req">*</span></span>
              <div class="otp-row">
                ${Array.from({ length: 6 }, (_, i) => `<input type="text" inputmode="numeric" maxlength="1" data-otp="${i}" aria-label="OTP ${i + 1}">`).join("")}
              </div>
              <div class="help" data-otp-help>Đang gửi mã...</div>
            </div>

            <label class="check" style="margin:14px 0">
              <input type="checkbox" name="consent" data-consent>
              <span>Tôi đồng ý để phòng khám xử lý dữ liệu cá nhân cho việc đặt lịch và chăm sóc sau khám.
                <a href="#" data-policy>Chính sách dữ liệu</a></span>
            </label>
            <div data-alert></div>

            <button class="btn primary block" type="submit" data-submit>Xác nhận đặt lịch</button>
            <div class="row" style="margin-top:10px">
              <a class="btn sm" href="#/k4">← Chọn giờ khác</a>
              <div class="spacer"></div>
              <span class="small muted">Chưa trừ tiền, chưa phải lịch cuối cùng.</span>
            </div>
          </form>
        </section>

        <aside class="summary">
          <div class="card">
            <div class="card-title">Lịch bạn đang đặt</div>
            <div class="line"><span>Ngày</span><b>${fmtDate(d.start)}</b></div>
            <div class="line"><span>Giờ</span><b>${fmtRange(d.start, dur)}</b></div>
            <div class="line"><span>Bác sĩ</span><b>${esc(doc ? doc.name : "Hệ thống phân bổ")}</b></div>
            <div class="line"><span>Người khám</span><b>${esc(d.patient?.fullName || "—")}</b></div>
            <hr class="sep">
            ${chosen.map((s) => `<div class="line"><span class="small">${esc(s.name)}</span><span class="small">${money(s.price)}</span></div>`).join("")}
            <div class="line"><span>Thời lượng</span><b>${dur} phút</b></div>
            <div class="line total"><span>Tạm tính</span><span>${money(price)}</span></div>
            <div class="help">Giá tham khảo, thanh toán tại phòng khám.</div>
          </div>
        </aside>
      </div>`;
  },
  mount(root, ctx) {
    const d = getDraft();
    const phone = d.phone || session.customer?.phone || "";
    const form = root.querySelector("[data-form]");
    const otpInputs = Array.from(root.querySelectorAll("[data-otp]"));
    const help = root.querySelector("[data-otp-help]");
    const alertBox = root.querySelector("[data-alert]");
    const submit = root.querySelector("[data-submit]");

    let resendLeft = 0;
    let expireLeft = 300;
    let stop = null;
    let demoCode = null;

    async function sendOtp(isResend = false) {
      try {
        const r = await api.requestOtp(phone);
        resendLeft = r.resendAfter ?? 60;
        expireLeft = r.expiresIn ?? 300;
        demoCode = r.prototypeOtp || null;
        help.textContent = `Đã gửi mã tới ${phone}. ${isResend ? "Mã cũ đã bị thay thế." : ""} Mã hết hạn sau ${Math.ceil(expireLeft / 60)} phút.`
          + (demoCode ? ` Mã demo (prototype): ${demoCode}.` : "");
      } catch (ex) {
        help.textContent = "";
        demoCode = null;
        alertBox.innerHTML = `<div class="banner danger">${esc(ex.message)}</div>`;
      }
      const demoBtn = root.querySelector("[data-demo-otp]");
      if (demoBtn) demoBtn.style.display = demoCode ? "" : "none";
      renderTimers();
    }

    function renderTimers() {
      const resendBtn = root.querySelector("[data-resend]");
      if (resendBtn) {
        resendBtn.disabled = resendLeft > 0;
        resendBtn.textContent = resendLeft > 0 ? `Gửi lại sau ${resendLeft}s` : "Gửi lại mã";
      }
      const expireEl = root.querySelector("[data-expire]");
      if (expireEl) expireEl.textContent = expireLeft > 0 ? `Mã còn hiệu lực ${Math.floor(expireLeft / 60)}:${String(expireLeft % 60).padStart(2, "0")}` : "Mã đã hết hạn";
    }

    const resend = document.createElement("button");
    resend.type = "button";
    resend.className = "btn sm";
    resend.dataset.resend = "";
    resend.textContent = "Gửi lại mã";
    resend.onclick = () => sendOtp(true);
    help.after(resend);
    const demoBtn = document.createElement("button");
    demoBtn.type = "button";
    demoBtn.className = "btn sm ghost";
    demoBtn.dataset.demoOtp = "";
    demoBtn.textContent = "Điền mã demo";
    demoBtn.style.display = "none";
    demoBtn.style.marginLeft = "6px";
    demoBtn.onclick = () => {
      if (!demoCode) return;
      demoCode.split("").forEach((ch, i) => { if (otpInputs[i]) otpInputs[i].value = ch; });
      otpInputs[Math.min(demoCode.length, otpInputs.length) - 1]?.focus();
    };
    resend.after(demoBtn);
    const expire = document.createElement("div");
    expire.className = "help";
    expire.dataset.expire = "";
    demoBtn.after(expire);

    stop = ticker(() => {
      if (resendLeft > 0) resendLeft -= 1;
      if (expireLeft > 0) expireLeft -= 1;
      renderTimers();
    }, 1000);

    otpInputs.forEach((inp, i) => {
      inp.addEventListener("input", () => {
        inp.value = inp.value.replace(/\D/g, "").slice(0, 1);
        if (inp.value && otpInputs[i + 1]) otpInputs[i + 1].focus();
      });
      inp.addEventListener("keydown", (e) => {
        if (e.key === "Backspace" && !inp.value && otpInputs[i - 1]) otpInputs[i - 1].focus();
      });
      inp.addEventListener("paste", (e) => {
        e.preventDefault();
        const text = (e.clipboardData.getData("text") || "").replace(/\D/g, "").slice(0, 6);
        text.split("").forEach((ch, k) => { if (otpInputs[k]) otpInputs[k].value = ch; });
        if (text.length === 6) otpInputs[5].focus();
      });
    });

    root.querySelector("[data-policy]").onclick = (e) => {
      e.preventDefault();
      toast("Chính sách dữ liệu: thông tin chỉ dùng cho đặt lịch và chăm sóc sau khám.");
    };

    sendOtp(false);

    form.addEventListener("submit", async (e) => {
      e.preventDefault();
      alertBox.innerHTML = "";
      const code = otpInputs.map((x) => x.value).join("");
      const consent = root.querySelector("[data-consent]").checked;
      if (code.length !== 6) {
        alertBox.innerHTML = `<div class="banner danger">Vui lòng nhập đủ 6 chữ số OTP.</div>`;
        return;
      }
      if (!consent) {
        alertBox.innerHTML = `<div class="banner warn">Bạn cần đồng ý xử lý dữ liệu cá nhân để đặt lịch. Nếu cần hỗ trợ, gọi ${esc(config.hotline)}.</div>`;
        return;
      }
      submit.disabled = true;
      submit.textContent = "Đang xác thực...";
      try {
        const auth = await api.verifyOtp({
          phone,
          code,
          consent: true,
          patient: d.patient,
          at: new Date().toISOString(),
        });
        setCustomerAuth(auth);
        setCustomerPhone(phone);

        submit.textContent = "Đang tạo lịch...";
        const appt = await api.createAppointment({
          phone,
          patient: d.patient,
          serviceIds: d.serviceIds,
          doctorId: d.doctorId || undefined,
          start: d.start,
          consent: true,
        });
        try { sessionStorage.setItem("qldapm_last_appt_72", JSON.stringify(appt)); } catch { /* ignore */ }
        setDraft({ lastAppointmentId: appt.id });
        navigate("k6", { id: appt.id });
      } catch (ex) {
        if (ex.code === "SLOT_TAKEN") {
          alertBox.innerHTML = `<div class="banner danger"><b>Khung giờ vừa được người khác giữ.</b> Vui lòng chọn giờ khác.
            <div style="margin-top:8px"><a class="btn sm primary" href="#/k4">Chọn giờ khác</a></div></div>`;
        } else {
          alertBox.innerHTML = `<div class="banner danger">${esc(ex.message)}</div>`;
        }
        submit.disabled = false;
        submit.textContent = "Xác nhận đặt lịch";
      }
    });

    return () => { if (stop) stop(); };
  },
};
