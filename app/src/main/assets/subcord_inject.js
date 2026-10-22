(function () {
  if (window.__SUBCORD_INSTALLED__) {
    try { window.__subcordBoot && window.__subcordBoot(); } catch (e) {}
    return;
  }
  window.__SUBCORD_INSTALLED__ = true;

  var PROCESSED = new WeakSet();
  var PENDING = Object.create(null);
  var STYLE_ID = "subcord-style-v3";
  var ATTR_ID = "data-subcord-id";
  var seq = 1;

  function isDarkTheme() {
    try {
      var bg = getComputedStyle(document.body || document.documentElement)
        .getPropertyValue("--background-primary")
        .trim();
      if (bg && bg.indexOf("rgb") === 0) {
        var m = bg.match(/(\d+)\s*,\s*(\d+)\s*,\s*(\d+)/);
        if (m) {
          var r = +m[1], g = +m[2], b = +m[3];
          // luminance
          return (0.2126 * r + 0.7152 * g + 0.0722 * b) < 140;
        }
      }
      // fallback: Discord html class / data
      var cl = (document.documentElement.className || "") + " " + (document.body && document.body.className || "");
      if (/theme-dark|darkTheme|dark/i.test(cl)) return true;
      if (/theme-light|lightTheme|light/i.test(cl)) return false;
    } catch (e) {}
    return true;
  }

  function ensureStyle() {
    var dark = isDarkTheme();
    var css = dark
      ? [
          ".subcord-tr{display:block;margin-top:5px;padding:7px 10px;border-radius:8px;",
          "background:#1e2230;border:1px solid #5865F2;border-left-width:4px;",
          "color:#f2f3f5 !important;font-size:14px;line-height:1.4;",
          "white-space:pre-wrap;word-break:break-word;max-width:100%;opacity:1 !important;}",
          ".subcord-tr b{color:#8ea1fc !important;opacity:1 !important;font-weight:700;",
          "margin-right:6px;font-size:12px;letter-spacing:.02em;}"
        ].join("")
      : [
          ".subcord-tr{display:block;margin-top:5px;padding:7px 10px;border-radius:8px;",
          "background:#eef0ff;border:1px solid #5865F2;border-left-width:4px;",
          "color:#1e1f22 !important;font-size:14px;line-height:1.4;",
          "white-space:pre-wrap;word-break:break-word;max-width:100%;opacity:1 !important;}",
          ".subcord-tr b{color:#3c45a5 !important;opacity:1 !important;font-weight:700;",
          "margin-right:6px;font-size:12px;letter-spacing:.02em;}"
        ].join("");

    var s = document.getElementById(STYLE_ID);
    if (!s) {
      s = document.createElement("style");
      s.id = STYLE_ID;
      (document.head || document.documentElement).appendChild(s);
    }
    if (s.getAttribute("data-theme") !== (dark ? "dark" : "light")) {
      s.setAttribute("data-theme", dark ? "dark" : "light");
      s.textContent = css;
    }
  }

  function looksEnglish(t) {
    if (!t || t.length < 2) return true;
    var letters = t.replace(/[^A-Za-z\u00C0-\u024F\u0400-\u04FF\u0600-\u06FF\u3040-\u30FF\u4E00-\u9FFF\uAC00-\uD7AF]/g, "");
    if (letters.length < 2) return true;
    if (/[\u0600-\u06FF\u0750-\u077F\u08A0-\u08FF]/.test(letters)) return false;
    if (/[^\u0000-\u024F]/.test(letters)) return false;
    var latin = (letters.match(/[A-Za-z]/g) || []).length;
    return latin / letters.length >= 0.85;
  }

  /** Strip Discord chrome that pollutes translation. */
  function cleanText(t) {
    if (!t) return "";
    t = t.replace(/\s+/g, " ").trim();
    // (edited) and localized variants
    t = t.replace(/^\(edited\)\s*/i, "");
    t = t.replace(/\s*\(edited\)\s*/gi, " ");
    // Full date lines Discord sometimes exposes: Thursday, October 22, 2026 at 2:58 PM
    t = t.replace(
      /\b(?:Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday),?\s+[A-Za-z]+\s+\d{1,2},?\s+\d{4}\s+at\s+\d{1,2}:\d{2}\s*(?:AM|PM)?/gi,
      " "
    );
    // Standalone times
    t = t.replace(/\b\d{1,2}:\d{2}\s*(?:AM|PM)\b/gi, " ");
    // NEW badge text
    t = t.replace(/\bNEW\b/g, " ");
    // Collapse whitespace again
    return t.replace(/\s+/g, " ").trim();
  }

  function textOf(el) {
    if (!el) return "";
    var clone = el.cloneNode(true);
    clone.querySelectorAll(
      ".subcord-tr, script, style, time, [class*='timestamp'], [class*='Timestamp'], [class*='edited'], [class*='Edited']"
    ).forEach(function (n) { n.remove(); });
    return cleanText(clone.innerText || clone.textContent || "");
  }

  function messageNodes(root) {
    root = root || document;
    var sels = [
      'div[class*="messageContent"]',
      'div[id^="message-content-"]',
      'div[class*="markup"]',
      'div[class*="messageContent-"]',
      '[data-list-item-id^="chat-messages"] div[class*="markup"]'
    ];
    var list = [];
    for (var i = 0; i < sels.length; i++) {
      try { root.querySelectorAll(sels[i]).forEach(function (n) { list.push(n); }); } catch (e) {}
    }
    // Prefer innermost messageContent over outer markup when both match
    return list.filter(function (n, idx) {
      if (list.indexOf(n) !== idx) return false;
      // skip if nested inside another matched message node
      for (var j = 0; j < list.length; j++) {
        if (list[j] !== n && list[j].contains(n)) return false;
      }
      return true;
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
    if (t.length < 2 || t.length > 900) return;
    if (window.__SUBCORD_SKIP_EN__ !== false && looksEnglish(t)) {
      PROCESSED.add(el);
      return;
    }
    if (!window.SubcordBridge || typeof window.SubcordBridge.translate !== "function") return;
    var id = "sc" + (seq++);
    el.setAttribute(ATTR_ID, id);
    PROCESSED.add(el);
    PENDING[id] = el;
    try { window.SubcordBridge.translate(id, t); }
    catch (e) { delete PENDING[id]; PROCESSED.delete(el); }
  }

  window.__subcordOnResult = function (id, translated) {
    try {
      var el = PENDING[id];
      delete PENDING[id];
      if (!el || !translated) return;
      ensureStyle();
      var slot = ensureSlot(el);
      var safe = String(translated)
        .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
      // Drop accidental date tails from bad MT input
      safe = safe.replace(
        /\s*(?:Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday),?\s+[A-Za-z]+\s+\d{1,2}.*/i,
        ""
      ).trim();
      slot.innerHTML = "<b>EN</b>" + safe;
      slot.style.display = "block";
    } catch (e) {}
  };

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
        if (m.type === "childList" && m.addedNodes) {
          m.addedNodes.forEach(function (n) {
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
      ensureStyle(); // theme may switch
      if (!obs) startObserver();
      scan(document);
    } catch (e) {}
  }, 3000);

  try {
    var push = history.pushState;
    history.pushState = function () {
      var r = push.apply(this, arguments);
      setTimeout(function () { scan(document); }, 350);
      return r;
    };
    window.addEventListener("popstate", function () {
      setTimeout(function () { scan(document); }, 350);
    });
  } catch (e) {}
})();
