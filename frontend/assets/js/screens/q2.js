/** Q2 — Quản lý danh sách Blacklist & Mở khóa đặc cách */
import { api } from "../api/index.js";
import { esc, toast, confirmDanger } from "../core/ui.js";

export default {
  id: "q2",
  title: "Quản lý Blacklist (Q2)",
  staff: true,
  async render() {
    let items = [];
    let error = null;
    try {
      const res = await api.managerBlacklist();
      items = Array.isArray(res) ? res : [];
    } catch (ex) {
      error = ex.message;
    }

    return `
      <div class="page-head">
        <div>
          <h1>Quản lý danh sách hạn chế đặt lịch (Blacklist)</h1>
          <div class="sub">Khách vi phạm Không đến từ 3 lần trong 12 tháng sẽ tự động bị khóa tự đặt lịch online. Quản lý có thể mở khóa đặc cách kèm lý do (giữ nguyên lịch sử no_show_event & ghi audit_log).</div>
        </div>
      </div>

      ${error ? `<div class="banner danger">${esc(error)}</div>` : ""}

      <div class="kpi">
        <div class="k"><span>Số SĐT đang bị khóa</span><b style="color:${items.length ? "var(--danger)" : "inherit"}">${items.length}</b></div>
        <div class="k"><span>Ngưỡng tự động khóa</span><b>3 lần / 12 tháng</b></div>
      </div>

      <div class="card" style="padding:0;overflow:hidden">
        ${items.length ? `
          <table class="table">
            <thead><tr><th>Số điện thoại</th><th>Hồ sơ bệnh nhân</th><th>Số lần Không đến (12 tháng)</th><th>Trạng thái</th><th></th></tr></thead>
            <tbody>
              ${items.map((b) => `
                <tr>
                  <td><b>${esc(b.phone)}</b><div class="small muted">Customer #${esc(b.customerId)}</div></td>
                  <td class="small">${b.patientNames.length ? b.patientNames.map(esc).join(", ") : "—"}</td>
                  <td><b style="color:var(--danger)">${b.activeNoShowCount} / ${b.threshold}</b> lần${b.stillExceedsThreshold ? ' <span class="tag noshow">Vượt ngưỡng</span>' : ""}</td>
                  <td><span class="tag noshow">Đang khóa tự đặt</span></td>
                  <td class="nowrap"><button class="btn sm primary" data-unlock="${esc(b.customerId)}" data-phone="${esc(b.phone)}">Mở khóa đặc cách</button></td>
                </tr>`).join("")}
            </tbody>
          </table>` : `<div class="empty" style="border:0"><b>Không có số điện thoại nào đang bị khóa</b>Tất cả khách hàng hiện đều có thể tự đặt lịch trực tuyến.</div>`}
      </div>`;
  },
  mount(root, ctx) {
    root.addEventListener("click", async (e) => {
      const btn = e.target.closest("[data-unlock]");
      if (!btn) return;
      const customerId = btn.dataset.unlock;
      const phone = btn.dataset.phone;
      const reason = await confirmDanger({
        title: `Mở khóa Blacklist cho ${phone}?`,
        text: "Các bản ghi lịch sử no_show_event cũ vẫn được giữ nguyên. Nếu khách tiếp tục vắng thêm 1 lần, hệ thống sẽ tự động khóa lại. Bắt buộc nhập lý do mở khóa.",
        confirmLabel: "Xác nhận mở khóa",
        requireReason: true,
        reasonLabel: "Lý do mở khóa đặc cách",
      });
      if (!reason) return;
      try {
        const res = await api.unlockBlacklist(customerId, reason);
        toast(res?.warning ? `Đã mở khóa ${phone}. Lưu ý: ${res.warning}` : `Đã mở khóa Blacklist cho ${phone}.`, "ok");
        ctx.refresh();
      } catch (ex) {
        toast(ex.message, "err");
      }
    });

    return null;
  },
};

