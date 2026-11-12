/* FlareTune v1 —— 首页结构取自 Spotify now_playing_bar / funkis_home / glue_card 布局，
   全屏页秩序取自 Apple Music 播放页。常量与来源见 index.html 顶部注释与 README。 */

const TRACKS = [
  {
    title: '难忘的一天', artist: '邓丽君', kind: '专辑', dur: 214,
    art: 'linear-gradient(160deg,#2f5aa8 0%,#5f8fd0 42%,#1b2f5c 100%)',
    tint: '#22346a',
    glyph: '难忘的一天',
    lyric: [
      [0, '不知你从哪里来'],
      [16, '你我也许相逢在梦里'],
      [33, '一时之间我不能相信'],
      [50, '这是难忘的一天'],
      [68, '如果你也不愿意'],
      [85, '就让时间来证明'],
      [102, '这一天 这一刻'],
      [120, '我会永远记得'],
      [140, '难忘的一天'],
      [162, '难忘的一天'],
    ],
  },
  {
    title: '失恋阵线联盟', artist: '草蜢', kind: '专辑', dur: 236,
    art: 'linear-gradient(150deg,#e0b478 0%,#a8703f 45%,#3a2418 100%)',
    tint: '#3a2a1c',
    glyph: '失恋阵线\n联盟',
    lyric: [
      [0, '她总是只留下电话号码'],
      [18, '从不肯让我送她回家'],
      [36, '听说你也曾经爱上过她'],
      [55, '曾经也同样无法自拔'],
      [74, '你说你学不会假装潇洒'],
      [93, '却叫我别太早放弃她'],
      [112, '把过去全说成一段神话'],
      [132, '然后笑彼此一样地傻'],
      [152, '我们这么在乎她'],
      [172, '却被她全部抹煞'],
      [192, '越疼她越伤心'],
      [212, '永远得不到回答'],
    ],
  },
  {
    title: '永远是你的朋友', artist: '陈慧娴', kind: '专辑', dur: 258,
    art: 'linear-gradient(200deg,#e8d6b8 0%,#b99a6e 40%,#4a3524 100%)',
    tint: '#3d2c1e',
    glyph: '永远是\n你的朋友',
    lyric: [
      [0, '来日纵使千千晚星'],
      [20, '亮过今晚月亮'],
      [40, '都比不起这宵美丽'],
      [60, '亦绝不可使我更欣赏'],
      [82, '因你今晚共我唱'],
      [104, '临行临别'],
      [124, '才顿感哀伤的漂亮'],
      [146, '原来全是你'],
      [168, '令我的思忆漫长'],
      [190, '何年何月'],
      [212, '才又可今宵一样'],
    ],
  },
  {
    title: '难得有情人', artist: '关淑怡', kind: '专辑', dur: 241,
    art: 'linear-gradient(140deg,#8a7a6a 0%,#c4a68e 38%,#2a2420 100%)',
    tint: '#332a24',
    glyph: '难得\n有情人',
    lyric: [
      [0, '如早春初醒'],
      [18, '催促我的心'],
      [36, '将不可再等'],
      [54, '含情待放那岁月'],
      [74, '空出了痴心'],
      [94, '令人动心'],
      [114, '幸福的光阴'],
      [136, '它不会偏心'],
      [158, '将分给每颗心'],
      [180, '情缘亦远亦近'],
      [204, '将交错一生'],
    ],
  },
  {
    title: '明天会更好', artist: '群星', kind: '单曲', dur: 268,
    art: 'linear-gradient(160deg,#9fc4e0 0%,#5b8fc0 45%,#1e3350 100%)',
    tint: '#243a5c',
    glyph: '明天会\n更好',
    lyric: [
      [0, '轻轻敲醒沉睡的心灵'],
      [20, '慢慢张开你的眼睛'],
      [40, '看看忙碌的世界'],
      [60, '是否依然孤独地转个不停'],
      [84, '春风不解风情'],
      [104, '吹动少年的心'],
      [124, '让昨日脸上的泪痕'],
      [146, '随记忆风干了'],
      [168, '唱出你的热情'],
      [190, '伸出你双手'],
      [212, '让我拥抱着你的梦'],
    ],
  },
  {
    title: '海阔天空', artist: 'Beyond', kind: '专辑', dur: 326,
    art: 'linear-gradient(170deg,#4a6a8c 0%,#20303f 55%,#0b1014 100%)',
    tint: '#1d2a38',
    glyph: '海阔\n天空',
    lyric: [
      [0, '今天我 寒夜里看雪飘过'],
      [22, '怀着冷却了的心窝漂远方'],
      [46, '风雨里追赶 雾里分不清影踪'],
      [70, '天空海阔你与我'],
      [92, '可会变（谁没在变）'],
      [116, '多少次 迎着冷眼与嘲笑'],
      [140, '从没有放弃过心中的理想'],
      [166, '一刹那恍惚 若有所失的感觉'],
      [192, '不知不觉已变淡'],
      [214, '心里爱（谁明白我）'],
      [240, '原谅我这一生不羁放纵爱自由'],
      [268, '也会怕有一天会跌倒'],
      [296, '背弃了理想 谁人都可以'],
    ],
  },
];

