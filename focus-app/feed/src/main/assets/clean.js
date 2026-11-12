/*
 * FocusFeed — скрипт, который выполняется на каждой странице instagram.com.
 *  - всегда открывает ленту «Подписки» (только те, на кого ты подписан);
 *  - не пускает в ленту Reels;
 *  - прячет рекламу, «Рекомендации для вас», Threads и кнопки Reels/«Создать».
 * CSS лежит в clean.css и подставляется сюда приложением как window.__FG_CSS.
 */
(function () {
  if (window.__focusFeed) return;
  window.__focusFeed = true;

  var FEED = '/?variant=following';

  // Надписи, по которым узнаём рекламу и рекомендации (английский и русский интерфейс).
  var AD_LABELS = ['Sponsored', 'Реклама', 'Спонсируемый контент', 'Спонсорский контент'];
  var SUGGESTED_LABELS = [
    'Suggested for you', 'Suggested for You', 'Suggested posts', 'Suggested reels',
    'Рекомендации для вас', 'Рекомендуемые публикации', 'Рекомендуемые', 'Рекомендуем вам',
    'Вам может понравиться',
  ];
  // Иконки в верхней/нижней панели, которые убираем.
  var HIDDEN_ICONS = ['Reels', 'New post', 'Create', 'Новая публикация', 'Создать', 'Threads'];

  // ---------------------------------------------------------------- маршруты

  function route() {
    var p = location.pathname;
    var r = 'other';
    if (p === '/' || p === '') r = 'home';
    else if (/^\/reels\/?$/.test(p)) r = 'reels';
    else if (p.indexOf('/explore') === 0) r = 'explore';
    else if (p.indexOf('/direct') === 0) r = 'direct';
    else if (p.indexOf('/stories') === 0) r = 'stories';
    else if (p.indexOf('/reel/') === 0 || p.indexOf('/reels/') === 0 || p.indexOf('/p/') === 0) r = 'post';
    document.documentElement.setAttribute('data-fg-route', r);

    if (r === 'reels') {
      location.replace(FEED);
    } else if (r === 'home' && location.search.indexOf('variant=following') === -1) {
      location.replace(FEED);
    }
  }

  ['pushState', 'replaceState'].forEach(function (m) {
    var original = history[m];
    history[m] = function () {
      var res = original.apply(this, arguments);
      setTimeout(route, 0);
      return res;
    };
  });
  window.addEventListener('popstate', function () { setTimeout(route, 0); });

  // ---------------------------------------------------------------- стили

  function addStyle() {
    if (document.getElementById('focus-feed-css')) return;
    var s = document.createElement('style');
    s.id = 'focus-feed-css';
    s.textContent = window.__FG_CSS || '';
    (document.head || document.documentElement).appendChild(s);
  }

  // ---------------------------------------------------------------- чистка

  function hide(el) {
    if (!el || el.hasAttribute('data-fg-hidden')) return;
    el.style.setProperty('display', 'none', 'important');
    el.setAttribute('data-fg-hidden', '1');
  }

  // Убираем пункт меню вместе с его обёрткой, чтобы остальные встали ровно.
  function hideNavItem(el) {
    var parent = el.parentElement;
    if (parent && parent.children.length === 1 &&
        parent.tagName !== 'NAV' && parent.getAttribute('role') !== 'navigation' &&
        parent !== document.body) {
      hide(parent);
    } else {
      hide(el);
    }
  }

  // Элемент находится в «шапке» или нижнем меню (они закреплены на экране)?
  function inFixedBar(el) {
    var cur = el;
    for (var i = 0; i < 12 && cur && cur !== document.body; i++) {
      var pos = getComputedStyle(cur).position;
      if (pos === 'fixed' || pos === 'sticky') return true;
      cur = cur.parentElement;
    }
    return false;
  }

  // Ближайший крупный блок вокруг надписи (карусель рекомендаций и т.п.).
  function blockAround(el) {
    var article = el.closest('article');
    if (article) return article;
    var cur = el;
    for (var i = 0; i < 8 && cur && cur !== document.body; i++) {
      if (cur.tagName === 'MAIN') return null;
      if (cur.getBoundingClientRect().height > 180) {
        // Не прячем целую ленту: если внутри несколько постов — это слишком большой блок.
        if (cur.querySelectorAll('article').length > 1 || cur.querySelector('main')) return null;
        return cur;
      }
      cur = cur.parentElement;
    }
    return null;
  }

  function clean() {
    addStyle();

    // 1. Иконки Reels / «Создать» / Threads в панелях
    document.querySelectorAll('svg[aria-label]').forEach(function (svg) {
      if (HIDDEN_ICONS.indexOf(svg.getAttribute('aria-label')) === -1) return;
      var btn = svg.closest('a,[role="button"],[role="link"]');
      if (!btn || btn.hasAttribute('data-fg-hidden')) return;
      if (inFixedBar(btn)) hideNavItem(btn);
    });
    document.querySelectorAll('a[href="/reels/"]').forEach(function (a) {
      if (inFixedBar(a)) hideNavItem(a);
    });

    // 2. Посты с рекламой Threads
    document.querySelectorAll('article a[href*="threads.net"], article a[href*="threads.com"]')
      .forEach(function (a) { hide(a.closest('article')); });

    // 3. Реклама и рекомендации — по надписям
    var root = document.querySelector('main') || document.body;
    if (!root) return;
    var walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
    var node;
    var found = [];
    while ((node = walker.nextNode())) {
      var t = node.nodeValue.trim();
      if (!t || t.length > 40) continue;
      if (AD_LABELS.indexOf(t) !== -1 || SUGGESTED_LABELS.indexOf(t) !== -1) {
        found.push(node.parentElement);
      }
    }
    found.forEach(function (el) {
      if (!el) return;
      var block = blockAround(el);
      if (block) hide(block);
    });
  }

  var scheduled = false;
  function schedule() {
    if (scheduled) return;
    scheduled = true;
    setTimeout(function () {
      scheduled = false;
      try { clean(); } catch (e) { /* не ломаем страницу */ }
    }, 250);
  }

  route();
  addStyle();
  new MutationObserver(schedule).observe(document.documentElement, { childList: true, subtree: true });
  document.addEventListener('DOMContentLoaded', schedule);
  schedule();
})();
