/** L1 — Duyệt lịch chờ xác nhận (lễ tân) */
import { api } from "../api/index.js";
import { esc, toast, drawer, confirmDanger, ticker } from "../core/ui.js";
import { fmtDate, fmtTime, fmtRange, countdown, shortPhone, sourceLabel, money } from "../core/format.js";
import { isMock } from "../config.js";

export default {
  id: "l1",
  title: "Duyệt lịch chờ xác nhận",
  staff: true,
  async render(ctx) {
    const date = ctx.params.get("date") || "";
    let items = [];
    let error = null;
    let doctors = [];
    try {
      const [r, ds] = await Promise.all([
        api.staffAppointments({ status: "PENDING", ...(date ? { date } : {}) }),
        api.doctors(),
      ]);
      items = r.items || [];
      doctors = ds.filter((d) => d.isActive);
    } catch (ex) { error = ex.message; }

    return `
      <div class="page-head">
        <div>
          <h1>Duyệt lịch chờ xác nhận</h1>
          <div class="sub">Chốt bác sĩ & ghế nha trước khi hết hạn giữ chỗ. Không duyệt hàng loạt.</div>
        </div>
        <div class="spacer"></div>
        <label class="row small">Ngày <input type="date" value="${esc(date)}" data-date style="width:160px;height:36px"></label>
        <button class="btn sm" data-all ${date ? "" : "disabled"}>Tất cả sắp tới</button>
        <a class="btn sm" href="#/l2">+ Tạo lịch</a>
      </div>

      ${error ? `<div class="banner danger">${esc(error)}</div>` : ""}
      ${items.some((a) => a.holdUntil && new Date(a.holdUntil) < new Date()) ? `<div class="banner danger"><b>Có lịch đã hết hạn giữ chỗ.</b> Cần từ chối và liên hệ khách hẹn lại.</div>` : ""}

      <div class="card" style="padding:0;overflow:hidden">
        ${items.length ? `
          <table class="table">
            <thead><tr><th>Giờ</th><th>Bệnh nhân</th><th>Dịch vụ</th><th>Bác sĩ</th><th>Hạn giữ chỗ</th><th>Nguồn</th><th></th></tr></thead>
            <tbody>
              ${items.map((a) => {
                const expired = a.holdUntil && new Date(a.holdUntil).getTime() < Date.now();
                return `<tr data-row="${esc(a.id)}">
                  <td><b>${fmtTime(a.start)}</b><div class="small muted">${date ? fmtRange(a.start, a.duration) : fmtDate(a.start) + " · " + fmtRange(a.start, a.duration)}</div></td>
                  <td><b>${esc(a.patientName)}</b><div class="small muted">${esc(shortPhone(a.phone))}</div></td>
                  <td class="small">${a.services.map((s) => esc(s.name)).join("<br>")}</td>
                  <td>${esc(a.doctorName || "Chưa chốt")}</td>
                  <td>${a.holdUntil ? `<span class="countdown" data-cd="${esc(a.holdUntil)}">${countdown(a.holdUntil)}</span>` : "—"}
                    ${expired ? '<div class="small" style="color:var(--danger)">Hết hạn</div>' : ""}</td>
                  <td class="small">${esc(sourceLabel(a.source))}${a.isPriority ? ' <span class="tag priority">Cấp cứu</span>' : ""}</td>
                  <td class="nowrap"><button class="btn sm primary" data-open="${esc(a.id)}">Xử lý</button></td>
                </tr>`;
              }).join("")}
            </tbody>
          </table>` : `<div class="empty" style="border:0"><b>Không có lịch chờ duyệt.</b>Tất cả đã được xử lý.</div>`}
      </div>
      <div class="help" style="margin-top:10px">${date ? `Ngày ${fmtDate(date + "T00:00:00")}` : "Tất cả lịch chờ sắp tới"} · ${items.length} lịch chờ · Hạn giữ chỗ mặc định 30 phút.</div>`;
  },
  mount(root, ctx) {
    const date = ctx.params.get("date") || "";

    root.querySelector("[data-date]").addEventListener("change", (e) => ctx.navigate("l1", { date: e.target.value }));
    root.querySelector("[data-all]").addEventListener("click", () => ctx.navigate("l1"));

    const stop = ticker(() => {
      root.querySelectorAll("[data-cd]").forEach((n) => {
        n.textContent = countdown(n.dataset.cd);
        if (new Date(n.dataset.cd).getTime() < Date.now()) n.style.color = "var(--danger)";
      });
    }, 1000);

    root.addEventListener("click", async (e) => {
      const b = e.target.closest("[data-open]");
      if (!b) return;
      const id = b.dataset.open;
      try {
        const [r, ds] = await Promise.all([api.staffAppointments({ status: "PENDING", ...(date ? { date } : {}) }), api.doctors()]);
        const a = (r.items || []).find((x) => x.id === id);
        if (!a) return;
        openDetail(a, ds.filter((d) => d.isActive), ctx);
      } catch (ex) { toast(ex.message, "err"); }
    });

    return () => stop();
  },
};

