/** K2 — Chọn người khám (bản thân / người thân) */
import { getDraft, setDraft } from "../core/session.js";
import { navigate } from "../core/router.js";
import { esc } from "../core/ui.js";

export default {
  id: "k2",
  title: "Chọn người khám",
  step: 2,
  async render() {
    const d = getDraft();
    const p = d.patient || {};
    const self = !p.relation;
    const year = new Date().getFullYear();
    return `
      <div class="cust-grid">
        <section class="card pad-lg">
          <div class="row" style="justify-content:space-between">
            <h2>Ai sẽ đi khám?</h2>
            <a class="small" href="#/k1">← Đổi số điện thoại</a>
          </div>
          <p class="muted small">Hồ sơ sẽ được đối chiếu sau khi xác thực OTP. Chưa hiển thị thông tin hồ sơ trước OTP.</p>

          <div class="grid-2" style="margin:16px 0 18px">
            <label class="doc-card ${self ? "on" : ""}" data-pick="self">
              <input type="radio" name="who" value="self" ${self ? "checked" : ""} style="accent-color:var(--teal)">
              <div><div style="font-weight:650">Đặt cho bản thân</div><div class="small muted">Dùng số điện thoại đang đặt lịch</div></div>
            </label>
            <label class="doc-card ${!self ? "on" : ""}" data-pick="other">
              <input type="radio" name="who" value="other" ${!self ? "checked" : ""} style="accent-color:var(--teal)">
              <div><div style="font-weight:650">Đặt cho người thân</div><div class="small muted">Con, bố/mẹ hoặc người giám hộ</div></div>
            </label>
          </div>

          <form data-form>
            <div class="grid-2">
              <label class="field"><span class="lbl">Họ và tên người khám <span class="req">*</span></span>
                <input type="text" name="fullName" maxlength="100" value="${esc(p.fullName || "")}" placeholder="VD: Nguyễn Văn A">
              </label>
              <label class="field"><span class="lbl">Năm sinh <span class="req">*</span></span>
                <input type="number" name="birthYear" min="1900" max="${year}" value="${esc(p.birthYear || "")}" placeholder="1995">
                <div class="help">Dùng để đối chiếu hồ sơ, tránh trùng bệnh nhân.</div>
              </label>
            </div>
            <div data-relation class="${self ? "hidden" : ""}">
              <div class="grid-2">
                <label class="field"><span class="lbl">Quan hệ với chủ tài khoản</span>
                  <select name="relation">
                    ${["Con", "Bố/Mẹ", "Vợ/Chồng", "Người giám hộ", "Khác"].map((r) => `<option ${p.relation === r ? "selected" : ""}>${r}</option>`).join("")}
                  </select>
                </label>
                <label class="check" style="margin-top:26px"><input type="checkbox" name="isGuardian" ${p.isGuardian ? "checked" : ""}>
                  <span>Tôi là người giám hộ hợp pháp của người khám</span></label>
              </div>
            </div>
            <label class="field"><span class="lbl">Ghi chú cho lễ tân (không bắt buộc)</span>
              <textarea name="note" maxlength="500" placeholder="VD: Bé sợ nha, cần bác sĩ nhẹ nhàng...">${esc(p.note || "")}</textarea>
            </label>
            <div data-err></div>
            <div class="row" style="margin-top:6px">
              <a class="btn" href="#/k1">Quay lại</a>
              <div class="spacer"></div>
              <button class="btn primary" type="submit">Tiếp tục chọn dịch vụ</button>
            </div>
          </form>
        </section>

        <aside class="stack">
          <div class="card">
            <div class="card-title">Thông tin tối thiểu</div>
            <p class="small muted">Chúng tôi chỉ hỏi họ tên và năm sinh ở bước này. Các thông tin khác (giới tính, địa chỉ, tiền sử) lễ tân/bác sĩ sẽ bổ sung khi bạn đến khám.</p>
          </div>
          <div class="card">
            <div class="card-title">Đặt cho người thân</div>
            <p class="small muted">Mỗi hồ sơ cần đúng họ tên và năm sinh. Nếu trẻ dưới 18 tuổi, cần xác nhận người giám hộ.</p>
          </div>
        </aside>
      </div>`;
  },
  mount(root) {
    const form = root.querySelector("[data-form]");
    const rel = root.querySelector("[data-relation]");
    const errBox = root.querySelector("[data-err]");
    const cards = root.querySelectorAll("[data-pick]");

    root.querySelectorAll('input[name="who"]').forEach((r) => {
      r.addEventListener("change", () => {
        const isSelf = form.querySelector('input[name="who"]:checked').value === "self";
        rel.classList.toggle("hidden", isSelf);
        cards.forEach((c) => c.classList.toggle("on", c.dataset.pick === (isSelf ? "self" : "other")));
      });
    });

    form.addEventListener("submit", (e) => {
      e.preventDefault();
      errBox.innerHTML = "";
      const fd = new FormData(form);
      const fullName = String(fd.get("fullName") || "").trim();
      const birthYear = Number(fd.get("birthYear"));
      const who = fd.get("who");
      const year = new Date().getFullYear();
      const errs = [];
      if (fullName.length < 2) errs.push("Họ tên phải có ít nhất 2 ký tự.");
      if (!birthYear || birthYear < 1900 || birthYear > year) errs.push("Năm sinh không hợp lệ.");
      if (errs.length) {
        errBox.innerHTML = `<div class="banner danger">${errs.map(esc).join("<br>")}</div>`;
        return;
      }
      setDraft({
        patient: {
          fullName,
          birthYear,
          relation: who === "self" ? null : String(fd.get("relation") || "Khác"),
          isGuardian: !!fd.get("isGuardian"),
          note: String(fd.get("note") || "").trim(),
        },
      });
      navigate("k3");
    });
    return null;
  },
};
