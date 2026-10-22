(function () {
  if (window.__SUBCORD_INSTALLED__) {
    try { window.__subcordBoot && window.__subcordBoot(); } catch (e) {}
    return;
  }
  window.__SUBCORD_INSTALLED__ = true;

  var PROCESSED = new WeakSet();
  var PENDING = Object.create(null);
  var STYLE_ID = "subcord-style-v1";
  var ATTR_ID = "data-subcord-id";
  var seq = 1;

  function ensureStyle() {
    if (document.getElementById(STYLE_ID)) return;
    var s = document.createElement("style");
    s.id = STYLE_ID;
    s.textContent = [
      ".subcord-tr{display:block;margin-top:4px;padding:6px 8px;border-radius:8px;",
      "background:rgba(88,101,242,.14);border-left:3px solid #5865F2;",
      "color:var(--text-normal,#dbdee1);font-size:.92em;line-height:1.35;",
      "white-space:pre-wrap;word-break:break-word;}",
      ".subcord-tr b{opacity:.55;font-weight:600;margin-right:6px;font-size:.85em;}"
    ].join("");
    (document.head || document.documentElement).appendChild(s);
  }

  function looksEnglish(t) {
    if (!t || t.length < 2) return true;
    var letters = t.replace(/[^A-Za-z\u00C0-\u024F\u0400-\u04FF\u0600-\u06FF\u3040-\u30FF\u4E00-\u9FFF\uAC00-\uD7AF]/g, "");
    if (letters.length < 2) return true;
    var nonLatin = (letters.match(/[^\u0000-\u024F]/g) || []).length;
    if (nonLatin > 0) return false;
    var latin = (letters.match(/[A-Za-z]/g) || []).length;
    return latin / letters.length >= 0.85;
  }

  function textOf(el) {
    if (!el) return "";
    var clone = el.cloneNode(true);
    clone.querySelectorAll(".subcord-tr, script, style").forEach(function (n) { n.remove(); });
    return (clone.innerText || clone.textContent || "").replace(/\s+/g, " ").trim();
  }

  function messageNodes(root) {
    root = root || document;
    var list = [];
    var sels = [
      'div[class*="messageContent"]',
      'div[id^="message-content-"]',
      'div[class*="markup"]'
    ];
    for (var i = 0; i < sels.length; i++) {
      try {
        root.querySelectorAll(sels[i]).forEach(function (n) { list.push(n); });
      } catch (e) {}
    }
    return Array.prototype.filter.call(list, function (n, idx) {
      return list.indexOf(n) === idx;
    });
  }

  function ensureSlot(el) {
    var next = el.nextElementSibling;
    if (next && next.classList && next.classList.contains("subcord-tr")) return next;
    var slot = document.createElement("div");
    slot.className = "subcord-tr";
    slot.style.display = "none";
    if (el.parentNode) el.parentNode.insertBefore(slot, el.nextSibling);
    else el.appendChild(slot);
    return slot;
  }

  function requestTranslate(el) {
    if (!el || PROCESSED.has(el)) return;
    if (el.closest && el.closest(".subcord-tr")) return;
    var t = textOf(el);
    if (t.length < 2 || t.length > 1200) return;
    if (window.__SUBCORD_SKIP_EN__ !== false && looksEnglish(t)) {
      PROCESSED.add(el);
      return;
    }
    if (!window.SubcordBridge || typeof window.SubcordBridge.translate !== "function") return;

    var id = "sc" + (seq++);
    el.setAttribute(ATTR_ID, id);
    PROCESSED.add(el);
    PENDING[id] = el;
    try {
      window.SubcordBridge.translate(id, t);
    } catch (e) {
      delete PENDING[id];
      PROCESSED.delete(el);
    }
  }

  window.__subcordOnResult = function (id, translated) {
    try {
      var el = PENDING[id];
      delete PENDING[id];
      if (!el || !translated) return;
      ensureStyle();
      var slot = ensureSlot(el);
      slot.innerHTML = "<b>EN</b>" + escapeHtml(String(translated));
      slot.style.display = "block";
    } catch (e) {}
  };

  function escapeHtml(s) {
    return s.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
  }

  function scan(root) {
    ensureStyle();
    messageNodes(root).forEach(requestTranslate);
  }

  var obs = null;
  function startObserver() {
    if (obs) return;
    var target = document.body || document.documentElement;
    if (!target) return;
    obs = new MutationObserver(function (mutations) {
      for (var i = 0; i < mutations.length; i++) {
        var m = mutations[i];
        if (m.type === "childList") {
          m.addedNodes && m.addedNodes.forEach(function (n) {
            if (n.nodeType !== 1) return;
            if (n.classList && n.classList.contains("subcord-tr")) return;
            scan(n);
          });
        }
      }
    });
    obs.observe(target, { childList: true, subtree: true });
  }

  window.__subcordBoot = function () {
    ensureStyle();
    startObserver();
    scan(document);
  };

  window.__subcordBoot();
  setInterval(function () {
    try {
      if (!document.getElementById(STYLE_ID)) ensureStyle();
      if (!obs) startObserver();
      scan(document);
    } catch (e) {}
  }, 2500);

  try {
    var push = history.pushState;
    history.pushState = function () {
      var r = push.apply(this, arguments);
      setTimeout(function () { scan(document); }, 400);
      return r;
    };
    window.addEventListener("popstate", function () {
      setTimeout(function () { scan(document); }, 400);
    });
  } catch (e) {}
})();
