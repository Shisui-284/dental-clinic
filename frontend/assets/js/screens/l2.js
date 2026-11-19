/** L2 — Tạo lịch hộ / vãng lai / cấp cứu */
import { api } from "../api/index.js";
import { esc, toast, ticker } from "../core/ui.js";
import { fmtTime, fmtDate, money, sourceLabel } from "../core/format.js";
import { todayKey } from "../core/ui.js";

const SOURCES = ["RECEPTION", "SOCIAL", "WALK_IN", "EMERGENCY", "FOLLOW_UP"];

export default {
  id: "l2",
  title: "Tạo lịch hộ / vãng lai / cấp cứu",
  staff: true,
  async render() {
    const [services, doctors] = await Promise.all([api.services(), api.doctors()]);
    return `
      <div class="page-head">
        <div><h1>Tạo lịch tại quầy</h1>
          <div class="sub">Đặt hộ qua điện thoại/MXH, tiếp nhận khách vãng lai hoặc chèn lịch cấp cứu. Lễ tân được đặt hộ cả khách trong blacklist.</div></div>
        <div class="spacer"></div>
        <a class="btn sm" href="#/l1">← Về duyệt lịch</a>
      </div>

      <div class="grid-form">
        <section class="card">
          <div class="card-title">Thông tin khách</div>
          <label class="field"><span class="lbl">Số điện thoại <span class="req">*</span></span>
            <input type="tel" data-phone maxlength="10" placeholder="0912345678">
            <div data-phone-note></div>
          </label>
          <div class="grid-2">
            <label class="field"><span class="lbl">Họ tên người khám <span class="req">*</span></span>
              <input type="text" data-name maxlength="100" placeholder="Nguyễn Văn A"></label>
            <label class="field"><span class="lbl">Năm sinh <span class="req">*</span></span>
              <input type="number" data-year min="1900" max="${new Date().getFullYear()}" placeholder="1990"></label>
          </div>
          <label class="check" style="margin-bottom:14px"><input type="checkbox" data-new>
            <span>Hồ sơ mới (cộng thêm 15 phút tạo thông tin y tế)</span></label>

          <div class="card-title" style="margin-top:6px">Nguồn đặt</div>
          <div class="row" style="flex-wrap:wrap;gap:6px;margin-bottom:10px" data-sources>
            ${SOURCES.map((s, i) => `<button type="button" class="chip ${i === 0 ? "on" : ""}" data-source="${s}">${sourceLabel(s)}</button>`).join("")}
          </div>
          <div data-source-note class="help" style="margin-bottom:12px"></div>

          <div class="card-title">Dịch vụ <span class="req">*</span></div>
          <div class="stack" data-services style="max-height:240px;overflow:auto;padding-right:4px">
            ${services.map((s) => `<label class="svc" data-svc="${esc(s.id)}" data-dur="${s.duration}" data-price="${s.price}">
              <input type="checkbox"><div class="grow"><div class="name">${esc(s.name)}</div><div class="meta">${esc(s.group)} · ${s.duration} phút</div></div>
              <div class="price">${money(s.price)}</div></label>`).join("")}
          </div>

          <div data-temp-box class="hidden" style="margin-top:12px;padding:12px;border:1px dashed var(--teal);border-radius:10px;background:#F0FDFA">
            <label class="check" style="margin-bottom:8px"><input type="checkbox" data-use-temp>
              <span><b>Dịch vụ tạm thời</b> (khách vãng lai chưa rõ dịch vụ cụ thể)</span></label>
            <div class="grid-2 hidden" data-temp-fields>
              <label class="field" style="margin:0"><span class="lbl">Thời lượng dự kiến (phút)</span>
                <input type="number" data-temp-dur min="5" max="240" value="30"></label>
              <label class="field" style="margin:0"><span class="lbl">Mô tả dịch vụ tạm</span>
                <input type="text" data-temp-desc maxlength="200" placeholder="VD: Đau răng hàm, khám sơ bộ"></label>
            </div>
          </div>

          <div data-auto-prio-box class="hidden" style="margin-top:10px">
            <label class="check"><input type="checkbox" data-auto-prio checked>
              <span>Tự động chèn vào khung giờ trống gần nhất hôm nay (không cần chọn slot thủ công)</span></label>
          </div>

          <div class="grid-2" style="margin-top:14px">
            <label class="field"><span class="lbl">Bác sĩ</span>
              <select data-doctor><option value="">— Hệ thống phân bổ —</option>
                ${doctors.filter((d) => d.isActive).map((d) => `<option value="${esc(d.id)}">${esc(d.name)}</option>`).join("")}
              </select></label>
            <label class="field"><span class="lbl">Ghi chú</span><input type="text" data-note placeholder="VD: khách gọi lúc 9h, đau răng"></label>
          </div>
        </section>

        <section class="card">
          <div class="row" style="justify-content:space-between;margin-bottom:10px">
            <div class="card-title" style="margin:0">Chọn khung giờ</div>
            <input type="date" data-date value="${todayKey()}" style="width:160px;height:36px">
          </div>
          <div data-slots>${'<div class="skel" style="height:120px"></div>'}</div>
          <hr class="sep">
          <div class="row" style="align-items:flex-end">
            <div class="grow">
              <div class="small muted">Tóm tắt</div>
              <div data-summary style="font-weight:600">Chưa chọn dịch vụ</div>
            </div>
            <button class="btn primary" data-create disabled>Tạo lịch</button>
          </div>
          <div data-alert style="margin-top:12px"></div>
        </section>
      </div>`;
  },
  mount(root, ctx) {
    let source = "RECEPTION";
    let date = root.querySelector("[data-date]").value;
    let start = null;
    let stopTick = null;

    const svcBoxes = Array.from(root.querySelectorAll("[data-svc]"));
    const slotsBox = root.querySelector("[data-slots]");
    const createBtn = root.querySelector("[data-create]");
    const summary = root.querySelector("[data-summary]");
    const alertBox = root.querySelector("[data-alert]");
    const tempBox = root.querySelector("[data-temp-box]");
    const useTemp = root.querySelector("[data-use-temp]");
    const tempFields = root.querySelector("[data-temp-fields]");
    const tempDurInput = root.querySelector("[data-temp-dur]");
    const tempDescInput = root.querySelector("[data-temp-desc]");
    const autoPrioBox = root.querySelector("[data-auto-prio-box]");
    const autoPrioChk = root.querySelector("[data-auto-prio]");

    const selectedIds = () => svcBoxes.filter((n) => n.querySelector("input").checked).map((n) => n.dataset.svc);
    const hasTemp = () => source === "WALK_IN" && useTemp.checked;
    const isAutoSlot = () => (source === "EMERGENCY" && autoPrioChk.checked) || (source === "WALK_IN" && !start);
    const durationOf = () => {
      const official = svcBoxes.filter((n) => n.querySelector("input").checked)
        .reduce((t, n) => t + Number(n.dataset.dur), 0);
      const temp = hasTemp() ? (Number(tempDurInput.value) || 30) : 0;
      return official + temp + 5 + (root.querySelector("[data-new]").checked ? 15 : 0);
    };

    function paintSummary() {
      const ids = selectedIds();
      const hasAnySvc = ids.length > 0 || hasTemp();
      const dur = hasAnySvc ? durationOf() : 0;
      const price = svcBoxes.filter((n) => n.querySelector("input").checked).reduce((t, n) => t + Number(n.dataset.price), 0);
      const svcLabel = hasTemp()
        ? (ids.length ? `${ids.length} dịch vụ + tạm` : "Dịch vụ tạm")
        : `${ids.length} dịch vụ`;
      summary.textContent = hasAnySvc ? `${svcLabel} · ${dur} phút · ${money(price)}` : "Chưa chọn dịch vụ";
      const timeReady = Boolean(start) || isAutoSlot();
      createBtn.disabled = !(hasAnySvc && timeReady && root.querySelector("[data-name]").value.trim());
    }

    async function loadSlots() {
      const ids = selectedIds();
      alertBox.innerHTML = "";
      if (!ids.length) {
        slotsBox.innerHTML = hasTemp()
          ? `<div class="banner info">Khách vãng lai dùng <b>Dịch vụ tạm thời</b>: hệ thống tự tiếp nhận ngay tại thời điểm hiện tại (trạng thái <b>Đã đến</b>).</div>`
          : `<div class="empty"><b>Chọn dịch vụ</b>Khung giờ phụ thuộc vào tổng thời lượng dịch vụ.</div>`;
        start = null;
        paintSummary();
        return;
      }
      slotsBox.innerHTML = `<div class="skel" style="height:160px"></div>`;
      try {
        const phone = root.querySelector("[data-phone]").value.trim();
        const res = await api.slots({
          date,
          serviceIds: ids,
          doctorId: root.querySelector("[data-doctor]").value || undefined,
          phone,
          patientFullName: root.querySelector("[data-name]").value.trim(),
          birthYear: Number(root.querySelector("[data-year]").value) || undefined,
          isNewPatient: root.querySelector("[data-new]").checked,
          asStaff: true,
        });
        const morning = res.slots.filter((s) => new Date(s.start).getHours() < 12);
        const afternoon = res.slots.filter((s) => new Date(s.start).getHours() >= 12);
        if (!res.slots.some((s) => s.available)) {
          slotsBox.innerHTML = `<div class="empty"><b>Không còn khung trong ngày</b>Gợi ý chọn ngày khác.</div>`;
          start = null;
          paintSummary();
          return;
        }
        slotsBox.innerHTML = `
          <div class="row" style="justify-content:space-between"><span class="small muted">Sáng</span><span class="small muted">${res.duration} phút/lịch</span></div>
          <div class="slots" style="margin:8px 0 14px">${morning.map(slotBtn).join("")}</div>
          <div class="small muted">Chiều</div>
          <div class="slots" style="margin-top:8px">${afternoon.map(slotBtn).join("")}</div>`;
        slotsBox.querySelectorAll("[data-slot]").forEach((b) => {
          if (b.disabled) return;
          b.addEventListener("click", () => {
            start = b.dataset.slot;
            slotsBox.querySelectorAll("[data-slot]").forEach((x) => x.classList.toggle("on", x === b));
            paintSummary();
          });
        });
      } catch (ex) {
        slotsBox.innerHTML = `<div class="banner danger">${esc(ex.message)}</div>`;
      }
      paintSummary();
    }

    function slotBtn(s) {
      return `<button type="button" class="slot ${s.start === start ? "on" : ""}" data-slot="${s.start}" ${s.available ? "" : "disabled"}>${fmtTime(s.start)}</button>`;
    }

    root.querySelectorAll("[data-source]").forEach((b) => {
      b.addEventListener("click", () => {
        source = b.dataset.source;
        root.querySelectorAll("[data-source]").forEach((x) => x.classList.toggle("on", x === b));
        paintSourceNote();
      });
    });

    function paintSourceNote() {
      const note = root.querySelector("[data-source-note]");
      tempBox.classList.toggle("hidden", source !== "WALK_IN");
      autoPrioBox.classList.toggle("hidden", source !== "EMERGENCY");
      if (source !== "WALK_IN") {
        useTemp.checked = false;
        tempFields.classList.add("hidden");
      }
      if (source === "WALK_IN") note.innerHTML = `<div class="banner info" style="margin:0">Khách vãng lai: sau khi tạo, lịch vào thẳng trạng thái <b>Đã đến</b> (hỗ trợ chọn Dịch vụ tạm nếu chưa rõ).</div>`;
      else if (source === "SOCIAL") note.innerHTML = `<div class="help" style="margin:0">Đặt hộ từ Zalo / Facebook — lịch vào thẳng <b>Đã xác nhận</b> (bỏ qua Blacklist).</div>`;
      else if (source === "EMERGENCY") note.innerHTML = `<div class="banner danger" style="margin:0"><b>Cấp cứu:</b> tự tìm hoặc chèn vào khung còn trống gần nhất, không tự hủy/dời lịch đã xác nhận của khách khác.</div>`;
      else if (source === "FOLLOW_UP") note.innerHTML = `<div class="banner info" style="margin:0">Tái khám: tạo từ đề xuất của bác sĩ sau buổi khám trước.</div>`;
      else note.innerHTML = `<div class="help" style="margin:0">Lễ tân tạo hộ qua điện thoại — lịch vào thẳng trạng thái <b>Đã xác nhận</b> (bỏ qua Blacklist).</div>`;
      paintSummary();
    }
    paintSourceNote();

    useTemp.addEventListener("change", () => {
      tempFields.classList.toggle("hidden", !useTemp.checked);
      loadSlots();
    });
    tempDurInput.addEventListener("input", paintSummary);
    autoPrioChk.addEventListener("change", paintSummary);

    svcBoxes.forEach((n) => n.querySelector("input").addEventListener("change", (e) => {
      n.classList.toggle("on", e.target.checked);
      start = null;
      loadSlots();
    }));

    root.querySelector("[data-doctor]").addEventListener("change", () => { start = null; loadSlots(); });
    root.querySelector("[data-date]").addEventListener("change", (e) => { date = e.target.value; start = null; loadSlots(); });
    root.querySelector("[data-new]").addEventListener("change", () => { start = null; loadSlots(); });
    root.querySelector("[data-name]").addEventListener("input", paintSummary);

    const phoneInput = root.querySelector("[data-phone]");
    phoneInput.addEventListener("input", () => { phoneInput.value = phoneInput.value.replace(/\D/g, "").slice(0, 10); });
    phoneInput.addEventListener("blur", async () => {
      const p = phoneInput.value.trim();
      const box = root.querySelector("[data-phone-note]");
      box.innerHTML = "";
      if (!/^0\d{9}$/.test(p)) return;
      try {
        const r = await api.checkPhone(p);
        if (r.isBlacklisted) box.innerHTML = `<div class="banner warn" style="margin:8px 0 0">Khách đang bị khóa tự đặt lịch (blacklist) — lễ tân vẫn được tạo lịch hộ.</div>`;
        else if (r.hasProfile) box.innerHTML = `<div class="help">Đã có hồ sơ với số này${r.lastVisit ? ` · lần khám gần nhất ${fmtDate(r.lastVisit)}` : ""}.</div>`;
      } catch { /* ignore */ }
    });

    createBtn.addEventListener("click", async () => {
      alertBox.innerHTML = "";
      const phone = phoneInput.value.trim();
      const name = root.querySelector("[data-name]").value.trim();
      const year = Number(root.querySelector("[data-year]").value);
      const ids = selectedIds();
      const usingTemp = hasTemp();
      const errs = [];
      if (!/^0\d{9}$/.test(phone)) errs.push("SĐT phải gồm 10 chữ số.");
      if (!name) errs.push("Thiếu họ tên người khám.");
      if (!year) errs.push("Thiếu năm sinh.");
      if (!ids.length && !usingTemp) errs.push("Chọn ít nhất 1 dịch vụ hoặc bật Dịch vụ tạm thời.");
      if (!start && !isAutoSlot()) errs.push("Chưa chọn khung giờ.");
      if (errs.length) { alertBox.innerHTML = `<div class="banner danger">${errs.map(esc).join("<br>")}</div>`; return; }

      if (source === "EMERGENCY" || source === "WALK_IN") {
        const ok = window.confirm(source === "EMERGENCY"
          ? "Tạo lịch CẤP CỨU? Chỉ dùng khung còn trống, không hủy/dời lịch khác."
          : "Tiếp nhận khách VÃNG LAI và chuyển thẳng sang Đã đến?");
        if (!ok) return;
      }

      createBtn.disabled = true;
      createBtn.textContent = "Đang tạo...";
      try {
        const appt = await api.staffCreate({
          phone,
          patient: { fullName: name, birthYear: year, isNew: root.querySelector("[data-new]").checked },
          serviceIds: ids,
          ...(usingTemp
            ? {
                temporaryService: {
                  durationMinutes: Number(tempDurInput.value) || 30,
                  description: tempDescInput.value.trim() || "Khám vãng lai",
                },
              }
            : {}),
          doctorId: root.querySelector("[data-doctor]").value || undefined,
          start: (source === "EMERGENCY" && autoPrioChk.checked) ? undefined : (start || undefined),
          source,
          isPriority: source === "EMERGENCY",
          note: root.querySelector("[data-note]").value.trim(),
        });
        toast(`Đã tạo lịch ${appt.code} (${sourceLabel(appt.source)}).`, "ok");
        ctx.navigate("l3", { date: todayKey(new Date(appt.start)) });
      } catch (ex) {
        alertBox.innerHTML = `<div class="banner danger">${esc(ex.message)}</div>`;
        createBtn.disabled = false;
        createBtn.textContent = "Tạo lịch";
      }
    });

    loadSlots();
    return () => { if (stopTick) stopTick(); };
  },
};
