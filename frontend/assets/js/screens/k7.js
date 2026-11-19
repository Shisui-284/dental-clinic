/** K7 — Lịch hẹn của tôi, đổi/hủy (chặn < 4 giờ) */
import { api } from "../api/index.js";
import { session, setCustomerPhone, setCustomerAuth } from "../core/session.js";
import { navigate } from "../core/router.js";
import { esc, toast, confirmDanger, drawer } from "../core/ui.js";
import { fmtDate, fmtRange, statusTag, statusMeta, sourceLabel, money, MIN_BOOKING_MS } from "../core/format.js";
import { config, isMock } from "../config.js";

export default {
  id: "k7",
  title: "Lịch hẹn của tôi",
  step: 0,
  async render(ctx) {
    const tab = ctx.params.get("tab") === "past" ? "past" : "upcoming";
    const phone = session.customer?.phone || ctx.params.get("phone") || "";

    // Live API: backend lọc lịch hẹn theo customerId trong JWT → bắt buộc OTP.
    if (!isMock() && !session.customer?.token) {
      return `
        <div class="cust-grid single">
          <section class="card pad-lg">
            <h2>Tra cứu lịch hẹn</h2>
            <p class="muted small">Để bảo vệ hồ sơ, backend yêu cầu xác thực OTP cho số điện thoại đã đặt lịch.</p>
            <form data-otp-login style="max-width:520px;margin-top:12px">
              <label class="field"><span class="lbl">Số điện thoại <span class="req">*</span></span>
                <input type="tel" name="phone" maxlength="10" placeholder="0912345678" value="${esc(phone)}">
              </label>
              <div data-otp-step class="hidden">
                <label class="field"><span class="lbl">Mã OTP (6 chữ số) <span class="req">*</span></span>
                  <input type="text" name="otp" inputmode="numeric" maxlength="6" placeholder="123456">
                </label>
                <div class="help" data-otp-help></div>
              </div>
              <div data-alert></div>
              <div class="row" style="margin-top:10px">
                <button class="btn primary" type="submit" data-send>Gửi mã OTP</button>
                <button class="btn primary hidden" type="submit" data-verify>Xác thực</button>
              </div>
            </form>
            <div style="margin-top:12px"><a class="small" href="#/k1">← Đặt lịch mới</a></div>
          </section>
        </div>`;
    }

    if (!phone) {
      return `
        <div class="cust-grid">
          <section class="card pad-lg">
            <h2>Tra cứu lịch hẹn</h2>
            <p class="muted small">Nhập số điện thoại đã dùng để đặt lịch. ${isMock() ? "Chế độ demo cho phép tra cứu trực tiếp." : "Bạn cần xác thực OTP ở luồng đặt lịch."}</p>
            <form data-phone class="row" style="max-width:460px;margin-top:12px">
              <input type="tel" name="phone" maxlength="10" placeholder="0912345678" value="">
              <button class="btn primary" type="submit">Tra cứu</button>
            </form>
            <div style="margin-top:10px"><a class="small" href="#/k1">← Đặt lịch mới</a></div>
          </section>
        </div>`;
    }

    let data = { items: [] };
    let error = null;
    try {
      data = await api.myAppointments({ phone, scope: tab });
    } catch (ex) {
      error = ex.message;
    }
    const items = data.items || [];

    return `
      <div class="cust-grid single">
        <section class="card">
          <div class="row" style="justify-content:space-between">
            <div>
              <h2>Lịch hẹn của tôi</h2>
              <div class="small muted">Số điện thoại ${esc(phone)}</div>
            </div>
            <a class="btn primary sm" href="#/k1">+ Đặt lịch mới</a>
          </div>
          <div class="tabs" style="margin-top:14px">
            <button data-tab="upcoming" class="${tab === "upcoming" ? "on" : ""}">Sắp tới</button>
            <button data-tab="past" class="${tab === "past" ? "on" : ""}">Đã qua</button>
          </div>
          ${error ? `<div class="banner danger">${esc(error)}</div>` : ""}
          ${items.length ? `
            <table class="table">
              <thead><tr><th>Ngày giờ</th><th>Bác sĩ</th><th>Dịch vụ</th><th>Trạng thái</th><th>Thao tác</th></tr></thead>
              <tbody>
                ${items.map((a) => {
                  const editable = ["PENDING", "CONFIRMED"].includes(a.status) && new Date(a.start).getTime() - Date.now() >= MIN_BOOKING_MS;
                  const tooLate = ["PENDING", "CONFIRMED"].includes(a.status) && !editable;
                  return `<tr data-appt="${esc(a.id)}">
                    <td><b>${fmtDate(a.start)}</b><div class="small muted">${fmtRange(a.start, a.duration)}</div></td>
                    <td>${esc(a.doctorName || "Hệ thống phân bổ")}</td>
                    <td>${a.services.map((s) => esc(s.name)).join("<br>")}</td>
                    <td>${statusTag(a.status)}${tooLate ? '<div class="small muted" style="margin-top:4px">Trong 4 giờ tới — gọi hotline</div>' : ""}</td>
                    <td class="nowrap" data-stop>
                      <button class="btn sm" data-resched="${esc(a.id)}" ${editable ? "" : "disabled"}>Đổi lịch</button>
                      <button class="btn sm danger" data-cancel="${esc(a.id)}" ${editable ? "" : "disabled"}>Hủy</button>
                    </td>
                  </tr>`;
                }).join("")}
              </tbody>
            </table>` : `<div class="empty"><b>${tab === "upcoming" ? "Chưa có lịch hẹn nào." : "Chưa có lịch hẹn đã qua."}</b>
                ${tab === "upcoming" ? 'Đặt lịch ngay để bắt đầu. <div style="margin-top:12px"><a class="btn primary" href="#/k1">Đặt lịch</a></div>' : ""}</div>`}
          <div class="help" style="margin-top:10px">Chỉ đổi/hủy trước giờ hẹn 4 giờ. Trong vòng 4 giờ, vui lòng gọi <b>${esc(config.hotline)}</b>.</div>
        </section>
      </div>`;
  },
  mount(root, ctx) {
    const phone = session.customer?.phone || ctx.params.get("phone") || "";
    const tab = ctx.params.get("tab") === "past" ? "past" : "upcoming";

    const otpForm = root.querySelector("[data-otp-login]");
    if (otpForm) {
      const alertBox = otpForm.querySelector("[data-alert]");
      const step = otpForm.querySelector("[data-otp-step]");
      const help = otpForm.querySelector("[data-otp-help]");
      const sendBtn = otpForm.querySelector("[data-send]");
      const verifyBtn = otpForm.querySelector("[data-verify]");
      let currentPhone = "";
      let demoCode = null;

      otpForm.addEventListener("submit", async (e) => {
        e.preventDefault();
        alertBox.innerHTML = "";
        const fd = new FormData(otpForm);
        const p = String(fd.get("phone") || "").replace(/\D/g, "");
        const code = String(fd.get("otp") || "").replace(/\D/g, "");

        if (!code) {
          if (!/^0\d{9}$/.test(p)) {
            alertBox.innerHTML = `<div class="banner danger">SĐT phải gồm 10 chữ số, bắt đầu bằng 0.</div>`;
            return;
          }
          sendBtn.disabled = true;
          sendBtn.textContent = "Đang gửi...";
          try {
            const r = await api.requestOtp(p);
            currentPhone = p;
            demoCode = r.prototypeOtp || null;
            step.classList.remove("hidden");
            sendBtn.classList.add("hidden");
            verifyBtn.classList.remove("hidden");
            help.textContent = `Đã gửi mã tới ${p}. Mã hết hạn sau ${Math.ceil((r.expiresIn || 300) / 60)} phút.`
              + (demoCode ? ` Mã demo (prototype): ${demoCode}.` : "");
          } catch (ex) {
            alertBox.innerHTML = `<div class="banner danger">${esc(ex.message)}</div>`;
            sendBtn.disabled = false;
            sendBtn.textContent = "Gửi mã OTP";
          }
          return;
        }

        if (!/^\d{6}$/.test(code)) {
          alertBox.innerHTML = `<div class="banner danger">Nhập đủ 6 chữ số OTP.</div>`;
          return;
        }
        verifyBtn.disabled = true;
        verifyBtn.textContent = "Đang xác thực...";
        try {
          const auth = await api.verifyOtp({ phone: currentPhone || p, code, consent: true });
          setCustomerAuth(auth);
          setCustomerPhone(currentPhone || p);
          navigate("k7", { tab });
        } catch (ex) {
          alertBox.innerHTML = `<div class="banner danger">${esc(ex.message)}</div>`;
          verifyBtn.disabled = false;
          verifyBtn.textContent = "Xác thực";
        }
      });
      return null;
    }

    const phoneForm = root.querySelector("[data-phone]");
    if (phoneForm) {
      phoneForm.addEventListener("submit", (e) => {
        e.preventDefault();
        const v = String(new FormData(phoneForm).get("phone") || "").replace(/\D/g, "");
        if (!/^0\d{9}$/.test(v)) { toast("SĐT phải gồm 10 chữ số.", "err"); return; }
        setCustomerPhone(v);
        navigate("k7", { phone: v, tab });
      });
      return null;
    }

    root.querySelectorAll("[data-tab]").forEach((b) => b.addEventListener("click", () => navigate("k7", { tab: b.dataset.tab })));

    // Mở drawer chi tiết khi bấm vào dòng
    root.querySelectorAll("tr[data-appt]").forEach((tr) => {
      tr.addEventListener("click", async (e) => {
        if (e.target.closest("[data-stop]")) return;
        const id = tr.dataset.appt;
        try {
          const all = await api.myAppointments({ phone, scope: tab });
          const a = (all.items || []).find((x) => x.id === id);
          if (a) showDetail(a, ctx);
        } catch (ex) { toast(ex.message, "err"); }
      });
    });

    root.querySelectorAll("[data-resched]").forEach((b) => b.addEventListener("click", (e) => {
      e.stopPropagation();
      navigate("k4", { id: b.dataset.resched });
    }));

    root.querySelectorAll("[data-cancel]").forEach((b) => b.addEventListener("click", async (e) => {
      e.stopPropagation();
      const ok = await confirmDanger({
        title: "Hủy lịch này?",
        text: "Khung giờ sẽ được giải phóng và không thể hoàn tác. Bạn có thể đặt lịch mới bất cứ lúc nào.",
        confirmLabel: "Hủy lịch",
      });
      if (!ok) return;
      try {
        await api.cancel(b.dataset.cancel, "Khách tự hủy trên web");
        toast("Đã hủy lịch. Khung giờ đã được giải phóng.", "ok");
        ctx.refresh();
      } catch (ex) {
        toast(ex.message, "err");
      }
    }));

    return null;
  },
};

