/** K3 — Chọn dịch vụ và bác sĩ */
import { api } from "../api/index.js";
import { getDraft, setDraft } from "../core/session.js";
import { navigate } from "../core/router.js";
import { esc, toast } from "../core/ui.js";
import { money } from "../core/format.js";

export default {
  id: "k3",
  title: "Chọn dịch vụ & bác sĩ",
  step: 3,
  async render() {
    const d = getDraft();
    const [services, doctors] = await Promise.all([api.services(), api.doctors()]);
    const groups = ["Tất cả", ...new Set(services.map((s) => s.group))];
    const selected = new Set(d.serviceIds || []);
    const picked = d.doctorId || "";

    return `
      <div class="cust-grid">
        <section class="stack">
          <div class="card">
            <div class="row" style="justify-content:space-between;margin-bottom:10px">
              <h2>Chọn dịch vụ</h2>
              <span class="small muted">Giá tham khảo, thanh toán tại phòng khám</span>
            </div>
            <div class="row" style="flex-wrap:wrap;gap:6px;margin-bottom:12px" data-groups>
              ${groups.map((g, i) => `<button type="button" class="chip ${i === 0 ? "on" : ""}" data-group="${esc(g)}">${esc(g)}</button>`).join("")}
            </div>
            <div class="stack" data-services>
              ${services.map((s) => `
                <label class="svc ${selected.has(s.id) ? "on" : ""}" data-svc="${esc(s.id)}" data-group="${esc(s.group)}">
                  <input type="checkbox" ${selected.has(s.id) ? "checked" : ""}>
                  <div class="grow">
                    <div class="name">${esc(s.name)}</div>
                    <div class="meta">${esc(s.group)} · ${s.duration} phút</div>
                  </div>
                  <div class="price">${money(s.price)}</div>
                </label>`).join("")}
            </div>
          </div>

          <div class="card">
            <div class="row" style="justify-content:space-between;margin-bottom:10px">
              <h2>Bác sĩ</h2>
              <span class="small muted">Có thể để hệ thống phân bổ</span>
            </div>
            <div class="grid-2">
              <label class="doc-card ${!picked ? "on" : ""}" data-doc="">
                <div class="ava" style="background:#F4F4F5;color:#52525B">↻</div>
                <div><div style="font-weight:650">Để hệ thống phân bổ</div><div class="small muted">Phù hợp nhất với khung giờ bạn chọn</div></div>
              </label>
              ${doctors.filter((x) => x.isActive).map((x) => `
                <label class="doc-card ${picked === x.id ? "on" : ""}" data-doc="${esc(x.id)}">
                  <div class="ava">${esc(x.name.split(" ").slice(-1)[0][0] || "?")}</div>
                  <div class="grow"><div style="font-weight:650">${esc(x.name)} ${d.suggestedDoctorId === x.id ? '<span class="badge">Bác sĩ cũ</span>' : ""}</div>
                    <div class="small muted">${esc(x.title)}</div></div>
                </label>`).join("")}
            </div>
          </div>
        </section>

        <aside class="summary">
          <div class="card">
            <div class="card-title">Tóm tắt</div>
            <div data-summary></div>
            <hr class="sep">
            <button class="btn primary block" data-next>Chọn ngày giờ</button>
            <a class="btn block" href="#/k2" style="margin-top:8px">Quay lại</a>
          </div>
        </aside>
      </div>`;
  },
  mount(root) {
    const d = getDraft();
    const selected = new Set(d.serviceIds || []);
    let doctorId = d.doctorId || "";
    const services = Array.from(root.querySelectorAll("[data-svc]"));
    let lastPrice = 0;
    let lastDur = 0;

    function refresh() {
      const list = services.filter((n) => n.querySelector("input").checked);
      const totalDur = list.reduce((t, n) => t + Number((n.querySelector(".meta").textContent.match(/(\d+) phút/) || [0, 0])[1]), 0);
      const totalPrice = list.reduce((t, n) => {
        const txt = n.querySelector(".price").textContent.replace(/[^\d]/g, "");
        return t + Number(txt || 0);
      }, 0);
      lastPrice = totalPrice;
      lastDur = totalDur ? totalDur + 5 : 0;
      const names = list.map((n) => n.querySelector(".name").textContent);
      root.querySelector("[data-summary]").innerHTML = `
        <div class="line"><span>Dịch vụ đã chọn</span><b>${list.length}</b></div>
        ${names.map((n) => `<div class="line"><span class="small">${esc(n)}</span></div>`).join("") || '<div class="small muted" style="padding:6px 0">Chưa chọn dịch vụ nào</div>'}
        <div class="line"><span>Thời lượng dự kiến</span><b>${totalDur ? totalDur + 5 : 0} phút</b></div>
        <div class="line"><span class="small muted">Đã gồm 5 phút vệ sinh ghế (+15 phút nếu hồ sơ mới)</span></div>
        <div class="line total"><span>Tạm tính</span><span>${money(totalPrice)}</span></div>`;
      if (totalDur > 120) {
        root.querySelector("[data-summary]").insertAdjacentHTML("beforeend",
          `<div class="banner warn small" style="margin:10px 0 0">Tổng thời lượng trên 2 giờ — lễ tân sẽ liên hệ xác nhận.</div>`);
      }
    }

    services.forEach((n) => {
      n.querySelector("input").addEventListener("change", (e) => {
        n.classList.toggle("on", e.target.checked);
        refresh();
      });
    });

    root.querySelectorAll("[data-doc]").forEach((n) => {
      n.addEventListener("click", () => {
        doctorId = n.dataset.doc;
        root.querySelectorAll("[data-doc]").forEach((x) => x.classList.toggle("on", x === n));
      });
    });

    root.querySelectorAll("[data-group]").forEach((b) => {
      b.addEventListener("click", () => {
        root.querySelectorAll("[data-group]").forEach((x) => x.classList.toggle("on", x === b));
        const g = b.dataset.group;
        services.forEach((n) => { n.style.display = g === "Tất cả" || n.dataset.group === g ? "" : "none"; });
      });
    });

    root.querySelector("[data-next]").addEventListener("click", () => {
      const ids = services.filter((n) => n.querySelector("input").checked).map((n) => n.dataset.svc);
      if (!ids.length) { toast("Chọn ít nhất 1 dịch vụ.", "err"); return; }
      setDraft({ serviceIds: ids, doctorId: doctorId || null, priceHint: lastPrice, durationHint: lastDur });
      navigate("k4");
    });

    refresh();
    return null;
  },
};
