/** K6 — Kết quả "Chờ xác nhận" + Hold_Until */
import { api } from "../api/index.js";
import { getDraft, session, clearDraft } from "../core/session.js";
import { esc, toast, ticker } from "../core/ui.js";
import { fmtDate, fmtRange, statusTag, countdown, money } from "../core/format.js";
import { config } from "../config.js";

export default {
  id: "k6",
  title: "Hoàn tất đặt lịch",
  step: 6,
  async render(ctx) {
    let appt = null;
    try { appt = JSON.parse(sessionStorage.getItem("qldapm_last_appt_72") || "null"); } catch { appt = null; }
    const id = ctx.params.get("id");
    if ((!appt || (id && appt.id !== id)) && id) {
      // Tải lại từ danh sách nếu người dùng F5
      try {
        const r = await api.myAppointments({ phone: session.customer?.phone || getDraft().phone, scope: "upcoming" });
        appt = r.items.find((x) => x.id === id) || null;
      } catch { /* ignore */ }
    }
    if (!appt) {
      return `<div class="empty"><b>Không tìm thấy lịch vừa tạo</b>Xem danh sách lịch hẹn của bạn.
        <div style="margin-top:12px"><a class="btn primary" href="#/k7">Lịch hẹn của tôi</a></div></div>`;
    }

    return `
      <div class="cust-grid">
        <section class="card pad-lg">
          <div class="result-hero">
            <div class="icon">✓</div>
            <h1>Đã gửi yêu cầu đặt lịch</h1>
            <p class="muted">Lễ tân sẽ xác nhận trước hạn giữ chỗ. <b>Chờ xác nhận chưa phải lịch đã chốt.</b></p>
            <div style="margin-top:8px">${statusTag(appt.status)}</div>
          </div>

          <div class="grid-2" style="margin-top:6px">
            <div>
              <dl class="kv">
                <dt>Mã lịch</dt><dd class="mono">${esc(appt.code)}</dd>
                <dt>Ngày</dt><dd>${fmtDate(appt.start)}</dd>
                <dt>Giờ</dt><dd>${fmtRange(appt.start, appt.duration)}</dd>
                <dt>Bác sĩ</dt><dd>${esc(appt.doctorName || "Hệ thống phân bổ")}</dd>
                <dt>Người khám</dt><dd>${esc(appt.patientName)}</dd>
              </dl>
            </div>
            <div>
              <dl class="kv">
                <dt>Dịch vụ</dt><dd>${appt.services.map((s) => esc(s.name)).join("<br>")}</dd>
                <dt>Thời lượng</dt><dd>${appt.duration} phút</dd>
                <dt>Tạm tính</dt><dd>${money(appt.services.reduce((t, s) => t + s.price, 0))}</dd>
              </dl>
            </div>
          </div>

          <div class="banner warn" style="margin-top:16px" data-hold>
            <div><b>Hạn giữ chỗ:</b> <span class="countdown" data-countdown>—</span>
              <div class="small" style="margin-top:4px">Nếu quá hạn mà chưa được xác nhận, khung giờ sẽ được giải phóng.</div></div>
          </div>

          <div class="banner info">
            Vui lòng đến trước 10 phút và mang theo giấy tờ tùy thân. Cần hỗ trợ: <b>${esc(config.hotline)}</b>.
          </div>

          <div class="row" style="margin-top:6px">
            <a class="btn primary" href="#/k7">Xem lịch của tôi</a>
            <button class="btn" data-ics>Thêm vào lịch (.ics)</button>
            <div class="spacer"></div>
            <a class="btn ghost" href="#/k1" data-home>Về trang chủ</a>
          </div>
        </section>

        <aside class="stack">
          <div class="card">
            <div class="card-title">Bước tiếp theo</div>
            <ol class="small muted" style="margin:0;padding-left:18px;line-height:1.9">
              <li>Lễ tân duyệt lịch và gửi xác nhận qua Zalo/SMS.</li>
              <li>Bạn có thể tự đổi/hủy trước giờ hẹn 4 giờ.</li>
              <li>Đến trước 10 phút, mang giấy tờ tùy thân.</li>
            </ol>
          </div>
          <div class="card">
            <div class="card-title">Thông báo</div>
            <p class="small muted">Hệ thống đã ghi nhận yêu cầu gửi tin xác nhận. Nếu không nhận được tin, lễ tân sẽ gọi xác nhận.</p>
          </div>
        </aside>
      </div>`;
  },
  mount(root) {
    let appt = null;
    try { appt = JSON.parse(sessionStorage.getItem("qldapm_last_appt_72") || "null"); } catch { appt = null; }

    const el = root.querySelector("[data-countdown]");
    let stop = null;
    if (el && appt?.holdUntil) {
      const paint = () => {
        const left = new Date(appt.holdUntil).getTime() - Date.now();
        if (left <= 0) {
          el.textContent = "Đã hết hạn giữ chỗ";
          root.querySelector("[data-hold]").classList.remove("warn");
          root.querySelector("[data-hold]").classList.add("danger");
          if (stop) stop();
          return;
        }
        el.textContent = countdown(appt.holdUntil);
      };
      paint();
      stop = ticker(paint, 1000);
    }

    root.querySelector("[data-ics]").onclick = async () => {
      try {
        const res = await api.appointmentIcs(appt.id);
        const text = typeof res === "string" ? res : res.text;
        const blob = new Blob([text], { type: "text/calendar;charset=utf-8" });
        const a = document.createElement("a");
        a.href = URL.createObjectURL(blob);
        a.download = `${appt.code}.ics`;
        a.click();
        URL.revokeObjectURL(a.href);
      } catch (ex) {
        toast(ex.message || "Không tải được file lịch.", "err");
      }
    };

    const home = root.querySelector("[data-home]");
    if (home) home.onclick = () => { clearDraft(); };
    return () => { if (stop) stop(); };
  },
};
