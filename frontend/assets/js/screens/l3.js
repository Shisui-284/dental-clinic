/** L3 — Điều phối lịch trong ngày + Đã đến / Không đến / Hoàn tác */
import { api } from "../api/index.js";
import { esc, toast, drawer, confirmDanger, ticker, todayKey } from "../core/ui.js";
import { fmtDate, fmtTime, fmtRange, statusTag, sourceLabel, money, statusMeta } from "../core/format.js";

export default {
  id: "l3",
  title: "Điều phối lịch trong ngày",
  staff: true,
  async render(ctx) {
    const date = ctx.params.get("date") || todayKey();
    const doctorId = ctx.params.get("doctorId") || "";
    const status = ctx.params.get("status") || "";
    let items = [];
    let doctors = [];
    let leaves = [];
    let error = null;
    try {
      const [r, ds, sch] = await Promise.all([
        api.staffAppointments({ date }),
        api.doctors(),
        api.schedules(date),
      ]);
      items = r.items || [];
      doctors = ds.filter((d) => d.isActive);
      leaves = sch.leaves || [];
    } catch (ex) { error = ex.message; }

    const filtered = items
      .filter((a) => (doctorId ? a.doctorId === doctorId : true))
      .filter((a) => (status ? a.status === status : true));

    const kpi = {
      total: items.filter((a) => a.status !== "CANCELLED").length,
      confirmed: items.filter((a) => a.status === "CONFIRMED").length,
      checked: items.filter((a) => a.status === "CHECKED_IN").length,
      noshow: items.filter((a) => a.status === "NO_SHOW").length,
    };

    return `
      <div class="page-head">
        <div><h1>Điều phối lịch trong ngày</h1>
          <div class="sub">Cập nhật Đã đến / Không đến, hoàn tác trong cùng ngày, theo dõi lịch bị ảnh hưởng do bác sĩ nghỉ.</div></div>
        <div class="spacer"></div>
        <label class="row small">Ngày <input type="date" value="${esc(date)}" data-date style="width:160px;height:36px"></label>
      </div>

      ${error ? `<div class="banner danger">${esc(error)}</div>` : ""}
      ${leaves.length ? `<div class="banner danger"><b>${leaves.length} bác sĩ có lịch nghỉ trong ngày.</b>
        ${leaves.map((l) => esc(l.doctorName)).join(", ")}. Các lịch bị ảnh hưởng <b>không tự hủy/dời</b> — lễ tân liên hệ khách đổi lịch.</div>` : ""}

      <div class="kpi">
        <div class="k"><span>Tổng lịch (không tính đã hủy)</span><b>${kpi.total}</b></div>
        <div class="k"><span>Đã xác nhận</span><b>${kpi.confirmed}</b></div>
        <div class="k"><span>Đã đến</span><b>${kpi.checked}</b></div>
        <div class="k"><span>Không đến</span><b style="color:var(--danger)">${kpi.noshow}</b></div>
      </div>

      <div class="card" style="padding:14px 16px;margin-bottom:14px">
        <div class="row" style="flex-wrap:wrap">
          <label class="row small">Bác sĩ
            <select data-doctor style="width:180px;height:34px">
              <option value="">Tất cả</option>
              ${doctors.map((d) => `<option value="${esc(d.id)}" ${doctorId === d.id ? "selected" : ""}>${esc(d.name)}</option>`).join("")}
            </select></label>
          <label class="row small">Trạng thái
            <select data-status style="width:170px;height:34px">
              <option value="">Tất cả</option>
              ${Object.entries({ PENDING: "Chờ xác nhận", CONFIRMED: "Đã xác nhận", CHECKED_IN: "Đã đến", NO_SHOW: "Không đến", COMPLETED: "Đã hoàn tất" }).map(([k, v]) => `<option value="${k}" ${status === k ? "selected" : ""}>${v}</option>`).join("")}
            </select></label>
          <div class="spacer"></div>
          <a class="btn sm" href="#/l2">+ Tạo lịch</a>
          <a class="btn sm" href="#/l1">Duyệt lịch chờ</a>
        </div>
      </div>

      ${filtered.length ? `
        <div class="card">
          <div class="tl">
            ${filtered.map((a) => {
              const late = a.status === "CONFIRMED" && Date.now() - new Date(a.start).getTime() > 15 * 60000;
              const onLeave = leaves.some((l) => l.doctorId === a.doctorId);
              return `<div class="row-item" data-appt="${esc(a.id)}">
                <div class="time">${fmtTime(a.start)}</div>
                <div class="dot" style="border-color:${a.status === "NO_SHOW" ? "var(--danger)" : a.status === "CHECKED_IN" ? "#1D4ED8" : "var(--teal)"}"></div>
                <div class="appt ${late || onLeave ? "warn" : ""}">
                  <div class="row">
                    <b>${esc(a.patientName)}</b>
                    ${statusTag(a.status)}
                    ${a.isPriority ? '<span class="tag priority">Cấp cứu</span>' : ""}
                    ${late ? `<span class="tag noshow">Trễ ${Math.floor((Date.now() - new Date(a.start).getTime()) / 60000)} phút</span>` : ""}
                    ${onLeave ? '<span class="tag noshow">Bác sĩ nghỉ</span>' : ""}
                    <div class="spacer"></div>
                    <span class="small muted">${esc(a.code)}</span>
                  </div>
                  <div class="small muted" style="margin-top:4px">
                    ${fmtRange(a.start, a.duration)} · ${esc(a.doctorName || "Chưa chốt bác sĩ")} · Ghế ${esc(a.chairId || "—")} · ${esc(sourceLabel(a.source))} · ${esc(a.phone)}
                  </div>
                  <div class="small" style="margin-top:4px">${a.services.map((s) => esc(s.name)).join(" · ")}</div>
                  <div class="row" style="margin-top:8px;gap:6px">
                    ${a.status === "CONFIRMED" ? `
                      <button class="btn sm primary" data-checkin="${esc(a.id)}">Đã đến</button>
                      <button class="btn sm danger" data-noshow="${esc(a.id)}">Không đến</button>` : ""}
                    ${a.status === "NO_SHOW" ? `<button class="btn sm" data-reverse="${esc(a.id)}">Hoàn tác Không đến</button>` : ""}
                    ${a.status === "PENDING" ? `<a class="btn sm" href="#/l1?date=${esc(date)}">Sang L1 để duyệt</a>` : ""}
                    <button class="btn sm ghost" data-detail="${esc(a.id)}">Chi tiết</button>
                  </div>
                </div>
              </div>`;
            }).join("")}
          </div>
        </div>` : `<div class="empty"><b>Hôm nay chưa có lịch hẹn.</b>Thử bỏ bộ lọc hoặc chọn ngày khác.</div>`}

      <div class="help" style="margin-top:10px">${fmtDate(date + "T00:00:00")} · ${filtered.length}/${items.length} lịch · Quy tắc: Đã xác nhận → Đã đến; Không đến → hoàn tác trong cùng ngày (bắt buộc lý do).</div>`;
  },
  mount(root, ctx) {
    const date = ctx.params.get("date") || todayKey();
    const doctorId = ctx.params.get("doctorId") || "";
    const status = ctx.params.get("status") || "";

    root.querySelector("[data-date]").addEventListener("change", (e) => ctx.navigate("l3", { date: e.target.value, doctorId, status }));
    root.querySelector("[data-doctor]").addEventListener("change", (e) => ctx.navigate("l3", { date, doctorId: e.target.value, status }));
    root.querySelector("[data-status]").addEventListener("change", (e) => ctx.navigate("l3", { date, doctorId, status: e.target.value }));

    const stop = ticker(() => {
      // cập nhật nhãn trễ mỗi phút (đơn giản: refresh nhẹ nhãn)
      root.querySelectorAll("[data-appt]").forEach(() => {});
    }, 60000);

    root.addEventListener("click", async (e) => {
      const btn = e.target.closest("[data-checkin],[data-noshow],[data-reverse],[data-detail]");
      if (!btn) return;
      const id = btn.dataset.checkin || btn.dataset.noshow || btn.dataset.reverse || btn.dataset.detail;

      if (btn.dataset.checkin) {
        try { await api.checkIn(id); toast("Đã ghi nhận khách đến.", "ok"); ctx.refresh(); }
        catch (ex) { toast(ex.message, "err"); }
        return;
      }

      if (btn.dataset.noshow) {
        const ok = await confirmDanger({
          title: "Đánh dấu Không đến?",
          text: "Khách sẽ bị tính 1 lần vắng; từ 3 lần trong 12 tháng sẽ bị khóa tự đặt lịch.",
          confirmLabel: "Không đến",
        });
        if (!ok) return;
        try { await api.noShow(id); toast("Đã đánh dấu Không đến.", "ok"); ctx.refresh(); }
        catch (ex) { toast(ex.message, "err"); }
        return;
      }

      if (btn.dataset.reverse) {
        const reason = await confirmDanger({
          title: "Hoàn tác Không đến?",
          text: "Chỉ áp dụng trong cùng ngày khám. Bộ đếm vắng sẽ được hoàn lại.",
          confirmLabel: "Hoàn tác",
          requireReason: true,
          reasonLabel: "Lý do hoàn tác",
        });
        if (!reason) return;
        try { await api.reverseNoShow(id, reason); toast("Đã hoàn tác, lịch chuyển sang Đã đến.", "ok"); ctx.refresh(); }
        catch (ex) { toast(ex.message, "err"); }
        return;
      }

      if (btn.dataset.detail) {
        try {
          const r = await api.staffAppointments({ date });
          const a = (r.items || []).find((x) => x.id === id);
          if (a) showDetail(a, ctx);
        } catch (ex) { toast(ex.message, "err"); }
      }
    });

    return () => stop();
  },
};

