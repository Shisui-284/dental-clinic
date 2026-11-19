/** B1 — Cổng Bác sĩ: Lịch khám cá nhân, ca làm việc & gửi đơn xin nghỉ */
import { api } from "../api/index.js";
import { session } from "../core/session.js";
import { esc, toast, todayKey } from "../core/ui.js";
import { fmtDate, fmtTime, fmtRange, statusTag, sourceLabel } from "../core/format.js";

function leaveStatusTag(st) {
  if (st === "APPROVED") return '<span class="tag confirmed">Đã duyệt</span>';
  if (st === "REJECTED") return '<span class="tag noshow">Từ chối</span>';
  return '<span class="tag pending">Chờ duyệt</span>';
}

export default {
  id: "b1",
  title: "Lịch khám cá nhân & Xin nghỉ (B1)",
  staff: true,
  async render(ctx) {
    const date = ctx.params.get("date") || "";
    const me = session.staff || {};
    let appointments = [];
    let schedules = [];
    let leaves = [];
    let error = null;

    try {
      const [apRes, scRes, lvRes] = await Promise.all([
        api.staffAppointments(date ? { date } : {}),
        api.doctorSchedules({}),
        api.staffLeaves({}),
      ]);
      appointments = apRes.items || [];
      schedules = Array.isArray(scRes) ? scRes : [];
      leaves = Array.isArray(lvRes) ? lvRes : [];
    } catch (ex) {
      error = ex.message;
    }

    const kpi = {
      total: appointments.filter((a) => a.status !== "CANCELLED").length,
      confirmed: appointments.filter((a) => a.status === "CONFIRMED").length,
      checked: appointments.filter((a) => a.status === "CHECKED_IN").length,
      pendingLeaves: leaves.filter((l) => l.status === "PENDING").length,
    };

    return `
      <div class="page-head">
        <div>
          <h1>Lịch khám của ${esc(me.name || "Bác sĩ")}</h1>
          <div class="sub">Bác sĩ chỉ xem được lịch hẹn và ca làm việc thuộc về chính mình (không tạo, duyệt, đổi hoặc hủy lịch).</div>
        </div>
        <div class="spacer"></div>
        <label class="row small">Lọc ngày <input type="date" value="${esc(date)}" data-date style="width:160px;height:36px"></label>
        <button class="btn sm" data-all ${date ? "" : "disabled"}>Tất cả lịch</button>
      </div>

      ${error ? `<div class="banner danger">${esc(error)}</div>` : ""}

      <div class="kpi">
        <div class="k"><span>Tổng lịch cá nhân</span><b>${kpi.total}</b></div>
        <div class="k"><span>Đã xác nhận</span><b>${kpi.confirmed}</b></div>
        <div class="k"><span>Khách đã đến</span><b>${kpi.checked}</b></div>
        <div class="k"><span>Đơn nghỉ chờ duyệt</span><b>${kpi.pendingLeaves}</b></div>
      </div>

      <div class="grid-form">
        <section class="stack">
          <div class="card">
            <div class="row" style="justify-content:space-between;margin-bottom:10px">
              <div class="card-title" style="margin:0">Danh sách lịch hẹn cá nhân</div>
              <span class="small muted">${date ? `Ngày ${fmtDate(date + "T00:00:00")}` : "Toàn bộ lịch được phân công"}</span>
            </div>
            ${appointments.length ? `
              <div class="tl">
                ${appointments.map((a) => `
                  <div class="row-item">
                    <div class="time">${fmtTime(a.start)}<div class="small muted">${fmtDate(a.start)}</div></div>
                    <div class="dot" style="border-color:${a.status === "CHECKED_IN" ? "#1D4ED8" : a.status === "NO_SHOW" ? "var(--danger)" : "var(--teal)"}"></div>
                    <div class="appt ${a.hasLeaveConflict ? "warn" : ""}">
                      <div class="row">
                        <b>${esc(a.patientName)}</b>
                        ${statusTag(a.status)}
                        ${a.isPriority ? '<span class="tag priority">Cấp cứu</span>' : ""}
                        ${a.hasLeaveConflict ? '<span class="tag noshow">Trùng ca nghỉ</span>' : ""}
                        <div class="spacer"></div>
                        <span class="small muted">${esc(a.code)}</span>
                      </div>
                      <div class="small muted" style="margin-top:4px">
                        ${fmtRange(a.start, a.duration)} · Ghế ${esc(a.chairId || "—")} · ${esc(sourceLabel(a.source))} · ${esc(a.phone)}
                      </div>
                      <div class="small" style="margin-top:4px">${a.services.map((s) => esc(s.name)).join(" · ")}</div>
                      ${a.note ? `<div class="small muted" style="margin-top:4px">Ghi chú: ${esc(a.note)}</div>` : ""}
                    </div>
                  </div>`).join("")}
              </div>` : `<div class="empty"><b>Chưa có lịch hẹn nào</b>${date ? "Thử chọn ngày khác hoặc bấm Tất cả lịch." : "Bạn chưa có lịch hẹn được phân công."}</div>`}
          </div>

          <div class="card">
            <div class="card-title">Ca làm việc được phân công</div>
            ${schedules.length ? `
              <table class="table">
                <thead><tr><th>Ngày</th><th>Khung giờ ca</th><th>Trạng thái</th></tr></thead>
                <tbody>
                  ${schedules.map((s) => `
                    <tr>
                      <td><b>${fmtDate(s.shiftDate + "T00:00:00")}</b></td>
                      <td>${esc(s.startTime)} – ${esc(s.endTime)}</td>
                      <td><span class="tag confirmed">${esc(s.status)}</span></td>
                    </tr>`).join("")}
                </tbody>
              </table>` : `<div class="small muted">Chưa có ca làm việc nào được cấu hình.</div>`}
          </div>
        </section>

        <section class="stack">
          <div class="card">
            <div class="card-title">Gửi đơn xin nghỉ phép / nghỉ đột xuất</div>
            <p class="small muted" style="margin-top:0">Đơn sẽ gửi tới Quản lý (màn Q1) phê duyệt. Nếu có lịch CONFIRMED bị trùng, hệ thống sẽ tự cảnh báo cho Lễ tân gọi khách dời lịch.</p>
            <form data-leave-form>
              <label class="field"><span class="lbl">Ngày nghỉ <span class="req">*</span></span>
                <input type="date" name="leaveDate" value="${todayKey()}" required>
              </label>
              <div class="grid-2">
                <label class="field"><span class="lbl">Từ giờ <span class="req">*</span></span>
                  <input type="time" name="startTime" value="08:00" required>
                </label>
                <label class="field"><span class="lbl">Đến giờ <span class="req">*</span></span>
                  <input type="time" name="endTime" value="12:00" required>
                </label>
              </div>
              <label class="field"><span class="lbl">Lý do xin nghỉ <span class="req">*</span></span>
                <textarea name="reason" maxlength="255" placeholder="VD: Bận việc gia đình đột xuất / tham dự hội thảo chuyên môn..." required></textarea>
              </label>
              <div data-leave-err></div>
              <button class="btn primary block" type="submit" data-leave-submit>Gửi đơn xin nghỉ</button>
            </form>
          </div>

          <div class="card">
            <div class="card-title">Đơn xin nghỉ đã gửi (${leaves.length})</div>
            ${leaves.length ? `
              <div class="stack">
                ${leaves.map((l) => `
                  <div style="border:1px solid var(--line);border-radius:10px;padding:10px 12px">
                    <div class="row">
                      <b>${fmtDate(l.leaveDate + "T00:00:00")} · ${esc(l.startTime)}–${esc(l.endTime)}</b>
                      <div class="spacer"></div>
                      ${leaveStatusTag(l.status)}
                    </div>
                    <div class="small muted" style="margin-top:4px">${esc(l.reason)}</div>
                    ${l.approvedByName ? `<div class="small muted" style="margin-top:2px">Xử lý bởi: ${esc(l.approvedByName)}</div>` : ""}
                  </div>`).join("")}
              </div>` : `<div class="small muted">Bạn chưa gửi đơn xin nghỉ nào.</div>`}
          </div>
        </section>
      </div>`;
  },
  mount(root, ctx) {
    root.querySelector("[data-date]").addEventListener("change", (e) => ctx.navigate("b1", { date: e.target.value }));
    root.querySelector("[data-all]").addEventListener("click", () => ctx.navigate("b1"));

    const form = root.querySelector("[data-leave-form]");
    const errBox = root.querySelector("[data-leave-err]");
    const btn = root.querySelector("[data-leave-submit]");

    form.addEventListener("submit", async (e) => {
      e.preventDefault();
      errBox.innerHTML = "";
      const fd = new FormData(form);
      const leaveDate = String(fd.get("leaveDate") || "");
      const startTime = String(fd.get("startTime") || "");
      const endTime = String(fd.get("endTime") || "");
      const reason = String(fd.get("reason") || "").trim();

      if (!leaveDate || !startTime || !endTime || !reason) {
        errBox.innerHTML = `<div class="banner danger">Vui lòng điền đầy đủ ngày, khung giờ và lý do xin nghỉ.</div>`;
        return;
      }
      if (endTime <= startTime) {
        errBox.innerHTML = `<div class="banner danger">Giờ kết thúc nghỉ phải lớn hơn giờ bắt đầu.</div>`;
        return;
      }

      btn.disabled = true;
      btn.textContent = "Đang gửi...";
      try {
        await api.doctorRequestLeave({
          leaveDate,
          startTime: startTime.length === 5 ? `${startTime}:00` : startTime,
          endTime: endTime.length === 5 ? `${endTime}:00` : endTime,
          reason,
        });
        toast("Đã gửi đơn xin nghỉ, chờ Quản lý phê duyệt.", "ok");
        ctx.refresh();
      } catch (ex) {
        errBox.innerHTML = `<div class="banner danger">${esc(ex.message)}</div>`;
        btn.disabled = false;
        btn.textContent = "Gửi đơn xin nghỉ";
      }
    });

    return null;
  },
};
