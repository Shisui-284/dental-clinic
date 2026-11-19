/** K4 — Chọn ngày giờ (khung trống động) + chế độ đổi lịch (?id=) */
import { api } from "../api/index.js";
import { getDraft, setDraft, session } from "../core/session.js";
import { navigate } from "../core/router.js";
import { esc, toast } from "../core/ui.js";
import { money, fmtTime, weekday, pad } from "../core/format.js";

function key(d) {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

export default {
  id: "k4",
  title: "Chọn ngày giờ",
  step: 4,
  async render(ctx) {
    const d = getDraft();
    const changeId = ctx.params.get("id");
    if (!d.serviceIds?.length) {
      return `<div class="empty"><b>Chưa chọn dịch vụ</b>Vui lòng quay lại bước chọn dịch vụ. <div style="margin-top:12px"><a class="btn primary" href="#/k3">Chọn dịch vụ</a></div></div>`;
    }
    const date = ctx.params.get("date") || key(new Date(Date.now() + 86400000));
    const [res, services] = await Promise.all([
      api.slots({
        date,
        serviceIds: d.serviceIds,
        doctorId: d.doctorId,
        phone: d.phone,
        patientFullName: d.patient?.fullName,
        birthYear: d.patient?.birthYear,
      }),
      api.services(),
    ]);

    const days = Array.from({ length: 30 }, (_, i) => {
      const x = new Date();
      x.setDate(x.getDate() + i);
      return { k: key(x), d: x };
    });
    const morning = res.slots.filter((s) => new Date(s.start).getHours() < 12);
    const afternoon = res.slots.filter((s) => new Date(s.start).getHours() >= 12);
    const selected = d.start || null;

    const price = services.filter((s) => d.serviceIds.includes(s.id)).reduce((t, s) => t + s.price, 0);
    const priceText = price ? money(price) : "";

    return `
      <div class="cust-grid">
        <section class="stack">
          ${changeId ? `<div class="banner info">Bạn đang <b>đổi lịch hẹn</b>. Chọn khung giờ mới — lịch sẽ quay lại trạng thái Chờ xác nhận.</div>` : ""}
          <div class="card">
            <div class="row" style="justify-content:space-between;margin-bottom:10px">
              <h2>Chọn ngày</h2>
              <span class="small muted">Chỉ đặt trước 2 giờ – 30 ngày</span>
            </div>
            <div class="days">
              ${days.map((x) => `<button type="button" class="day ${x.k === date ? "on" : ""}" data-date="${x.k}">
                <b>${pad(x.d.getDate())}</b>${weekday(x.d)}<br><span class="small">${pad(x.d.getMonth() + 1)}</span></button>`).join("")}
            </div>
          </div>

          <div class="card">
            <div class="card-title">Buổi sáng <span class="count">08:00–11:30</span></div>
            <div class="slots">${morning.map((s) => slot(s, selected)).join("") || '<div class="small muted">Không có khung buổi sáng.</div>'}</div>
            <div class="card-title" style="margin-top:18px">Buổi chiều <span class="count">13:30–17:30</span></div>
            <div class="slots">${afternoon.map((s) => slot(s, selected)).join("") || '<div class="small muted">Không có khung buổi chiều.</div>'}</div>
            <div class="help" style="margin-top:12px">Giờ gạch = đã kín hoặc ngoài thời gian cho phép. Hệ thống tự gán ghế nha, bạn không cần chọn ghế.</div>
          </div>
        </section>

        <aside class="summary">
          <div class="card">
            <div class="card-title">Tóm tắt</div>
            <div class="line"><span>Số dịch vụ</span><b>${d.serviceIds.length}</b></div>
            <div class="line"><span>Thời lượng</span><b data-dur="${res.duration}">${res.duration} phút</b></div>
            <div class="line"><span class="small muted">Đã gồm đệm ghế</span></div>
            <div class="line"><span>Ngày</span><b>${esc(date.split("-").reverse().join("/"))}</b></div>
            <div class="line"><span>Giờ</span><b data-picked>${selected ? fmtTime(selected) : "Chưa chọn"}</b></div>
            ${priceText ? `<div class="line"><span>Giá tham khảo</span><b>${priceText}</b></div>` : ""}
            <hr class="sep">
            <button class="btn primary block" data-next ${selected ? "" : "disabled"}>${changeId ? "Xác nhận đổi lịch" : "Tiếp tục xác thực"}</button>
            <a class="btn block" href="#/k3" style="margin-top:8px">Quay lại</a>
          </div>
        </aside>
      </div>`;
  },
  mount(root, ctx) {
    const d = getDraft();
    const changeId = ctx.params.get("id");
    const date = ctx.params.get("date");
    let picked = d.start || null;
    const btn = root.querySelector("[data-next]");

    root.querySelectorAll("[data-date]").forEach((b) => {
      b.addEventListener("click", () => {
        setDraft({ start: null });
        const p = new URLSearchParams({ date: b.dataset.date });
        if (changeId) p.set("id", changeId);
        navigate("k4", p);
      });
    });

    root.querySelectorAll("[data-slot]").forEach((b) => {
      if (b.disabled) return;
      b.addEventListener("click", () => {
        picked = b.dataset.slot;
        root.querySelectorAll("[data-slot]").forEach((x) => x.classList.toggle("on", x === b));
        const label = root.querySelector("[data-picked]");
        if (label) label.textContent = fmtTime(picked);
        if (btn) btn.disabled = false;
      });
    });

    if (btn) btn.addEventListener("click", async () => {
      if (!picked) return;
      btn.disabled = true;
      btn.textContent = "Đang kiểm tra...";
      try {
        if (changeId) {
          await api.reschedule(changeId, { start: picked, doctorId: d.doctorId || undefined });
          toast("Đã cập nhật lịch hẹn. Vui lòng chờ lễ tân xác nhận lại.", "ok");
          navigate("k7");
        } else {
          const durEl = root.querySelector("[data-dur]");
          const durationHint = durEl ? Number(durEl.dataset.dur) || undefined : undefined;
          setDraft({ start: picked, ...(durationHint ? { durationHint } : {}) });
          navigate("k5");
        }
      } catch (ex) {
        root.querySelector(".stack")?.insertAdjacentHTML("afterbegin",
          `<div class="banner danger">${esc(ex.message)} <a href="#/k4${changeId ? "?id=" + changeId : ""}">Chọn giờ khác</a></div>`);
        btn.disabled = false;
        btn.textContent = changeId ? "Xác nhận đổi lịch" : "Tiếp tục xác thực";
      }
    });

    return null;
  },
};

function slot(s, selected) {
  return `<button type="button" class="slot ${s.start === selected ? "on" : ""}" data-slot="${s.start}" ${s.available ? "" : "disabled"}>${fmtTime(s.start)}</button>`;
}
