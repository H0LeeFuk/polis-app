// Procedural vector buildings (original art) — ported from the standalone game.
// Each returns an SVG string drawn in a ~120x120 viewBox; size grows subtly with level.

const C = {
  terracotta: "#c4582f", slip: "#1c1410", ivory: "#ece0c4",
  gold: "#d8ad53", teal: "#2f6d6d", roof: "#9c3b22", stone: "#cdbf9e", dark: "#3a2a1d",
};

const roof = (x: number, y: number, w: number, h: number) =>
  `<polygon points="${x},${y} ${x + w / 2},${y - h} ${x + w},${y}" fill="${C.roof}" stroke="${C.slip}" stroke-width="1.5"/>`;
const wallBox = (x: number, y: number, w: number, h: number, fill = C.ivory) =>
  `<rect x="${x}" y="${y}" width="${w}" height="${h}" fill="${fill}" stroke="${C.slip}" stroke-width="1.5"/>`;
const col = (x: number, y: number, h: number) =>
  `<rect x="${x}" y="${y}" width="5" height="${h}" rx="2" fill="${C.stone}" stroke="${C.slip}" stroke-width="1"/>`;

function temple(s: number) {
  let cols = "";
  for (let i = 0; i < 5; i++) cols += col(34 + i * 12, 56, 30);
  return `<rect x="28" y="86" width="64" height="6" fill="${C.stone}" stroke="${C.slip}"/>${cols}
    <rect x="30" y="52" width="60" height="6" fill="${C.ivory}" stroke="${C.slip}"/>${roof(28, 52, 64, 16 + s * 2)}`;
}
function house(fill: string, s: number) {
  return `${wallBox(40, 60, 40, 30, fill)}${roof(36, 60, 48, 14 + s * 2)}
    <rect x="55" y="72" width="10" height="18" fill="${C.dark}"/>`;
}
function tower(s: number) {
  return `${wallBox(48, 40, 24, 50, C.stone)}<rect x="46" y="34" width="28" height="8" fill="${C.ivory}" stroke="${C.slip}"/>
    <rect x="49" y="34" width="5" height="6" fill="${C.slip}"/><rect x="58" y="34" width="5" height="6" fill="${C.slip}"/>
    <rect x="67" y="34" width="5" height="6" fill="${C.slip}"/><rect x="56" y="60" width="8" height="30" fill="${C.dark}"/>`;
}

const ART: Record<string, (s: number) => string> = {
  SENATE: (s) => temple(s + 2),
  TEMPLE: (s) => temple(s + 1),
  ACADEMY: (s) => `${house(C.ivory, s)}<circle cx="60" cy="50" r="6" fill="${C.gold}" stroke="${C.slip}"/>`,
  BARRACKS: (s) => `${house(C.terracotta, s)}<rect x="44" y="64" width="32" height="4" fill="${C.slip}"/>`,
  HARBOR: (s) => `<rect x="30" y="78" width="60" height="12" fill="${C.teal}" stroke="${C.slip}"/>${house(C.stone, s)}
    <rect x="74" y="40" width="4" height="40" fill="${C.dark}"/><polygon points="78,42 78,64 60,52" fill="${C.ivory}" stroke="${C.slip}"/>`,
  WALL: (s) => `${tower(s)}`,
  WAREHOUSE: (s) => `${wallBox(38, 58, 44, 32, C.stone)}${roof(34, 58, 52, 12)}<rect x="46" y="66" width="28" height="16" fill="${C.dark}"/>`,
  FARM: (s) => `${house(C.ivory, s)}<rect x="40" y="84" width="40" height="6" fill="${C.gold}"/>
    <circle cx="48" cy="80" r="3" fill="${C.terracotta}"/><circle cx="60" cy="80" r="3" fill="${C.terracotta}"/><circle cx="72" cy="80" r="3" fill="${C.terracotta}"/>`,
  TIMBER: (s) => `${house(C.terracotta, s)}<rect x="30" y="80" width="14" height="4" fill="${C.dark}"/><rect x="33" y="70" width="4" height="14" fill="${C.dark}"/>`,
  QUARRY: (s) => `<polygon points="40,88 52,62 72,62 84,88" fill="${C.stone}" stroke="${C.slip}" stroke-width="1.5"/>
    <rect x="50" y="70" width="10" height="8" fill="${C.dark}"/><rect x="64" y="74" width="9" height="6" fill="${C.dark}"/>`,
  MINE: (s) => `<polygon points="42,88 60,58 78,88" fill="${C.dark}" stroke="${C.slip}" stroke-width="1.5"/>
    <rect x="54" y="74" width="12" height="14" fill="${C.slip}"/><circle cx="60" cy="68" r="4" fill="${C.gold}"/>`,
  AGORA: (s) => `<rect x="30" y="84" width="60" height="6" fill="${C.stone}" stroke="${C.slip}"/>${col(38, 60, 24)}${col(58, 60, 24)}${col(78, 60, 24)}
    <rect x="34" y="56" width="50" height="5" fill="${C.ivory}" stroke="${C.slip}"/>`,
};

export function buildingSvg(type: string, level: number): string {
  const s = Math.min(6, Math.max(0, level));
  const inner = (ART[type] || house.bind(null, C.ivory))(s);
  const scale = 0.8 + s * 0.03;
  return `<svg viewBox="0 0 120 120" xmlns="http://www.w3.org/2000/svg" style="overflow:visible">
    <g transform="translate(60 92) scale(${scale}) translate(-60 -92)">${inner}</g>
    <text x="60" y="108" text-anchor="middle" font-family="Cinzel" font-size="11" fill="${C.ivory}" stroke="${C.slip}" stroke-width="0.5">${level}</text>
  </svg>`;
}

export function emptyPlotSvg(): string {
  return `<svg viewBox="0 0 120 120" xmlns="http://www.w3.org/2000/svg">
    <rect x="34" y="60" width="52" height="30" rx="4" fill="none" stroke="#6b5740" stroke-width="2" stroke-dasharray="6 5"/>
    <text x="60" y="80" text-anchor="middle" font-family="Cinzel" font-size="26" fill="#6b5740">+</text></svg>`;
}

export function constructionSvg(): string {
  return `<svg viewBox="0 0 120 120" xmlns="http://www.w3.org/2000/svg">
    <rect x="40" y="66" width="40" height="24" fill="#2a1f15" stroke="#6b5740"/>
    <line x1="44" y1="44" x2="44" y2="66" stroke="#6b5740" stroke-width="3"/>
    <line x1="44" y1="46" x2="74" y2="46" stroke="#6b5740" stroke-width="3"/>
    <line x1="72" y1="46" x2="72" y2="58" stroke="#d8ad53" stroke-width="2"/></svg>`;
}
