import type {
  GameState, WorldData, RankRow, InboxMsg, Movement, AttackPreview, PlayerMovements,
  BattleReport, BattleReportPage, BattleOutcome, Hero, ResourceNode, HeroItemDto,
  BanditCamp, BanditAttackResult, IslandSlots, FoundingStatus, MissionsData, IslandBoss, BossAttackResult,
  LibraryData,
} from "./types";

const TOKEN_KEY = "polis_token";
export const getToken = () => localStorage.getItem(TOKEN_KEY);
export const setToken = (t: string) => localStorage.setItem(TOKEN_KEY, t);
export const clearToken = () => localStorage.removeItem(TOKEN_KEY);

async function api<T>(path: string, opts: RequestInit = {}): Promise<T> {
  const headers: Record<string, string> = { "Content-Type": "application/json", ...(opts.headers as any) };
  const tok = getToken();
  if (tok) headers["Authorization"] = `Bearer ${tok}`;
  const res = await fetch(path, { ...opts, headers });
  const text = await res.text();
  const data = text ? JSON.parse(text) : {};
  if (!res.ok) throw new Error(data.error || `Request failed (${res.status})`);
  return data as T;
}

export const register = (username: string, email: string, password: string) =>
  api<{ token: string }>("/api/auth/register", { method: "POST", body: JSON.stringify({ username, email, password }) });
export const login = (username: string, password: string) =>
  api<{ token: string }>("/api/auth/login", { method: "POST", body: JSON.stringify({ username, password }) });

export const getState = (cityId?: number) =>
  api<GameState>(`/api/game/state${cityId ? `?cityId=${cityId}` : ""}`);
export const getWorld = () => api<WorldData>("/api/world");
export const getRankings = (type: string) => api<RankRow[]>(`/api/rankings?type=${type}`);
export const getInbox = () => api<InboxMsg[]>("/api/messages");
export const sendMessage = (toPlayerId: number, body: string) =>
  api<{ ok: boolean }>("/api/messages", { method: "POST", body: JSON.stringify({ toPlayerId, body }) });

const post = (cityId: number, action: string, body: any) =>
  api<{ ok: boolean }>(`/api/cities/${cityId}/${action}`, { method: "POST", body: JSON.stringify(body) });

export const doBuild    = (c: number, buildingType: string) => post(c, "build", { buildingType });
export const doTrain    = (c: number, unitType: string, count: number) => post(c, "train", { unitType, count });
export const doResearch = (c: number, researchType: string) => post(c, "research", { researchType });
export const doRename   = (c: number, name: string) => post(c, "rename", { name });
export const doCancel   = (c: number, jobId: number) => api<{ ok: boolean }>(`/api/cities/${c}/cancel/${jobId}`, { method: "POST" });
export const doFinish   = (c: number, jobId: number) => api<{ ok: boolean }>(`/api/cities/${c}/finish/${jobId}`, { method: "POST" });
export const doRaid     = (c: number, targetCityId: number, units: Record<string, number>, heroId: number | null = null) => post(c, "raid", { targetCityId, units, heroId });

// --- city founding (hero settle + race choice) ---
export const getIslandSlots = (islandId: number) => api<IslandSlots>(`/api/islands/${islandId}/slots`);
export const settleSlot = (islandId: number, slotIndex: number, fromCityId: number, heroId: number) =>
  api<{ ok: boolean; movementId: number; arriveAt: string | null }>(
    `/api/islands/${islandId}/slots/${slotIndex}/settle`, { method: "POST", body: JSON.stringify({ fromCityId, heroId }) });
export const foundCity = (islandId: number, slotIndex: number, race: string, cityName: string, heroReturnCityId?: number | null) =>
  api<{ ok: boolean; cityId: number }>(
    `/api/islands/${islandId}/slots/${slotIndex}/found-city`,
    { method: "POST", body: JSON.stringify({ race, cityName, heroReturnCityId: heroReturnCityId ?? null }) });
export const getFoundingStatus = () => api<FoundingStatus>("/api/players/me/founding-status");
export const settlePreview = (islandId: number, fromCityId: number, heroId?: number | null) =>
  api<AttackPreview>(`/api/islands/${islandId}/settle/preview?fromCityId=${fromCityId}${heroId ? `&heroId=${heroId}` : ""}`);

// --- troop movements ---
export const getCityMovements = (cityId: number) => api<Movement[]>(`/api/cities/${cityId}/movements`);
export const getMyMovements = () => api<PlayerMovements>("/api/players/me/movements");
export const previewAttack = (cityId: number, targetCityId: number, units: Record<string, number>) =>
  api<AttackPreview>(`/api/cities/${cityId}/attack/preview?targetCityId=${targetCityId}&units=${encodeURIComponent(JSON.stringify(units))}`);

// --- battle reports ---
export interface ReportFilters { page?: number; size?: number; outcome?: BattleOutcome; read?: boolean; cityId?: number; }
function reportQuery(f: ReportFilters): string {
  const p = new URLSearchParams();
  if (f.page != null) p.set("page", String(f.page));
  if (f.size != null) p.set("size", String(f.size));
  if (f.outcome) p.set("outcome", f.outcome);
  if (f.read != null) p.set("read", String(f.read));
  if (f.cityId != null) p.set("cityId", String(f.cityId));
  const q = p.toString();
  return q ? `?${q}` : "";
}
export const getMyReports = (f: ReportFilters = {}) =>
  api<BattleReportPage>(`/api/players/me/battle-reports${reportQuery(f)}`);