function openDetail(a, doctors, ctx) {
  const expired = a.holdUntil && new Date(a.holdUntil).getTime() < Date.now();
  const d = drawer({
    title: esc(a.patientName),
    sub: `${fmtDate(a.start)} · ${fmtRange(a.start, a.duration)} · ${esc(a.code)}`,
    body: `
      ${expired ? `<div class="banner danger"><b>Đã hết hạn giữ chỗ.</b> Không thể duyệt — cần từ chối và hẹn lại.</div>` : ""}
      <dl class="kv">
        <dt>Số điện thoại</dt><dd>${esc(a.phone)}</dd>
        <dt>Dịch vụ</dt><dd>${a.services.map((s) => esc(s.name)).join("<br>")}</dd>
        <dt>Thời lượng</dt><dd>${a.duration} phút</dd>
        <dt>Tạm tính</dt><dd>${money(a.services.reduce((t, s) => t + s.price, 0))}</dd>
        <dt>Nguồn</dt><dd>${esc(sourceLabel(a.source))}</dd>
        <dt>Hạn giữ chỗ</dt><dd>${a.holdUntil ? countdown(a.holdUntil) : "—"}</dd>
      </dl>
      <hr class="sep">
      <label class="field"><span class="lbl">Bác sĩ <span class="req">*</span></span>
        <select data-doc ${expired ? "disabled" : ""}>
          <option value="">— Chọn bác sĩ —</option>
          ${doctors.map((x) => `<option value="${esc(x.id)}" ${a.doctorId === x.id ? "selected" : ""}>${esc(x.name)} · ${esc(x.title)}</option>`).join("")}
        </select>
      </label>
      <label class="field"><span class="lbl">Ghế nha</span>
        ${isMock()
          ? `<select data-chair ${expired ? "disabled" : ""}>
              ${["GH1", "GH2", "GH3", "GH4"].map((c) => `<option ${a.chairId === c ? "selected" : ""}>${c}</option>`).join("")}
            </select>
            <div class="help">Hệ thống đã gán ghế trống; lễ tân có thể đổi.</div>`
          : `<input type="text" value="${esc(a.chairId || "Hệ thống tự gán")}" disabled>
            <div class="help">Live API: backend tự gán/giữ ghế trống khi duyệt.</div>`}
      </label>`,
    footer: `<button class="btn danger" data-reject>Từ chối</button><div class="spacer"></div>
      <button class="btn primary" data-confirm ${expired ? "disabled" : ""}>Xác nhận lịch</button>`,
  });

  d.root.querySelector("[data-confirm]").onclick = async () => {
    const doctorId = d.root.querySelector("[data-doc]").value;
    if (!doctorId) { toast("Chọn bác sĩ trước khi duyệt.", "err"); return; }
    try {
      await api.staffConfirm(a.id, {
        doctorId,
        ...(isMock() ? { chairId: d.root.querySelector("[data-chair]").value } : {}),
      });
      toast("Đã xác nhận lịch và gửi thông báo cho khách.", "ok");
      d.close();
      ctx.refresh();
    } catch (ex) { toast(ex.message, "err"); }
  };

  d.root.querySelector("[data-reject]").onclick = async () => {
    const reason = await confirmDanger({
      title: "Từ chối lịch này?",
      text: expired ? "Lịch đã hết hạn giữ chỗ — hệ thống sẽ chuyển sang Đã hủy." : "Lịch sẽ chuyển sang Đã hủy và khung giờ được giải phóng.",
      confirmLabel: "Từ chối",
      requireReason: true,
      reasonLabel: "Lý do từ chối",
    });
    if (!reason) return;
    try {
      await api.staffCancel(a.id, reason);
      toast("Đã từ chối lịch.", "ok");
      d.close();
      ctx.refresh();
    } catch (ex) { toast(ex.message, "err"); }
  };
}
