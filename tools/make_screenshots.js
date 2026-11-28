// Store screenshots: renders the app (assets/index.html) at 390x780 x 2.77 with seeded watches/records and a real recording fed to the measuring screen.
//   node tools/make_screenshots.js <output dir>   (needs Playwright; REAL below = a watch recording WAV, 28,800 bph, not in the repo)
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
const HTML = '/home/user/timegrapher-note/app/src/main/assets/index.html';
const REAL = '/tmp/claude-0/-home-user/7a8cd3ce-4c6a-51d1-927b-9c9a6752a249/scratchpad/real/t1745.wav';
const OUT = process.argv[2] || '.';
const wavb = fs.readFileSync(REAL); const n = wavb.readUInt32LE(40) / 2; const pcm = wavb.subarray(44, 44 + n * 2).toString('base64');
const t = (d, h, m) => new Date(2026, 10, d, h, m).getTime();
const log = [
 [t(24,14,49), 't', 5.52, 311, .08], [t(24,16,18), 't', 6.69, 307, .03], [t(24,16,32), 't', 5.89, 302, .05], [t(24,17,9), 't', 5.06, 302, .06], [t(24,17,37), 't', 3.66, 306, .08], [t(24,17,40), 't', 4.06, 307, .10], [t(24,17,45), 't', 4.05, 318, .16],
 [t(24,17,6), 'h', 1.93, 297, .68], [t(24,17,17), 'h', 1.75, 286, .02], [t(24,17,36), 'h', 3.23, 290, .56], [t(24,20,43), 'h', 2.53, 305, .12], [t(24,20,48), 'h', 1.88, 309, .16],
].map(([ts, w, rate, amp, be]) => ({ ts, wid: w, watch: w === 't' ? '튜더 블랙베이 58' : '해밀턴 카키필드 36mm', pos: '다이얼 위', rate, rateErr: .05, amp, be, bph: w === 't' ? 28800 : 21600 })).sort((a, b) => a.ts - b.ts);
const watches = [
 { id: 't', name: '튜더 블랙베이 58', mov: '', bph: '28800', la: 52, stamp: '2026-11-18', svcYears: 5, memo: '', dial: 'black' },
 { id: 'h', name: '해밀턴 카키필드 36mm', mov: '', bph: '21600', la: 50, svcYears: 5, memo: '', dial: 'green' },
];
(async () => { const b = await chromium.launch();
  const ctx = await b.newContext({ colorScheme: 'light', viewport: { width: 390, height: 780 }, deviceScaleFactor: 1080 / 390, acceptDownloads: true }); const p = await ctx.newPage(); const errs = []; p.on('pageerror', e => errs.push(e.message));
  await p.addInitScript(([w, l, pcm]) => {
    localStorage.setItem('tg-lang', 'ko'); localStorage.setItem('tg-watches-v1', JSON.stringify(w)); localStorage.setItem('tg-log-v1', JSON.stringify(l)); localStorage.setItem('tg-cur-watch', 't'); localStorage.setItem('tg-dur', '60');
    const bin = atob(pcm); const x = new Uint8Array(bin.length); for (let i = 0; i < bin.length; i++) x[i] = bin.charCodeAt(i);
    let off = 0, timer = null; const chunk = 4800 * 2;
    window.AndroidRec = { start() { off = 0; timer = setInterval(() => { if (off >= x.length) return; const c = x.subarray(off, off + chunk); off += chunk; let s = ''; for (let i = 0; i < c.length; i += 0x2000) s += String.fromCharCode.apply(null, c.subarray(i, i + 0x2000)); window.__recData && window.__recData(btoa(s)); }, 20); return 48000; }, stop() { clearInterval(timer); }, sheet() {}, setReminders() {}, notifOk() { return true; }, askNotif() {}, saveFile(b64) { window.__saved = b64; setTimeout(() => window.__fileSaved && window.__fileSaved('ok'), 50); }, saveWav() {} };
  }, [watches, log, pcm]);
  await p.goto('file://' + HTML); await p.waitForTimeout(800);
  const shot = async (name, opt = {}) => { await p.waitForTimeout(400); await p.screenshot({ path: path.join(OUT, name), ...opt }); console.log('saved', name); };
  await shot('1_measure_start.png');
  await p.click('#liveBtn'); await p.waitForTimeout(9000); await shot('2_measuring.png');
  await p.waitForTimeout(8000); if (await p.isVisible('#mStop')) await p.waitForFunction(() => !document.querySelector('#mStop') || document.querySelector('#mStop').hidden, null, { timeout: 30000 }).catch(() => {});
  await p.waitForTimeout(500); await p.click('#mClose'); await p.waitForTimeout(1200); await p.evaluate(() => { const y = document.getElementById('res-h').getBoundingClientRect().top + window.scrollY - 28; window.scrollTo(0, y); }); await shot('3_result.png');
  await p.click('#shareImg'); await p.waitForTimeout(500); fs.writeFileSync(path.join(OUT, '6_result_image.png'), Buffer.from(await p.evaluate(() => window.__saved), 'base64'));
  await p.evaluate(() => window.scrollTo(0, 0)); await p.click('#tLib'); await p.waitForTimeout(500); await p.evaluate(() => { window.scrollTo(0, 0); const r = document.getElementById('remindRow'); if (r) r.style.display = 'none'; document.querySelectorAll('#lib textarea').forEach(t => t.style.display = 'none'); document.querySelectorAll('#lib .acts').forEach(t => t.style.display = 'none'); }); await shot('4_library.png');
  await p.evaluate(() => document.getElementById('logPanel').scrollIntoView({ block: 'start' })); await shot('5_records.png');
  console.log('errors', errs); await b.close(); })();