function showDetail(a, ctx) {
  const m = statusMeta(a.status);
  const d = drawer({
    title: esc(a.patientName),
    sub: `${esc(a.code)} · ${statusTag(a.status)}`,
    body: `
      <dl class="kv">
        <dt>Ngày giờ</dt><dd>${fmtDate(a.start)} · ${fmtRange(a.start, a.duration)}</dd>
        <dt>Bác sĩ</dt><dd>${esc(a.doctorName || "Chưa chốt")}</dd>
        <dt>Ghế</dt><dd>${esc(a.chairId || "—")}</dd>
        <dt>SĐT</dt><dd>${esc(a.phone)}</dd>
        <dt>Nguồn</dt><dd>${esc(sourceLabel(a.source))}</dd>
        <dt>Dịch vụ</dt><dd>${a.services.map((s) => esc(s.name)).join("<br>")}</dd>
        <dt>Thời lượng</dt><dd>${a.duration} phút</dd>
        <dt>Tạm tính</dt><dd>${money(a.services.reduce((t, s) => t + s.price, 0))}</dd>
        <dt>Ghi chú</dt><dd>${esc(a.note || "—")}</dd>
      </dl>
      <div class="banner info small" style="margin-top:14px">${esc(m.note)}</div>
      ${a.cancelReason ? `<div class="banner danger small">Lý do hủy: ${esc(a.cancelReason)}</div>` : ""}
      ${a.reversedReason ? `<div class="banner warn small">Hoàn tác Không đến: ${esc(a.reversedReason)}</div>` : ""}`,
    footer: `<button class="btn" data-close>Đóng</button><div class="spacer"></div>
      ${a.status === "CONFIRMED" ? `<button class="btn primary" data-ci>Đã đến</button>` : ""}`,
  });

  d.root.querySelector("[data-close]").onclick = () => d.close();
  const ci = d.root.querySelector("[data-ci]");
  if (ci) ci.onclick = async () => {
    try { await api.checkIn(a.id); d.close(); toast("Đã ghi nhận khách đến.", "ok"); ctx.refresh(); }
    catch (ex) { toast(ex.message, "err"); }
  };
}
