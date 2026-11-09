/**
 * 线性图标集合（24x24、stroke=currentColor）。
 * 通过 icons.xxx() 返回 SVG 字符串，直接插入 innerHTML。
 */

const wrap = (body, extra = '') =>
  `<svg viewBox="0 0 24 24" aria-hidden="true"${extra ? ` ${extra}` : ''}>${body}</svg>`;

/**
 * 品牌标记：拾起的一束光（日出 + 笔记页脚线）。
 * 每次调用生成独立的渐变 id，避免同页多处使用时 id 冲突。
 */
let brandMarkSeq = 0;
const brandMark = () => {
  const id = `brandMarkGrad${(brandMarkSeq += 1)}`;
  return `<svg viewBox="0 0 40 40" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
    <defs>
      <linearGradient id="${id}" x1="4" y1="2" x2="36" y2="38" gradientUnits="userSpaceOnUse">
        <stop stop-color="#8ea1ff"/><stop offset="0.52" stop-color="#4a5cf5"/><stop offset="1" stop-color="#8b5cf6"/>
      </linearGradient>
    </defs>
    <rect width="40" height="40" rx="12" fill="url(#${id})"/>
    <circle cx="20" cy="16.5" r="6.8" fill="#fff" fill-opacity="0.96"/>
    <path d="M7 27.5h26" stroke="#fff" stroke-width="2.6" stroke-linecap="round" stroke-opacity="0.92"/>
    <path d="M13 33h14" stroke="#fff" stroke-width="2.6" stroke-linecap="round" stroke-opacity="0.5"/>
  </svg>`;
};

