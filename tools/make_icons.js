// Regenerates the launcher icons (app/src/main/res/mipmap-*), docs/store/icon_512.png and feature_1024x500.png with Playwright:
//   node tools/make_icons.js   (needs the 'playwright' package; Noto fonts for the graphic)
const { chromium } = require('playwright'); const fs = require('fs'); const path = require('path');
const RES = '/home/user/timegrapher-note/app/src/main/res';
const dial = (id) => { let t = '', sun = '', idx = '';
  for (let i = 0; i < 60; i++) { const big = i % 5 === 0; t += `<line x1="50" y1="${big ? 9.5 : 10.6}" x2="50" y2="${big ? 17 : 14}" stroke="#3A2E1A" stroke-width="${big ? 1.1 : .4}" opacity="${big ? .85 : .55}" transform="rotate(${i * 6} 50 50)"/>`; }
  for (let i = 0; i < 90; i++) sun += `<line x1="50" y1="50" x2="50" y2="8" stroke="#A38A58" stroke-width=".25" opacity=".18" transform="rotate(${i * 4} 50 50)"/>`;
  for (let i = 0; i < 12; i++) idx += `<rect x="48.8" y="11" width="2.4" height="7.5" rx=".5" fill="#B39A66" stroke="#8A7448" stroke-width=".25" transform="rotate(${i * 30} 50 50)"/>`;
  return `<svg viewBox="0 0 100 100" xmlns="http://www.w3.org/2000/svg"><defs><radialGradient id="f${id}" cx=".38" cy=".32" r=".9"><stop offset="0" stop-color="#FBF6E9"/><stop offset="1" stop-color="#E9DFC7"/></radialGradient></defs>
  <circle cx="50" cy="50" r="49" fill="#B39A66"/><circle cx="50" cy="50" r="46.8" fill="#8A7448"/><circle cx="50" cy="50" r="45.6" fill="#B39A66"/><circle cx="50" cy="50" r="43.2" fill="url(#f${id})"/>${sun}${t}${idx}
  <g transform="rotate(-62 50 50)"><polygon points="50,53 48.2,50 49.1,23 50,18.5 50.9,23 51.8,50" fill="#B39A66" stroke="#8A7448" stroke-width=".25"/></g>
  <g transform="rotate(62 50 50)"><polygon points="50,54 48.7,50 49.4,13 50,9 50.6,13 51.3,50" fill="#B39A66" stroke="#8A7448" stroke-width=".25"/></g>
  <g transform="rotate(150 50 50)"><rect x="49.75" y="46" width=".5" height="30" fill="#B8322B"/></g><circle cx="50" cy="50" r="2.6" fill="#B39A66" stroke="#8A7448" stroke-width=".25"/><circle cx="50" cy="50" r="1" fill="#B8322B"/></svg>`; };
const page = (w, h, bg, frac, shadow) => `<html><body style="margin:0;width:${w}px;height:${h}px;background:${bg};display:flex;align-items:center;justify-content:center"><div style="width:${w * frac}px;height:${w * frac}px;${shadow ? `filter:drop-shadow(0 ${w * .012}px ${w * .025}px rgba(0,0,0,.45))` : ''}">${dial('x')}</div></body></html>`;
(async () => { const b = await chromium.launch(); const shot = async (html, w, h, out, transparent) => { const p = await b.newPage({ viewport: { width: w, height: h }, deviceScaleFactor: 1 }); await p.setContent(html); await p.screenshot({ path: out, omitBackground: !!transparent }); await p.close(); };
  const dens = { mdpi: [48, 108], hdpi: [72, 162], xhdpi: [96, 216], xxhdpi: [144, 324], xxxhdpi: [192, 432] };
  for (const [d, [leg, fg]] of Object.entries(dens)) {
    await shot(page(leg, leg, '#151310', .76, true), leg, leg, `${RES}/mipmap-${d}/ic_launcher.png`);
    await shot(page(fg, fg, 'transparent', .6, true), fg, fg, `${RES}/mipmap-${d}/ic_launcher_foreground.png`, true);
  }
  await shot(page(512, 512, '#151310', .76, true), 512, 512, '/home/user/timegrapher-note/docs/store/icon_512.png');
  // feature graphic 1024x500
  const feat = `<html><body style="margin:0;width:1024px;height:500px;background:radial-gradient(90% 120% at 78% 50%,#2A241A 0,#151310 60%,#0E0D0B 100%);position:relative;overflow:hidden;font-family:'Noto Serif CJK KR',serif">
   <div style="position:absolute;left:70px;top:112px;color:#C2A874;font:600 20px 'Noto Sans CJK KR';letter-spacing:.5em">WATCH NOTE</div>
   <div style="position:absolute;left:66px;top:150px;color:#EFE6D3;font:600 112px 'Noto Serif CJK KR';letter-spacing:.02em">워치노트</div>
   <div style="position:absolute;left:72px;top:298px;color:#B3A88F;font:400 34px 'Noto Sans CJK KR'">휴대폰으로 재는 기계식 시계 오차</div>
   <div style="position:absolute;left:72px;top:358px;color:#8F7F63;font:400 24px 'Noto Sans CJK KR';letter-spacing:.06em">하루 오차 · 진폭 · 비트 에러 · 시계 보관함</div>
   <div style="position:absolute;left:78px;top:410px;width:170px;height:1px;background:linear-gradient(90deg,#B39A66,transparent)"></div>
   <div style="position:absolute;left:640px;top:50px;width:360px;height:360px;filter:drop-shadow(0 18px 30px rgba(0,0,0,.55))">${dial('y')}</div>
   <div style="position:absolute;left:640px;top:418px;width:360px;text-align:center;color:#C2A874;font:500 30px 'Noto Serif CJK KR';letter-spacing:.04em">+2.53 <span style="font-size:20px;color:#9A8F7B">초/일</span></div></body></html>`;
  await shot(feat, 1024, 500, '/home/user/timegrapher-note/docs/store/feature_1024x500.png'); await b.close(); })();