const RECENT = [
  { title: '千千阙歌', artist: '陈慧娴', t: TRACKS[2] },
  { title: '不再犹豫', artist: 'Beyond', t: TRACKS[5] },
  { title: '甜蜜蜜', artist: '邓丽君', t: TRACKS[0] },
  { title: '失恋', artist: '草蜢', t: TRACKS[1] },
];

const $ = s => document.querySelector(s);

/* ---------- 渲染 ---------- */
function artHTML(t) {
  return `<div class="art" style="background:${t.art}">
    <div class="glyph">${t.glyph.replace(/\n/g, '<br>')}</div>
  </div>`;
}

function cardHTML(t, i) {
  return `<button class="card" data-i="${i}" data-open>
    ${artHTML(t)}
    <div class="kind">${t.kind}</div>
    <div class="title">${t.title}</div>
    <div class="artist">${t.artist}</div>
  </button>`;
}

$('#row1').innerHTML = TRACKS.map(cardHTML).join('');
$('#row2').innerHTML = [...TRACKS].reverse().map(t => cardHTML(t, TRACKS.indexOf(t))).join('');

$('#recent').innerHTML = RECENT.map((r, i) => `
  <button class="lrow" data-i="${TRACKS.indexOf(r.t)}" data-open>
    <span class="lart" style="background:${r.t.art}"></span>
    <span class="lt"><b>${r.title}</b><span>${r.artist}</span></span>
    <span class="lmore">···</span>
  </button>`).join('');

/* ---------- 播放状态 ---------- */
const S = {
  i: 0,
  playing: false,
  pos: 0,
  timer: null,
  started: false,   // 有曲目后才展开 mini player
};

const cur = () => TRACKS[S.i];
const fmt = s => {
  s = Math.max(0, Math.floor(s));
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
};

function paintMini() {
  const t = cur();
  $('#miniArt').style.background = t.art;
  $('#miniTitle').textContent = t.title;
  $('#miniSub').innerHTML = `${t.artist}<span class="dot">•</span>REDMI Buds 8`;
  $('#mini').style.setProperty('--mini-bg', t.tint);
  $('#mini').classList.toggle('has-track', S.started);
  $('#miniProg').style.width = `${(S.pos / t.dur) * 100}%`;
  $('#wash').style.setProperty('--wash',
    `radial-gradient(135% 95% at 30% 2%,${t.tint} 0%,${t.tint} 18%,#0b0b0b 72%)`);
}

function paintFull() {
  const t = cur();
  $('#fpArt').style.background = t.art;
  $('#fpArt').innerHTML = `<div class="glyph" style="position:absolute;inset:0;display:grid;place-items:center;font-size:34px;font-weight:800;color:rgba(255,255,255,.9);text-align:center;line-height:1.15;padding:24px">${t.glyph.replace(/\n/g, '<br>')}</div>`;
  $('#fpArt').style.position = 'relative';
  $('#fpArt').style.overflow = 'hidden';
  $('#fpTitle').textContent = t.title;
  $('#fpArtist').textContent = t.artist;
  $('#tCur').textContent = fmt(S.pos);
  $('#tDur').textContent = fmt(t.dur);
  $('#fpFill').style.width = `${(S.pos / t.dur) * 100}%`;
  $('#fpKnob').style.left = `${(S.pos / t.dur) * 100}%`;
  paintMini();
  paintLyric();
  paintTeaser();
}