const icons = {
  sparkles: () =>
    wrap(
      '<path d="M12 3v4M12 17v4M3 12h4M17 12h4M5.6 5.6l2.8 2.8M15.6 15.6l2.8 2.8M18.4 5.6l-2.8 2.8M8.4 15.6l-2.8 2.8"/>'
    ),
  search: () => wrap('<circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/>'),
  compass: () =>
    wrap('<circle cx="12" cy="12" r="9"/><path d="m15.5 8.5-2 5-5 2 2-5z"/>'),
  plus: () => wrap('<path d="M12 5v14M5 12h14"/>'),
  user: () =>
    wrap('<circle cx="12" cy="8" r="4"/><path d="M4.5 20a7.5 7.5 0 0 1 15 0"/>'),
  users: () =>
    wrap(
      '<circle cx="9" cy="8" r="3.4"/><path d="M2.8 19.5a6.2 6.2 0 0 1 12.4 0"/><path d="M16 5.2a3.4 3.4 0 0 1 0 6.6"/><path d="M17.6 13.6a5.6 5.6 0 0 1 3.6 5.2"/>'
    ),
  code: () => wrap('<path d="m8 7-5 5 5 5M16 7l5 5-5 5"/>'),
  heart: () =>
    wrap(
      '<path d="M20.4 5.9a5.1 5.1 0 0 0-7.2 0L12 7.1l-1.2-1.2a5.1 5.1 0 1 0-7.2 7.2L12 21l8.4-7.9a5.1 5.1 0 0 0 0-7.2z"/>'
    ),
  star: () =>
    wrap(
      '<path d="m12 3.5 2.6 5.3 5.9.9-4.3 4.1 1 5.8-5.2-2.7-5.2 2.7 1-5.8L3.5 9.7l5.9-.9z"/>'
    ),
  comment: () =>
    wrap(
      '<path d="M21 11.5a8.4 8.4 0 0 1-9 8.4 9.6 9.6 0 0 1-3.2-.6L3 21l1.7-5.1a8.4 8.4 0 1 1 16.3-4.4z"/>'
    ),
  share: () =>
    wrap(
      '<circle cx="18" cy="5" r="2.6"/><circle cx="6" cy="12" r="2.6"/><circle cx="18" cy="19" r="2.6"/><path d="m8.4 10.8 7.2-4.2M8.4 13.2l7.2 4.2"/>'
    ),
  edit: () =>
    wrap(
      '<path d="M12 20h9"/><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L8 18l-4 1 1-4z"/>'
    ),
  trash: () =>
    wrap(
      '<path d="M3 6h18M8 6V4.5A1.5 1.5 0 0 1 9.5 3h5A1.5 1.5 0 0 1 16 4.5V6M5.5 6l1 13.5A1.5 1.5 0 0 0 8 21h8a1.5 1.5 0 0 0 1.5-1.5L18.5 6"/><path d="M10 10.5v6M14 10.5v6"/>'
    ),
  pin: () =>
    wrap(
      '<path d="M9 3h6l-1 6 4 4v2H6v-2l4-4z"/><path d="M12 15v6"/>'
    ),
  lock: () =>
    wrap(
      '<rect x="4.5" y="10" width="15" height="11" rx="2.5"/><path d="M8 10V7a4 4 0 0 1 8 0v3"/><path d="M12 14.5v2"/>'
    ),
  unlock: () =>
    wrap(
      '<rect x="4.5" y="10" width="15" height="11" rx="2.5"/><path d="M8 10V7a4 4 0 0 1 7.5-2"/><path d="M12 14.5v2"/>'
    ),
  image: () =>
    wrap(
      '<rect x="3" y="4" width="18" height="16" rx="2.5"/><circle cx="8.5" cy="9.5" r="1.6"/><path d="m21 15-5-5-9 9"/>'
    ),
  video: () =>
    wrap(
      '<rect x="3" y="6" width="13" height="12" rx="2.5"/><path d="m16 10 5-2.5v9L16 14"/>'
    ),
  upload: () =>
    wrap('<path d="M12 16V4M7.5 8.5 12 4l4.5 4.5"/><path d="M4 16v3a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-3"/>'),
  close: () => wrap('<path d="M6 6l12 12M18 6 6 18"/>'),
  arrowLeft: () => wrap('<path d="M19 12H5M11 18l-6-6 6-6"/>'),
  arrowRight: () => wrap('<path d="M5 12h14M13 6l6 6-6 6"/>'),
  chevronLeft: () => wrap('<path d="m14.5 6-6 6 6 6"/>'),
  chevronRight: () => wrap('<path d="m9.5 6 6 6-6 6"/>'),
  chevronDown: () => wrap('<path d="m6 9.5 6 6 6-6"/>'),
  check: () => wrap('<path d="m5 12.5 4.5 4.5L19 7.5"/>'),
  alert: () =>
    wrap(
      '<path d="M12 3.5 22 20H2z"/><path d="M12 9.5v4.5"/><circle cx="12" cy="17" r=".7" fill="currentColor" stroke="none"/>'
    ),
  info: () => wrap('<circle cx="12" cy="12" r="9"/><path d="M12 11v5.5"/><circle cx="12" cy="7.8" r=".7" fill="currentColor" stroke="none"/>'),
  refresh: () =>
    wrap(
      '<path d="M20 11a8 8 0 1 0-1.8 6"/><path d="M20 5v6h-6"/>'
    ),
  logout: () =>
    wrap(
      '<path d="M15 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3"/><path d="M10 8l-4 4 4 4"/><path d="M6 12h10"/>'
    ),
  settings: () =>
    wrap(
      '<circle cx="12" cy="12" r="3.2"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.6V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.6 1.7 1.7 0 0 0-1.9.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.9 1.7 1.7 0 0 0-1.6-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.6-1.1 1.7 1.7 0 0 0-.3-1.9l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.9.3H9a1.7 1.7 0 0 0 1-1.6V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.6 1.7 1.7 0 0 0 1.9-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.9V9a1.7 1.7 0 0 0 1.6 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z"/>'
    ),
  link: () =>
    wrap(
      '<path d="M10 13.5a4 4 0 0 0 5.7 0l3-3a4 4 0 1 0-5.7-5.7l-1.5 1.5"/><path d="M14 10.5a4 4 0 0 0-5.7 0l-3 3a4 4 0 1 0 5.7 5.7l1.5-1.5"/>'
    ),
  eye: () =>
    wrap(
      '<path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z"/><circle cx="12" cy="12" r="3"/>'
    ),
  eyeOff: () =>
    wrap(
      '<path d="M3 3l18 18"/><path d="M10.6 6.1A9.7 9.7 0 0 1 12 6c6 0 9.5 6 9.5 6a16 16 0 0 1-3 3.7"/><path d="M6.3 6.8A16.4 16.4 0 0 0 2.5 12S6 18 12 18a9.4 9.4 0 0 0 4-.9"/><path d="M9.9 9.9a3 3 0 0 0 4.2 4.2"/>'
    ),
  play: () => wrap('<path d="M8 5.5 19 12 8 18.5z"/>'),
  external: () =>
    wrap('<path d="M14 4h6v6"/><path d="M20 4 11 13"/><path d="M19 14v4a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2h4"/>'),
  key: () =>
    wrap(
      '<circle cx="7.5" cy="15.5" r="4"/><path d="m10.5 12.5 8-8M16 5l3 3M14 7l2 2"/>'
    ),
  shield: () =>
    wrap('<path d="M12 3 5 6v6c0 4.4 3 7.6 7 9 4-1.4 7-4.6 7-9V6z"/><path d="m9 12 2 2 4-4"/>'),
  zap: () => wrap('<path d="M13 2 4.5 13.5H11l-1 8.5L19.5 10H13z"/>'),
  clock: () =>
    wrap('<circle cx="12" cy="12" r="9"/><path d="M12 7.5V12l3 2"/>'),
  file: () =>
    wrap(
      '<path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z"/><path d="M14 3v5h5"/>'
    ),
  server: () =>
    wrap(
      '<rect x="3" y="4" width="18" height="7" rx="2"/><rect x="3" y="13" width="18" height="7" rx="2"/><path d="M7 7.5h.01M7 16.5h.01"/>'
    ),
  activity: () => wrap('<path d="M3 12h4l2.5-7 5 14 2.5-7H21"/>'),
  grid: () =>
    wrap(
      '<rect x="3.5" y="3.5" width="7" height="7" rx="1.6"/><rect x="13.5" y="3.5" width="7" height="7" rx="1.6"/><rect x="3.5" y="13.5" width="7" height="7" rx="1.6"/><rect x="13.5" y="13.5" width="7" height="7" rx="1.6"/>'
    ),
  more: () =>
    wrap(
      '<circle cx="5" cy="12" r="1.4" fill="currentColor" stroke="none"/><circle cx="12" cy="12" r="1.4" fill="currentColor" stroke="none"/><circle cx="19" cy="12" r="1.4" fill="currentColor" stroke="none"/>'
    ),
  book: () =>
    wrap(
      '<path d="M4 4.5A2.5 2.5 0 0 1 6.5 2H20v18H6.5A2.5 2.5 0 0 0 4 22.5z"/><path d="M4 17.5A2.5 2.5 0 0 1 6.5 15H20"/>'
    ),
  listUl: () =>
    wrap(
      '<path d="M9.5 6h10.5M9.5 12h10.5M9.5 18h10.5"/><circle cx="4.6" cy="6" r="1.4" fill="currentColor" stroke="none"/><circle cx="4.6" cy="12" r="1.4" fill="currentColor" stroke="none"/><circle cx="4.6" cy="18" r="1.4" fill="currentColor" stroke="none"/>'
    ),
  listOl: () =>
    wrap(
      '<path d="M9.5 6h10.5M9.5 12h10.5M9.5 18h10.5"/><path d="M4 4.6h.9V7.6M3.7 7.6h2.4"/><path d="M3.7 10.6h2l-2 2.4h2.1"/><path d="M3.7 16.2h2v1.1h-2v1.1h2v1.1h-2"/>'
    ),
  smile: () =>
    wrap(
      '<circle cx="12" cy="12" r="9"/><path d="M8.4 14.2a4.3 4.3 0 0 0 7.2 0"/><circle cx="9.2" cy="9.8" r="1.1" fill="currentColor" stroke="none"/><circle cx="14.8" cy="9.8" r="1.1" fill="currentColor" stroke="none"/>'
    ),
  sun: () =>
    wrap(
      '<circle cx="12" cy="12" r="4.2"/><path d="M12 2.6v2.2M12 19.2v2.2M4.4 12H2.2M21.8 12h-2.2M6.3 6.3 4.8 4.8M19.2 19.2l-1.5-1.5M17.7 6.3l1.5-1.5M4.8 19.2l1.5-1.5"/>'
    ),
  moon: () =>
    wrap('<path d="M20.5 14.3a8.5 8.5 0 1 1-10.8-10.8 7 7 0 0 0 10.8 10.8z"/>'),
  brandMark,
};

export { icons };
