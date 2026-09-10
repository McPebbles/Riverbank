/*
 * Riverbank — app-install banner filter.
 *
 * This runs inside the page, at document start, injected by the app. It is a
 * one-way injection: the page gains no access to anything Android. There is
 * still no addJavascriptInterface bridge anywhere in this project.
 *
 * The three lists below are the tuning surface. Edit them and rebuild; nothing
 * here is fetched over the network.
 */
(function () {
  'use strict';
  if (window.__rbBannerFilter) return;
  window.__rbBannerFilter = true;

  /* Exact CSS selectors. Anything matching is hidden outright. These are the
   * brittle part — the storefront renames things — so the heuristics below do
   * most of the real work. */
  var SELECTORS = [
    '#nav-swmslot',
    '#navSwmSlot',
    '[data-cel-widget="nav-swmslot"]',
    '#mobile-app-banner',
    '#appLaunchBanner',
    '#app-install-banner',
    '.mobile-app-popover',
    '.smart-banner',
    '.app-banner'
  ];

  /* Substrings matched against an element's id and class names. */
  var NAME_HINTS = [
    'appbanner', 'app-banner', 'app_banner',
    'smartbanner', 'smart-banner',
    'appinstall', 'app-install', 'app_install',
    'openinapp', 'open-in-app',
    'getapp', 'get-app',
    'appdownload', 'app-download',
    'downloadapp', 'download-app',
    'mobile-app-popover', 'appstorebanner'
  ];

  /* Substrings matched against link hrefs. The link's nearest *small* ancestor
   * is hidden, which is what catches the banner around the button. */
  var APP_LINKS = [
    '/gp/mas/dl/android',
    '/gp/mas/get/amazonapp',
    '/gp/mas/dl/',
    'play.google.com/store/apps',
    'apps.apple.com/',
    'itunes.apple.com/',
    '/mobile-apps',
    '/mobileapp'
  ];

  /* URL schemes that hand off to a native app. Defused, never followed. */
  var APP_SCHEMES = [
    'intent:', 'market:', 'amzn:', 'android-app:',
    'com.amazon.mobile.shopping:', 'com.amazon.mobile.shopping.web:'
  ];

  var MAX_CLIMB = 4;            // levels we may walk up from a matched link
  var MAX_BANNER_HEIGHT = 220;  // px; never hide anything taller than this
  var MAX_LINKS_IN_CONTAINER = 6;

  var hidAnOverlay = false;

  function hide(el) {
    if (!el || el.nodeType !== 1) return;
    if (el === document.body || el === document.documentElement) return;
    try {
      if (el.dataset && el.dataset.rbHidden === '1') return;
      var cs = window.getComputedStyle(el);
      if (cs && cs.position === 'fixed' && el.offsetHeight > window.innerHeight * 0.6) {
        hidAnOverlay = true;
      }
      el.style.setProperty('display', 'none', 'important');
      if (el.dataset) el.dataset.rbHidden = '1';
    } catch (e) { /* detached or cross-origin; ignore */ }
  }

  /* Walk up from a matched link, stopping before we swallow real page content. */
  function containerFor(link) {
    var el = link, best = link;
    for (var i = 0; i < MAX_CLIMB; i++) {
      var parent = el.parentElement;
      if (!parent || parent === document.body) break;
      if (parent.offsetHeight > MAX_BANNER_HEIGHT) break;
      if (parent.querySelectorAll('a').length > MAX_LINKS_IN_CONTAINER) break;
      el = parent;
      best = parent;
    }
    return best;
  }

  function nameMatches(el) {
    var id = (el.id || '').toLowerCase();
    var cls = (typeof el.className === 'string' ? el.className : '').toLowerCase();
    if (!id && !cls) return false;
    var name = id + ' ' + cls;
    for (var i = 0; i < NAME_HINTS.length; i++) {
      if (name.indexOf(NAME_HINTS[i]) !== -1) return true;
    }
    return false;
  }

  function each(root, selector, fn) {
    var nodes;
    try {
      nodes = root.querySelectorAll(selector);
    } catch (e) {
      return;
    }
    for (var i = 0; i < nodes.length; i++) fn(nodes[i]);
  }

  function sweep(root) {
    if (!root || root.nodeType !== 1 && root.nodeType !== 9) return;
    if (!root.querySelectorAll) return;

    for (var i = 0; i < SELECTORS.length; i++) each(root, SELECTORS[i], hide);

    each(root, '[id],[class]', function (el) {
      if (nameMatches(el)) hide(el);
    });

    each(root, 'a[href]', function (a) {
      var href = (a.getAttribute('href') || '').toLowerCase();
      if (!href) return;
      var k;
      for (k = 0; k < APP_SCHEMES.length; k++) {
        if (href.indexOf(APP_SCHEMES[k]) === 0) {
          a.removeAttribute('href');
          hide(containerFor(a));
          return;
        }
      }
      for (k = 0; k < APP_LINKS.length; k++) {
        if (href.indexOf(APP_LINKS[k]) !== -1) {
          hide(containerFor(a));
          return;
        }
      }
    });

    /* A dismissed full-screen interstitial usually leaves the page scroll
     * locked behind it. Give it back. */
    if (hidAnOverlay) {
      hidAnOverlay = false;
      try {
        if (document.body && window.getComputedStyle(document.body).overflow === 'hidden') {
          document.body.style.setProperty('overflow', 'auto', 'important');
        }
      } catch (e) { /* ignore */ }
    }
  }

  /* Debounced full-document sweep. Note the explicit window receiver:
   * detaching requestAnimationFrame from window throws "Illegal invocation"
   * in Chromium. */
  var pending = false;
  function scheduleSweep() {
    if (pending) return;
    pending = true;
    var run = function () {
      pending = false;
      sweep(document);
    };
    if (window.requestAnimationFrame) {
      window.requestAnimationFrame(run);
    } else {
      window.setTimeout(run, 0);
    }
  }

  function start() {
    sweep(document);
    if (!window.MutationObserver || !document.documentElement) return;
    new MutationObserver(function (records) {
      for (var i = 0; i < records.length; i++) {
        var added = records[i].addedNodes;
        for (var j = 0; j < added.length; j++) {
          if (added[j].nodeType === 1) sweep(added[j]);
        }
      }
    }).observe(document.documentElement, { childList: true, subtree: true });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', scheduleSweep);
  }
  window.addEventListener('load', scheduleSweep);
  start();
})();