/* 播放页中段的歌词摘录：当前行 + 下一行 */
function paintTeaser() {
  const t = cur();
  let k = 0;
  t.lyric.forEach(([at], j) => { if (S.pos >= at) k = j; });
  $('#tease1').textContent = t.lyric[k] ? t.lyric[k][1] : '';
  $('#tease2').textContent = t.lyric[k + 1] ? t.lyric[k + 1][1] : '';
}

/* ---------- 歌词 ---------- */
function paintLyric() {
  const t = cur();
  const box = $('#lyrInner');
  if (box.dataset.i !== String(S.i)) {
    box.dataset.i = String(S.i);
    box.innerHTML = t.lyric.map(([at, text], k) =>
      `<p data-k="${k}" data-at="${at}">${text}</p>`).join('');
    box.style.transform = 'translateY(0)';
    box.querySelectorAll('p').forEach(p => {
      p.onclick = e => {
        e.stopPropagation();
        S.pos = +p.dataset.at;
        if (!S.playing) toggle();
        else paintFull();
      };
    });
  }
  const ps = [...box.querySelectorAll('p')];
  let idx = 0;
  t.lyric.forEach(([at], k) => { if (S.pos >= at) idx = k; });
  ps.forEach((p, k) => {
    const d = Math.abs(k - idx);
    p.classList.toggle('on', d === 0);
    p.style.opacity = d === 0 ? 1 : Math.max(0.14, 0.5 - d * 0.1);
  });
  // 让当前行停在中上部
  const line = ps[idx];
  if (line) {
    // inner 有 36vh 上内边距，首行 offsetTop 即 36vh，无需负位移
    const want = box.parentElement.clientHeight * 0.34;
    const shift = Math.max(0, line.offsetTop - want);
    box.style.transform = `translateY(${-shift}px)`;
  }
}

/* ---------- 播放控制 ---------- */
function toggle() {
  S.playing = !S.playing;
  const ico = $('#icoPlay');
  ico.innerHTML = S.playing
    ? '<rect x="7" y="5" width="4" height="14" rx="1.2"/><rect x="14" y="5" width="4" height="14" rx="1.2"/>'
    : '<path d="M8 5.5l11 6.5-11 6.5z"/>';
  $('#miniPlay').innerHTML = S.playing
    ? '<svg viewBox="0 0 24 24" style="fill:currentColor;stroke:none;width:20px;height:20px"><rect x="7" y="5" width="4" height="14" rx="1.2"/><rect x="14" y="5" width="4" height="14" rx="1.2"/></svg>'
    : '<svg viewBox="0 0 24 24" style="fill:currentColor;stroke:none;width:20px;height:20px"><path d="M8 5.5l11 6.5-11 6.5z"/></svg>';
  clearInterval(S.timer);
  if (S.playing) {
    S.timer = setInterval(() => {
      S.pos += 1;
      if (S.pos >= cur().dur) { S.pos = 0; next(); return; }
      $('#tCur').textContent = fmt(S.pos);
      $('#fpFill').style.width = `${(S.pos / cur().dur) * 100}%`;
      $('#fpKnob').style.left = `${(S.pos / cur().dur) * 100}%`;
      $('#miniProg').style.width = `${(S.pos / cur().dur) * 100}%`;
      paintLyric();
    }, 1000);
  }
}

function select(i, autoplay = true) {
  S.i = i; S.pos = 0; S.started = true;
  paintFull();
  if (autoplay && !S.playing) toggle();
  else if (autoplay && S.playing) { clearInterval(S.timer); S.playing = false; toggle(); }
}

function next() { select((S.i + 1) % TRACKS.length, S.playing); }
function prev() { select((S.i - 1 + TRACKS.length) % TRACKS.length, S.playing); }