export const getCityReports = (cityId: number, f: ReportFilters = {}) =>
  api<BattleReportPage>(`/api/cities/${cityId}/battle-reports${reportQuery(f)}`);
export const getReport = (reportId: number) =>
  api<BattleReport>(`/api/players/me/battle-reports/${reportId}`);
export const deleteReport = (reportId: number) =>
  api<{ ok: boolean }>(`/api/players/me/battle-reports/${reportId}`, { method: "DELETE" });
export const markAllReportsRead = () =>
  api<{ ok: boolean }>("/api/players/me/battle-reports/read-all", { method: "POST" });
export const getUnreadReportCount = () =>
  api<{ count: number }>("/api/players/me/battle-reports/unread-count");

// --- heroes (Leo + Celine) ---
export const getHeroes = () => api<Hero[]>("/api/players/me/heroes");
export const setHeroAttributes = (heroId: number, leadership: number, cunning: number, valor: number) =>
  api<Hero>(`/api/players/me/heroes/${heroId}/attributes`, { method: "POST", body: JSON.stringify({ leadership, cunning, valor }) });
export const stationHero = (heroId: number, cityId: number) =>
  api<Hero>(`/api/players/me/heroes/${heroId}/station`, { method: "POST", body: JSON.stringify({ cityId }) });
export const armHeroSkill = (heroId: number, skillId: string) =>
  api<Hero>(`/api/players/me/heroes/${heroId}/arm-skill`, { method: "POST", body: JSON.stringify({ skillId }) });
export const getHeroInventory = () => api<HeroItemDto[]>("/api/players/me/inventory");
export const equipHeroItem = (heroId: number, itemId: number) =>
  api<Hero>(`/api/players/me/heroes/${heroId}/equip`, { method: "POST", body: JSON.stringify({ itemId }) });
export const unequipHeroSlot = (heroId: number, slot: string) =>
  api<Hero>(`/api/players/me/heroes/${heroId}/unequip`, { method: "POST", body: JSON.stringify({ slot }) });

// --- library / research tree ---
export const getLibrary = (cityId: number) => api<LibraryData>(`/api/cities/${cityId}/library`);
export const startLibraryResearch = (cityId: number, researchId: string) =>
  api<{ ok: boolean }>(`/api/cities/${cityId}/library/research`, { method: "POST", body: JSON.stringify({ researchId }) });
export const respecLibrary = (cityId: number) =>
  api<{ ok: boolean }>(`/api/cities/${cityId}/library/respec`, { method: "POST" });

// --- missions ---
export const getMissions = () => api<MissionsData>("/api/players/me/missions");
export const claimMission = (missionId: number) =>
  api<{ ok: boolean; rewards: Record<string, number>; unlockedHero?: Hero }>(
    `/api/players/me/missions/${missionId}/claim`, { method: "POST" });

// --- resource nodes ---
export const getIslandNodes = (islandId: number) => api<ResourceNode[]>(`/api/islands/${islandId}/nodes`);
export const getNode = (nodeId: number) => api<ResourceNode>(`/api/nodes/${nodeId}`);
export const getMyNodes = () => api<ResourceNode[]>("/api/players/me/nodes");
type NodeMove = { cityId: number; troops: Record<string, number>; heroId?: number | null };
export const occupyNode = (nodeId: number, b: NodeMove) =>
  api<{ ok: boolean }>(`/api/nodes/${nodeId}/occupy`, { method: "POST", body: JSON.stringify(b) });
export const reinforceNode = (nodeId: number, b: NodeMove) =>
  api<{ ok: boolean }>(`/api/nodes/${nodeId}/reinforce`, { method: "POST", body: JSON.stringify(b) });
export const attackNode = (nodeId: number, b: NodeMove) =>
  api<{ ok: boolean }>(`/api/nodes/${nodeId}/attack`, { method: "POST", body: JSON.stringify(b) });
export const withdrawNode = (nodeId: number, troops?: Record<string, number>) =>
  api<{ ok: boolean }>(`/api/nodes/${nodeId}/withdraw`, { method: "POST", body: JSON.stringify({ troops: troops ?? null }) });

// --- bandit camp ---
export const getBanditCamp = (islandId: number) => api<BanditCamp>(`/api/islands/${islandId}/bandit-camp`);
export const attackBanditCamp = (islandId: number, cityId: number, troops: Record<string, number>) =>
  api<BanditAttackResult>(`/api/islands/${islandId}/bandit-camp/attack`, { method: "POST", body: JSON.stringify({ cityId, troops }) });

// --- island boss ---
export const getIslandBoss = (islandId: number) => api<IslandBoss>(`/api/islands/${islandId}/boss`);
export const attackBoss = (islandId: number, cityId: number, troops: Record<string, number>, heroId: number | null = null) =>
  api<BossAttackResult>(`/api/islands/${islandId}/boss/attack`, { method: "POST", body: JSON.stringify({ cityId, troops, heroId }) });
