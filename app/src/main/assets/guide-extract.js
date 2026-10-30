// Finds the guide on the page the touch browser shows, for saving it. BrowserOverlay injects this after each
// page load and reads it back through evaluateJavascript: there is no bridge. Selectors were read from live
// GameFAQs, Neoseeker and ZeldaDungeon guide pages on 2026-10-30, and break when a site changes its layout.
(() => {
  if (window.esdeGuide) return;
  // Long strings go back to the app in pieces of this many characters.
  const CHUNK = 256 * 1024;
  const DROP = 'script,style,link,meta,iframe,frame,object,embed,form,input,button,select,textarea,noscript,ins,video,audio,canvas,svg';
  // Ad, share and comment blocks, by a word in their id or class: "adthrive-ad", "neoseeker-primis-inline", "disqus_thread".
  const JUNK = /(^|[\s_-])(ad|ads|adthrive|advert\w*|primis\w*|prms\w*|sponsor\w*|share\w*|social\w*|disqus\w*|comments?|readmore)(?=[\s_-]|$)/i;
  const KEEP = new Set(['href', 'src', 'alt', 'id', 'name', 'colspan', 'rowspan', 'title']);

  const $ = (s, root = document) => root.querySelector(s);
  const $$ = (s, root = document) => [...root.querySelectorAll(s)];
  const text = e => (e ? e.innerText.replace(/\s+/g, ' ').trim() : '');
  const bare = href => { const u = new URL(href, location.href); u.hash = ''; return u.href; };
  const here = () => ({ url: bare(location.href), title: document.title });
  // First title for each URL, in order.
  const distinct = pages => pages.filter((p, i) => pages.findIndex(q => q.url === p.url) === i);
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

  const SITES = { 'gamefaqs.gamespot.com': gamefaqs, 'www.neoseeker.com': neoseeker, 'www.zeldadungeon.net': zeldadungeon };
  const guide = () => (SITES[location.host] || (() => null))();

  let out = '';
  let images = [];
  const results = {};

  window.esdeGuide = {
    describe() {
      const g = guide();
      return g ? { kind: g.kind, title: g.title, author: g.author, pages: g.pages } : { kind: 'none' };
    },

    /** Keeps the text guide's text for part(); returns its length. */
    text() {
      out = guide().text();
      return out.length;
    },

    /**
     * Keeps the guide's cleaned HTML for part(): no scripts, frames, forms, ads or share blocks; only plain
     * attributes; links to the guide's [pages] become #sK, other links their text; each image src img:N.
     * Returns its length and the number of images.
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
        const i = href ? index.get(bare(a.href)) : undefined;
        if (i === undefined) a.replaceWith(...a.childNodes);
        else a.setAttribute('href', '#s' + (i + 1));
      }
      images = [];
      for (const img of $$('img', c)) {
        const src = img.getAttribute('src');
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