/* ---------- 停止播放：mini player 以 anticipate 收起 ---------- */
let suppressClick = false;
function stop() {
  S.started = false;
  S.playing = false;
  clearInterval(S.timer);
  close();
  const m = $('#mini');
  m.classList.add('is-leaving');
  m.classList.remove('has-track');
  setTimeout(() => m.classList.remove('is-leaving'), 180);
  $('#icoPlay').innerHTML = '<path d="M8 5.5l11 6.5-11 6.5z"/>';
  $('#miniPlay').innerHTML =
    '<svg viewBox="0 0 24 24" style="fill:currentColor;stroke:none;width:20px;height:20px"><path d="M8 5.5l11 6.5-11 6.5z"/></svg>';
  suppressClick = true;
  setTimeout(() => { suppressClick = false; }, 400);
}

/* ---------- 全屏开关 ---------- */
const full = $('#full');
function open() {
  full.classList.add('is-open');
  if (!S.playing) toggle();
}
function close() {
  full.classList.remove('is-open');
  full.classList.remove('is-lyric');
  $('#btnLyric').classList.remove('on');
}

document.addEventListener('click', e => {
  if (suppressClick) { suppressClick = false; return; }
  const hit = e.target.closest('[data-open]');
  if (!hit) return;
  if (hit.classList.contains('card') || hit.classList.contains('lrow')) {
    select(+hit.dataset.i);
  } else {
    paintFull();
    open();
  }
});

$('#fpClose').onclick = close;
$('#miniPlay').onclick = e => { e.stopPropagation(); toggle(); };
$('#miniDev').onclick = e => e.stopPropagation();

/* 长按 mini player（600ms）停止播放，看收起动效 */
let lp = null;
const miniEl = $('#mini');
miniEl.addEventListener('pointerdown', e => {
  if (e.target.closest('.mbtn')) return;
  lp = setTimeout(stop, 600);
});
['pointerup', 'pointercancel', 'pointerleave'].forEach(ev =>
  miniEl.addEventListener(ev, () => clearTimeout(lp)));
$('#btnPlay').onclick = toggle;
$('#btnNext').onclick = next;
$('#btnPrev').onclick = prev;

$('#btnShuffle').onclick = e => e.currentTarget.classList.toggle('on');
$('#btnRepeat').onclick = e => e.currentTarget.classList.toggle('on');
$('#btnLike').onclick = e => {
  const b = e.currentTarget;
  const on = !b.classList.contains('on');
  b.classList.toggle('on', on);
  b.querySelector('path').setAttribute('fill', on ? 'currentColor' : 'none');
};
$('#btnLyric').onclick = e => {
  const on = !full.classList.contains('is-lyric');
  full.classList.toggle('is-lyric', on);
  e.currentTarget.classList.toggle('on', on);
  if (on) paintLyric();
};
$('#btnQueue').onclick = () => {};
$('#fpTeaser').onclick = () => {
  full.classList.add('is-lyric');
  $('#btnLyric').classList.add('on');
  paintLyric();
};

/* ---------- 进度拖拽 ---------- */
(function drag() {
  const bar = $('#fpBar');
  let on = false;
  const set = clientX => {
    const r = bar.getBoundingClientRect();
    const p = Math.min(1, Math.max(0, (clientX - r.left) / r.width));
    S.pos = p * cur().dur;
    $('#tCur').textContent = fmt(S.pos);
    $('#fpFill').style.width = `${p * 100}%`;
    $('#fpKnob').style.left = `${p * 100}%`;
    $('#miniProg').style.width = `${p * 100}%`;
    paintLyric();
  };
  bar.addEventListener('pointerdown', e => {
    on = true; bar.classList.add('is-drag'); bar.setPointerCapture(e.pointerId); set(e.clientX);
  });
  bar.addEventListener('pointermove', e => { if (on) set(e.clientX); });
  bar.addEventListener('pointerup', e => {
    on = false; bar.classList.remove('is-drag');
  });
})();

/* ---------- chips ---------- */
$('#chips').addEventListener('click', e => {
  const c = e.target.closest('.chip');
  if (!c) return;
  [...$('#chips').children].forEach(x => x.classList.toggle('is-active', x === c));
});

/* ---------- 键盘 ---------- */
document.addEventListener('keydown', e => {
  if (e.key === 'Escape') close();
  if (e.key === ' ') { e.preventDefault(); toggle(); }
  if (e.key === 'ArrowRight') next();
  if (e.key === 'ArrowLeft') prev();
});

/* ---------- 初始化 ---------- */
paintFull();