function showDetail(a, ctx) {
  const m = statusMeta(a.status);
  const editable = ["PENDING", "CONFIRMED"].includes(a.status) && new Date(a.start).getTime() - Date.now() >= MIN_BOOKING_MS;
  const d = drawer({
    title: esc(a.code),
    sub: `${statusTag(a.status)} <span class="small muted">${esc(m.note)}</span>`,
    body: `
      <dl class="kv">
        <dt>Ngày</dt><dd>${fmtDate(a.start)}</dd>
        <dt>Giờ</dt><dd>${fmtRange(a.start, a.duration)}</dd>
        <dt>Bác sĩ</dt><dd>${esc(a.doctorName || "Hệ thống phân bổ")}</dd>
        <dt>Người khám</dt><dd>${esc(a.patientName)}</dd>
        <dt>Nguồn đặt</dt><dd>${esc(sourceLabel(a.source))}</dd>
        <dt>Dịch vụ</dt><dd>${a.services.map((s) => esc(s.name)).join("<br>")}</dd>
        <dt>Thời lượng</dt><dd>${a.duration} phút</dd>
        <dt>Tạm tính</dt><dd>${money(a.services.reduce((t, s) => t + s.price, 0))}</dd>
        ${a.holdUntil ? `<dt>Hạn giữ chỗ</dt><dd>${fmtDate(a.holdUntil)} ${fmtRange(a.holdUntil, 0)}</dd>` : ""}
        ${a.cancelReason ? `<dt>Lý do hủy</dt><dd>${esc(a.cancelReason)}</dd>` : ""}
        <dt>Ghi chú</dt><dd>${esc(a.note || "—")}</dd>
      </dl>
      <div class="banner info small" style="margin-top:14px">Mọi thay đổi được ghi vào nhật ký thao tác (audit log).</div>`,
    footer: editable
      ? `<button class="btn" data-d-resched>Đổi lịch</button><button class="btn danger" data-d-cancel>Hủy lịch</button>`
      : `<a class="btn" href="#/k1">Đặt lịch mới</a>`,
  });

  d.root.querySelector("[data-d-resched]")?.addEventListener("click", () => { d.close(); navigate("k4", { id: a.id }); });
  d.root.querySelector("[data-d-cancel]")?.addEventListener("click", async () => {
    const ok = await confirmDanger({
      title: "Hủy lịch này?",
      text: "Khung giờ sẽ được giải phóng và không thể hoàn tác.",
      confirmLabel: "Hủy lịch",
    });
    if (!ok) return;
    try {
      await api.cancel(a.id, "Khách tự hủy trên web");
      d.close();
      toast("Đã hủy lịch.", "ok");
      ctx.refresh();
    } catch (ex) { toast(ex.message, "err"); }
  });
}
