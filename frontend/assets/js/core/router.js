/** Hash router tối giản: #/k1, #/l1?date=2026-11-19 */
export function parseHash() {
  const raw = (location.hash || "#/k1").replace(/^#\/?/, "");
  const [path, qs = ""] = raw.split("?");
  const id = (path || "k1").toLowerCase();
  return { id, params: new URLSearchParams(qs) };
}

export function navigate(path, params) {
  const qs = params ? "?" + (params instanceof URLSearchParams ? params.toString() : new URLSearchParams(params).toString()) : "";
  location.hash = "#/" + String(path).replace(/^#?\/?/, "") + qs;
}

export function replace(path, params) {
  const qs = params ? "?" + (params instanceof URLSearchParams ? params.toString() : new URLSearchParams(params).toString()) : "";
  history.replaceState(null, "", "#/" + String(path).replace(/^#?\/?/, "") + qs);
}

export function onChange(fn) {
  window.addEventListener("hashchange", fn);
}
