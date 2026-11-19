/** Q1 — Quản lý duyệt nghỉ bác sĩ & Cảnh báo xung đột lịch hẹn */
import { api } from "../api/index.js";
import { esc, toast, confirmDanger } from "../core/ui.js";
import { fmtDate, fmtTime } from "../core/format.js";

function leaveTag(st) {
  if (st === "APPROVED") return '<span class="tag confirmed">Đã duyệt</span>';
  if (st === "REJECTED") return '<span class="tag noshow">Từ chối</span>';
  return '<span class="tag pending">Chờ duyệt</span>';
}

export default {
  id: "q1",
  title: "Duyệt nghỉ bác sĩ & Xung đột (Q1)",
  staff: true,
  async render(ctx) {
    const status = ctx.params.get("status") || "";
    let leaves = [];
    let conflicts = [];
    let error = null;

    try {
      const [lv, cf] = await Promise.all([
        api.staffLeaves(status ? { status } : {}),
        api.leaveConflicts({}),
      ]);
      leaves = Array.isArray(lv) ? lv : [];
      conflicts = Array.isArray(cf) ? cf : [];
    } catch (ex) {
      error = ex.message;
    }

    const pendingCount = leaves.filter((l) => l.status === "PENDING").length;

    return `
      <div class="page-head">
        <div>
          <h1>Duyệt đơn nghỉ bác sĩ & Cảnh báo xung đột</h1>
          <div class="sub">Khi duyệt nghỉ, hệ thống truy vấn View <code>v_appointment_leave_conflict</code> cảnh báo các lịch CONFIRMED bị trùng để Lễ tân chủ động gọi khách dời lịch.</div>
        </div>
        <div class="spacer"></div>
        <label class="row small">Trạng thái
          <select data-status style="width:160px;height:36px">
            <option value="">Tất cả</option>
            <option value="PENDING" ${status === "PENDING" ? "selected" : ""}>Chờ duyệt</option>
            <option value="APPROVED" ${status === "APPROVED" ? "selected" : ""}>Đã duyệt</option>
            <option value="REJECTED" ${status === "REJECTED" ? "selected" : ""}>Đã từ chối</option>
          </select>
        </label>
      </div>

      ${error ? `<div class="banner danger">${esc(error)}</div>` : ""}
      ${conflicts.length ? `<div class="banner danger"><b>CẢNH BÁO: Có ${conflicts.length} lịch hẹn CONFIRMED đang trùng với ca nghỉ đã duyệt của bác sĩ!</b> Hệ thống không tự động hủy lịch khách — vui lòng chuyển Lễ tân tại L3 gọi khách dời lịch.</div>` : ""}

      <div class="kpi">
        <div class="k"><span>Tổng đơn xin nghỉ</span><b>${leaves.length}</b></div>
        <div class="k"><span>Đang chờ duyệt</span><b>${pendingCount}</b></div>
        <div class="k"><span>Lịch hẹn trùng ca nghỉ</span><b style="color:${conflicts.length ? "var(--danger)" : "inherit"}">${conflicts.length}</b></div>
      </div>

      <div class="card" style="padding:0;overflow:hidden;margin-bottom:16px">
        <div style="padding:14px 16px;border-bottom:1px solid var(--line)"><b>Danh sách đơn xin nghỉ của bác sĩ</b></div>
        ${leaves.length ? `
          <table class="table">
            <thead><tr><th>Bác sĩ</th><th>Ngày nghỉ</th><th>Khung giờ</th><th>Lý do</th><th>Trạng thái</th><th>Thao tác</th></tr></thead>
            <tbody>
              ${leaves.map((l) => `
                <tr>
                  <td><b>${esc(l.doctorName)}</b></td>
                  <td>${fmtDate(l.leaveDate + "T00:00:00")}</td>
                  <td>${esc(l.startTime)} – ${esc(l.endTime)}</td>
                  <td class="small">${esc(l.reason)}</td>
                  <td>${leaveTag(l.status)}${l.approvedByName ? `<div class="small muted">${esc(l.approvedByName)}</div>` : ""}</td>
                  <td class="nowrap">
                    ${l.status === "PENDING" ? `
                      <button class="btn sm primary" data-approve="${esc(l.id)}">Duyệt</button>
                      <button class="btn sm danger" data-reject="${esc(l.id)}">Từ chối</button>` : `<span class="small muted">Đã xử lý</span>`}
                  </td>
                </tr>`).join("")}
            </tbody>
          </table>` : `<div class="empty" style="border:0"><b>Không có đơn xin nghỉ nào</b>Thử đổi bộ lọc trạng thái.</div>`}
      </div>

      <div class="card" style="padding:0;overflow:hidden">
        <div class="row" style="padding:14px 16px;border-bottom:1px solid var(--line);justify-content:space-between">
          <b>Lịch hẹn CONFIRMED bị trùng ca nghỉ (v_appointment_leave_conflict)</b>
          <span class="small muted">${conflicts.length} lịch cần dời</span>
        </div>
        ${conflicts.length ? `
          <table class="table">
            <thead><tr><th>Mã lịch</th><th>Bệnh nhân</th><th>SĐT</th><th>Bác sĩ nghỉ</th><th>Giờ hẹn</th><th>Ca nghỉ</th><th></th></tr></thead>
            <tbody>
              ${conflicts.map((c) => `
                <tr>
                  <td><b>${esc(c.appointmentCode)}</b></td>
                  <td>${esc(c.patientName)}</td>
                  <td>${esc(c.phone)}</td>
                  <td>${esc(c.doctorName)}</td>
                  <td>${fmtDate(c.dateTime)} · <b>${fmtTime(c.dateTime)}</b></td>
                  <td class="small">${esc(c.leaveStartTime)}–${esc(c.leaveEndTime)} (${esc(c.leaveReason)})</td>
                  <td class="nowrap"><a class="btn sm primary" href="#/l3?date=${esc(c.leaveDate)}">Dời lịch tại L3</a></td>
                </tr>`).join("")}
            </tbody>
          </table>` : `<div class="empty" style="border:0"><b>Không có xung đột lịch hẹn</b>Tất cả lịch đã xác nhận đều an toàn.</div>`}
      </div>`;
  },
  mount(root, ctx) {
    root.querySelector("[data-status]").addEventListener("change", (e) => ctx.navigate("q1", { status: e.target.value }));

    root.addEventListener("click", async (e) => {
      const btn = e.target.closest("[data-approve],[data-reject]");
      if (!btn) return;
      const isApprove = !!btn.dataset.approve;
      const id = btn.dataset.approve || btn.dataset.reject;

      const ok = await confirmDanger({
        title: isApprove ? "Phê duyệt đơn xin nghỉ?" : "Từ chối đơn xin nghỉ?",
        text: isApprove
          ? "Hệ thống sẽ kiểm tra các lịch CONFIRMED trùng khung giờ nghỉ và cảnh báo ngay cho Lễ tân."
          : "Đơn xin nghỉ sẽ chuyển sang trạng thái Từ chối.",
        confirmLabel: isApprove ? "Duyệt nghỉ" : "Từ chối",
      });
      if (!ok) return;

      try {
        const res = await api.approveLeave(id, isApprove ? "APPROVED" : "REJECTED");
        if (isApprove && res?.conflictCount > 0) {
          toast(`Đã duyệt nghỉ — CẢNH BÁO có ${res.conflictCount} lịch CONFIRMED bị trùng!`, "err");
        } else {
          toast(isApprove ? "Đã duyệt đơn xin nghỉ." : "Đã từ chối đơn xin nghỉ.", "ok");
        }
        ctx.refresh();
      } catch (ex) {
        toast(ex.message, "err");
      }
    });

    return null;
  },
};
