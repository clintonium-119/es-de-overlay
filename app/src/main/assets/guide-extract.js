// Finds the guide on the page the touch browser shows, for saving it. BrowserOverlay injects this, after
// Mozilla's Readability, after each page load and reads it back through evaluateJavascript: there is no bridge.
// Selectors were read from live GameFAQs, Neoseeker and ZeldaDungeon guide pages on 2026-10-30, and break when a
// site changes its layout. Any other page goes to Readability, Firefox's Reader View.
(() => {
  if (window.esdeGuide) return;
  // Long strings go back to the app in pieces of this many characters.
  const CHUNK = 256 * 1024;
  const DROP = 'script,style,link,meta,iframe,frame,object,embed,form,input,button,select,textarea,noscript,ins,video,audio,canvas,svg';
  // Ad, share and comment blocks, by a word in their id or class: "adthrive-ad", "neoseeker-primis-inline", "disqus_thread".
  const JUNK = /(^|[\s_-])(ad|ads|adthrive|advert\w*|primis\w*|prms\w*|sponsor\w*|share\w*|social\w*|disqus\w*|comments?|readmore)(?=[\s_-]|$)/i;
  const KEEP = new Set(['href', 'src', 'alt', 'id', 'name', 'colspan', 'rowspan', 'title']);
  // A link to a YouTube video stays a link: the viewer plays it over the guide.
  const YOUTUBE = /^https?:\/\/((www|m)\.)?(youtube\.com\/(watch\?|shorts\/|embed\/|live\/)|youtu\.be\/)/;
  // Whether an element is in the page's own parts rather than its content, as a screen reader's landmarks have it:
  // navigation anywhere; a header, footer or sidebar outside any article, section or main content. (ZeldaDungeon
  // keeps each chapter's enemy list, hearts and all, in an <aside> inside the chapter's <section>.)
  const inSitePart = e => {
    const part = e.closest('nav,header,footer,aside');
    return !!part && (part.matches('nav') || !part.closest('article,main,section'));
  };

  const $ = (s, root = document) => root.querySelector(s);
  const $$ = (s, root = document) => [...root.querySelectorAll(s)];
  const squash = s => (s || '').replace(/\s+/g, ' ').trim();
  const text = e => (e ? squash(e.innerText) : '');
  const bare = href => { const u = new URL(href, location.href); u.hash = ''; return u.href; };
  const here = () => ({ url: bare(location.href), title: document.title });
  // First title for each URL, in order.
  const distinct = pages => pages.filter((p, i) => pages.findIndex(q => q.url === p.url) === i);
  // The biggest picture a srcset lists ("a.jpg 1x, b.jpg 2x" or "a.jpg 295w, b.jpg 590w"), null for none. A URL
  // runs to the next space, so it can hold a comma; a candidate with no descriptor is 1x.
  const largest = srcset => {
    let best = null, size = 0;
    for (const [, url, d] of (srcset || '').matchAll(/[\s,]*(\S*[^\s,])([^,]*)/g)) {
      const n = parseFloat((/([\d.]+)[wx]/.exec(d) || [0, 1])[1]);
      if (n > size) { best = url; size = n; }
    }
    return best;
  };
  // GameFAQs splits a long text guide into <pre> blocks at line ends, around ads, and drops the newline there.
  const preText = (box) => { const pres = $$('pre', box); return pres.length ? pres.map(p => p.textContent).join('\n') : box.textContent; };

  // Each site's guide on this page: kind, title, author and pages ({url, title} in guide order), plus a text()
  // for a text guide or the content element (and any page-list element to leave out) for an HTML one.
  function gamefaqs() {
    const author = text($('h2.title a.contrib1'));
    // A guide split into named pages shows the page's name in h1 and "Game —Guide (Platform)" below it.
    const platform = text($('h3.platform-title'));
    const title = platform.includes('—') ? platform.replace(/\s*\([^)]*\)$/, '').replace(/\s*—\s*/, ' – ') : text($('h1.page-title'));
    const faq = $('#faqtext');
    if (faq) return { kind: 'text', title, author, pages: [here()], text: () => preText(faq) };
    const content = $('#faqwrap');
    if (!content) return null;
    const toc = $$('#faq_toc .toc_menu a[href]').map(a => ({ url: bare(a.href), title: text(a) }));
    const pager = $$('.paginate a[href*="page="]').map(a => +new URL(a.href).searchParams.get('page'));
    let pages;
    if (pager.length || new URL(location.href).searchParams.has('page')) {
      // Numbered pages (?page=0 to the pager's last); the contents list skips some of them.
      const last = Math.max(+(new URL(location.href).searchParams.get('page') || 0), ...pager);
      pages = [...Array(last + 1).keys()].map(i => {
        const u = new URL(location.href);
        u.hash = '';
        u.search = '?page=' + i;
        return { url: u.href, title: (toc.find(p => p.url === u.href) || { title: 'Page ' + (i + 1) }).title };
      });
    } else {
      pages = toc.length ? distinct(toc) : [here()];
    }
    return { kind: 'html', title, author, pages, content, drop: '.ftoc,.faq_bookmark_jumper,.paginate' };
  }

  function neoseeker() {
    const faq = $('#faqtxt');
    if (faq) {
      const title = text($('h1'));
      const author = text($('.author_area .author')).replace(/^by\s+/i, '');
      return faq.classList.contains('text_only')
        ? { kind: 'text', title, author, pages: [here()], text: () => preText(faq) }
        : { kind: 'html', title, author, pages: [here()], content: faq };
    }
    const content = $('#wiki-content .mw-parser-output');
    if (!content) return null;
    // Wiki walkthroughs: "Area 2 Walkthrough - Metroid: Samus Returns - Neoseeker", or the front page's h1.
    const parts = document.title.split(' - ');
    const game = parts.length >= 3 ? parts[parts.length - 2] : text($('h1')).replace(/\s+Walkthrough.*$/i, '');
    const root = '/' + location.pathname.split('/')[1] + '/walkthrough';
    const inGuide = a => { const p = new URL(a.href).pathname; return p === root || p.startsWith(root + '/'); };
    // Chapter pages carry the whole list in .wiki-toc; the front page only links its chapters.
    const links = $$('.wiki-toc a[href]').filter(inGuide);
    const listed = (links.length ? links : $$('a[href]', content).filter(inGuide)).map(a => ({ url: bare(a.href), title: text(a) }));
    const pages = distinct([{ url: location.origin + root, title: 'Introduction' }, ...listed]);
    return { kind: 'html', title: game + ' Walkthrough', author: '', pages, content };
  }

  function zeldadungeon() {
    const content = $('.content-wrap');
    if (!content) return null;
    // "Link's Awakening Walkthrough – Key Cavern – Game Boy Color": the guide, and its version if named.
    const parts = text($('h1')).split(/\s+–\s+/);
    const title = parts[0] + (parts.length > 2 ? ' – ' + parts[parts.length - 1] : '');
    const root = '/' + location.pathname.split('/')[1] + '/';
    // The chapter list: the <ul> with the most chapter links. The current chapter is a <strong>, not a link.
    const chapters = ul => [...ul.children].map(li => {
      const a = $(':scope > a[href]', li);
      if (a && new URL(a.href).pathname.startsWith(root) && new URL(a.href).pathname !== root) return { url: bare(a.href), title: text(a) };
      const strong = $(':scope > strong', li);
      return strong ? { url: bare(location.href), title: text(strong) } : null;
    }).filter(Boolean);
    const lists = $$('ul', content).map(ul => ({ ul, pages: chapters(ul) }));
    const pages = lists.reduce((best, l) => (l.pages.length > best.length ? l.pages : best), []);
    // The list shows above and below each chapter; the saved guide has its own contents.
    const copies = pages.length > 1 ? lists.filter(l => l.pages.length === pages.length).map(l => l.ul) : [];
    return { kind: 'html', title, author: '', pages: pages.length ? distinct(pages) : [here()], content, lists: copies };
  }

  // A listed page that looks like the site's own rather than the guide's starts unticked: a "+66 more" link, or an
  // address that is just a contributors, edit, history, talk, log-in or search page.
  const SITE_TEXT = /^(\+?\d+\s+more|more|edit|history|talk|discussion|comments?|log ?in|sign ?(in|up)|share|print)$/i;
  const SITE_PAGE = /^((top-?)?contributors?|edit|history|talk|discussion|comments|login|signin|signup|register|account|profile|search|tags?|categor(y|ies)|share|print|feed|rss)$/i;
  const siteOwn = (u, title) => SITE_TEXT.test(title) || SITE_PAGE.test(u.pathname.split('/').pop().replace(/\.\w+$/, '')) ||
    /^(edit|history)$/i.test(u.searchParams.get('action') || '');

  // A guide's front page can be little more than its contents, which Reader View's test turns down (IGN's
  // "/wikis/super-mario-world" has one paragraph and links 26 pages below it): a page two or more folders deep that
  // links at least ten pages below its own path counts as a guide too. A site's section tabs are fewer (IGN's
  // "/games/super-mario-world" links 8: reviews, videos and the like).
  // ponytail: a count, not a reading of the page; a guide with under ten pages needs Reader View's test to pass.
  function guideFront() {
    const path = location.pathname.replace(/\/$/, '');
    if (path.split('/').length <= 2) return false;
    const below = new Set();
    for (const a of $$('a[href]')) {
      try {
        const u = new URL(a.getAttribute('href'), location.href);
        if (u.origin === location.origin && u.pathname.startsWith(path + '/')) below.add(u.pathname);
      } catch (e) { /* not a URL */ }
    }
    return below.size >= 10;
  }

  // Any other page, when Reader View would offer it or it's a guide's front page: its main content, and the guide's
  // other pages from its links.
  // Pages numbered ?page=N anywhere on the page; else links anywhere below this page's path, or in the content
  // beside it in a folder two or more deep ("/wikis/shantae/", not a flat "/wiki/"); also a rel="next" or "Next" link as
  // [next], which the save can follow page by page. Parsed once per URL. A page picked to save is read even
  // when Reader View wouldn't offer it ([force]): guide pages that are mostly lists or tables fail its test.
  let parsed = null;
  function generic(force) {
    if (!parsed || parsed.url !== location.href || (force && !parsed.guide)) parsed = { url: location.href, guide: readable(force) };
    return parsed.guide;
  }

  // Sites off the guide list whose guide block is known: it is saved as it stands, in place of Reader View's pick.
  // IGN's wiki pages are short on prose, so Reader View takes the whole page, with "Was this guide helpful?" and the
  // footer; seen 2026-11-11.
  const BLOCKS = { 'www.ign.com': '.wiki-html' };

  function readable(force) {
    if (!/^https?:$/.test(location.protocol) || !(force || isProbablyReaderable(document) || guideFront())) return null;
    const page = document.cloneNode(true);
    // Reader View drops what a page hides from screen readers, and some pages mark their pictures so (IGN's wiki
    // does): a picture the page shows at picture size counts as shown. The copy's images are in the page's order.
    const shown = $$('img');
    $$('img', page).forEach((img, i) => {
      const r = shown[i] && shown[i].getBoundingClientRect();
      if (r && r.width >= 100 && r.height >= 60) img.removeAttribute('aria-hidden');
    });
    const article = new Readability(page).parse();
    if (!article || !article.content) return null;
    const block = $(BLOCKS[location.host] || ':not(*)');
    // Parsed inert: no images load and no handlers run until the content is cleaned.
    let content = block || new DOMParser().parseFromString(article.content, 'text/html').body;
    // Reader View weighs text, so on a page of maps with captions it can pick a footer instead. When its pick has
    // little text and none of the page's big pictures, the guide is the smallest block holding all of them.
    const big = $$('img').filter(i => {
      const r = i.getBoundingClientRect();
      return r.width >= 200 && r.height >= 100 && !i.closest('header,nav,aside,footer');
    });
    const kept = new Set($$('img', content).map(i => i.getAttribute('src')));
    if (!block && article.length < 500 && big.length >= 2 && !big.some(i => kept.has(i.getAttribute('src')))) {
      let around = big[0].parentElement;
      while (around && !big.every(i => around.contains(i))) around = around.parentElement;
      if (around && around !== document.body && around !== document.documentElement) content = around;
    }
    const title = squash(article.title || document.title);
    const link = href => { try { return new URL(href, location.href); } catch (e) { return null; } };
    const urls = (root) => $$('a[href]', root).map(a => ({ u: link(a.getAttribute('href')), title: text(a) }))
      .filter(l => l.u && l.u.origin === location.origin && !/\.(png|jpe?g|gif|webp|svg|pdf|zip|mp4)$/i.test(l.u.pathname));
    const self = new URL(location.href);
    let pages = [];
    const numbers = urls(document).filter(l => l.u.pathname === self.pathname && /^\d+$/.test(l.u.searchParams.get('page') || ''))
      .map(l => +l.u.searchParams.get('page'));
    if (numbers.length) {
      const at = self.searchParams.get('page');
      if (at === null) pages.push(here());
      for (let n = Math.min(...numbers, at === null ? Infinity : +at); n <= Math.max(...numbers, +at); n++) {
        const u = new URL(location.href);
        u.hash = '';
        u.searchParams.set('page', n);
        pages.push({ url: u.href, title: 'Page ' + n });
      }
    } else {
      const others = root => urls(root).filter(l => bare(l.u.href) !== bare(location.href));
      const inContent = others(content);
      const path = self.pathname.replace(/\/$/, '');
      const dir = path.replace(/[^/]*$/, '');
      // Pages below this one are its own, wherever the page links them: a contents sidebar lists all of a guide's.
      let listed = [...inContent, ...others(document)].filter(l => l.u.pathname.startsWith(path + '/'));
      if (!listed.length && dir.split('/').length > 3) listed = inContent.filter(l => l.u.pathname.startsWith(dir));
      pages = distinct([here(), ...listed.map(l => ({ url: bare(l.u.href), title: l.title || l.u.pathname, skip: siteOwn(l.u, l.title) }))]);
    }
    const nextLink = $('link[rel~="next"][href], a[rel~="next"][href]') ||
      $$('a[href]').find(a => /^next( page)?\s*[»›>→]?$/i.test(text(a)));
    const next = nextLink && link(nextLink.getAttribute('href'));
    return {
      kind: 'html', generic: true, title, author: squash(article.byline), pages, content,
      next: next && next.origin === location.origin && bare(next.href) !== bare(location.href) ? bare(next.href) : null,
    };
  }

  const SITES = { 'gamefaqs.gamespot.com': gamefaqs, 'www.neoseeker.com': neoseeker, 'www.zeldadungeon.net': zeldadungeon };
  const guide = (force) => (SITES[location.host] || generic)(force);

  let out = '';
  let images = [];
  const results = {};
  // The addresses check() found the site doesn't have; null while it runs.
  let gone = null;

  window.esdeGuide = {
    describe(force) {
      const g = guide(force);
      return g ? { kind: g.kind, title: g.title, author: g.author, pages: g.pages, generic: !!g.generic, next: g.next || null } : { kind: 'none' };
    },

    /** Keeps the text guide's text for part(); returns its length. */
    text() {
      out = guide().text();
      return out.length;
    },

    /**
     * Keeps the guide's cleaned HTML for part(): no scripts, frames, forms, ads or share blocks; only plain
     * attributes; links to the guide's [pages] become #sK, links to YouTube videos stay, other links become their
     * text; the site's own images (logos, icons) go; each image src img:N, for its srcset's biggest picture if it
     * lists any. Returns its length and the number of images.
     */
    html(pages) {
      const g = guide();
      const c = g.content.cloneNode(true);
      const uls = $$('ul', g.content);
      const cloned = $$('ul', c);
      (g.lists || []).forEach(ul => cloned[uls.indexOf(ul)].remove());
      $$(DROP + (g.drop ? ',' + g.drop : ''), c).forEach(e => e.remove());
      $$('*', c).filter(e => JUNK.test(e.id + ' ' + (e.getAttribute('class') || ''))).forEach(e => e.remove());
      const index = new Map(pages.map((p, i) => [p.url, i]));
      for (const a of $$('a', c)) {
        const href = a.getAttribute('href') || '';
        if (href.startsWith('#')) continue;
        if (href && YOUTUBE.test(a.href)) {
          a.setAttribute('href', a.href);
          // IGN's wikis show their old <youtube> tag as text around the bare URL.
          for (const t of [a.previousSibling, a.nextSibling]) if (t && t.nodeType === 3 && /^\s*<\/?youtube>\s*$/.test(t.data)) t.remove();
          if (/^https?:/.test(a.textContent.trim())) a.textContent = 'YouTube video';
          continue;
        }
        const i = href ? index.get(bare(a.href)) : undefined;
        if (i === undefined) a.replaceWith(...a.childNodes);
        else a.setAttribute('href', '#s' + (i + 1));
      }
      // A site's own image: every copy of it on the page sits in the page's own parts. Reader View's pick can hold
      // those too (IGN's whole page, with the logos in its <nav>).
      // ponytail: by where the page puts it, so a logo in a plain <div> stays. Compare the guide's pages for images
      // they all share if one turns up.
      const siteImage = new Map();
      for (const i of $$('img')) {
        for (const u of new Set([i.src, i.currentSrc])) if (u) siteImage.set(u, siteImage.get(u) !== false && inSitePart(i));
      }
      images = [];
      for (const img of $$('img', c)) {
        if (siteImage.get(img.src)) {
          img.remove();
          continue;
        }
        const src = largest(img.getAttribute('srcset')) || img.getAttribute('src');
        if (!src || src.startsWith('data:')) {
          img.removeAttribute('src');
          continue;
        }
        const u = new URL(src, location.href);
        // http images are blocked on these https pages, and plain http requests from the app too.
        u.protocol = 'https:';
        let n = images.indexOf(u.href);
        if (n < 0) n = images.push(u.href) - 1;
        img.setAttribute('src', 'img:' + n);
      }
      for (const e of $$('*', c)) for (const a of [...e.attributes]) if (!KEEP.has(a.name)) e.removeAttribute(a.name);
      // The viewer counts the saved guide's <section>s as its pages.
      for (const e of $$('section', c)) {
        const div = document.createElement('div');
        div.append(...e.childNodes);
        e.replaceWith(div);
      }
      out = c.innerHTML;
      return { length: out.length, images: images.length };
    },

    part(i) {
      return out.substr(i * CHUNK, CHUNK);
    },

    /** Starts reading image n from inside the page; imageResult(n) has it once done. Returns its URL. */
    image(n) {
      delete results[n];
      fetch(images[n])
        .then(r => { if (!r.ok) throw new Error('HTTP ' + r.status); return r.blob(); })
        .then(b => new Promise((resolve, reject) => {
          const f = new FileReader();
          f.onload = () => resolve(f.result);
          f.onerror = () => reject(f.error);
          f.readAsDataURL(b);
        }))
        .then(data => { results[n] = { data }; }, e => { results[n] = { error: String(e) }; });
      return images[n];
    },

    /**
     * Starts asking the site whether it has each of [urls] (the browser sends a few at a time); missing() has the
     * answer. A contents list can link pages that aren't there (IGN's wikis do), and a "not found" page reads as a
     * guide page of footer text. An address that gives no answer counts as there.
     */
    check(urls) {
      gone = null;
      Promise.all(urls.map(u => fetch(u, { method: 'HEAD' }).then(r => r.status === 404 || r.status === 410, () => false)))
        .then(not => { gone = urls.filter((u, i) => not[i]); });
      return urls.length;
    },

    /** Null while check() runs; then the addresses the site answered "not found" or "gone" for. */
    missing() {
      return gone;
    },

    /** Null while image n loads; then its error, or its type and length with the base64 kept for part(). */
    imageResult(n) {
      const r = results[n];
      if (!r) return null;
      if (r.error) return { error: r.error };
      const comma = r.data.indexOf(',');
      out = r.data.slice(comma + 1);
      return { mime: r.data.slice(5, r.data.indexOf(';')), length: out.length };
    },
  };
})();
