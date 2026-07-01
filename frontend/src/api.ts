import type {
  GameState, WorldData, RankRow, PublicProfile, InboxMsg, Movement, AttackPreview, PlayerMovements,
  BattleReport, BattleReportPage, BattleOutcome, Hero, ResourceNode, HeroItemDto,
  BanditTowerState, BanditTowerLevelRow, BanditTowerAttackResult, IslandSlots, FoundingStatus, MissionsData, IslandBoss, BossDispatch,
  LibraryData, TradeMarket, BuyPreview, TradeConvoyDto, AllianceView, TierProgress,
  AltarState, Progression, SiegeData,
  WorldEndgame, WonderDto, WonderLeader,
  ColossusDto, ColossusDamageRow,
  WatchtowerDto, SpyReportDto, SpyAlertDto, SpyIntel,
  CityGroupsView, CityOverview,
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
export const getServerTime = () => api<{ epochMillis: number }>("/api/game/time");
export const getRankings = (type: string) => api<RankRow[]>(`/api/rankings?type=${type}`);
export const getPlayerProfile = (id: number) => api<PublicProfile>(`/api/players/${id}/profile`);
export const getInbox = () => api<InboxMsg[]>("/api/messages");
export const sendMessage = (toPlayerId: number, body: string) =>
  api<{ ok: boolean }>("/api/messages", { method: "POST", body: JSON.stringify({ toPlayerId, body }) });

const post = (cityId: number, action: string, body: any) =>
  api<{ ok: boolean }>(`/api/cities/${cityId}/${action}`, { method: "POST", body: JSON.stringify(body) });

// ---- City Groups ----
export const getCityGroups = () => api<CityGroupsView>("/api/players/me/city-groups");
export const getCitiesOverview = () => api<CityOverview[]>("/api/players/me/cities-overview");
export const createCityGroup = (name: string, icon: string) =>
  api<{ id: number }>("/api/players/me/city-groups", { method: "POST", body: JSON.stringify({ name, icon }) });
export const patchCityGroup = (id: number, patch: { name?: string; icon?: string; sortOrder?: number }) =>
  api<{ ok: boolean }>(`/api/players/me/city-groups/${id}`, { method: "PATCH", body: JSON.stringify(patch) });
export const deleteCityGroup = (id: number) =>
  api<{ ok: boolean }>(`/api/players/me/city-groups/${id}`, { method: "DELETE" });
export const addCitiesToGroup = (id: number, cityIds: number[]) =>
  api<{ ok: boolean }>(`/api/players/me/city-groups/${id}/cities`, { method: "POST", body: JSON.stringify({ cityIds }) });
export const removeCitiesFromGroup = (id: number, cityIds: number[]) =>
  api<{ ok: boolean }>(`/api/players/me/city-groups/${id}/cities`, { method: "DELETE", body: JSON.stringify({ cityIds }) });

export const doBuild    = (c: number, buildingType: string) => post(c, "build", { buildingType });
export const doTrain    = (c: number, unitType: string, count: number) => post(c, "train", { unitType, count });
export const doResearch = (c: number, researchType: string) => post(c, "research", { researchType });
export const doRename   = (c: number, name: string) => post(c, "rename", { name });
export const doCancel   = (c: number, jobId: number) => api<{ ok: boolean }>(`/api/cities/${c}/cancel/${jobId}`, { method: "POST" });
export const doFinish   = (c: number, jobId: number) => api<{ ok: boolean }>(`/api/cities/${c}/finish/${jobId}`, { method: "POST" });
export const doAttack   = (c: number, targetCityId: number, units: Record<string, number>, heroId: number | null = null, intent?: string) => post(c, "attack", { targetCityId, units, heroId, intent });
export const doSupport  = (c: number, targetCityId: number, units: Record<string, number>) => post(c, "support", { targetCityId, units });
export const getReinforcements = (cityId: number) =>
  api<{ ownerPlayerId: number; owner: string; mine: boolean; units: Record<string, number> }[]>(`/api/cities/${cityId}/reinforcements`);

// Siege & Conquest
export const startSiege   = (c: number, targetCityId: number, units: Record<string, number>, heroId: number | null) =>
  post(c, "attack", { targetCityId, units, heroId, intent: "SIEGE" });
export const getSiege     = (siegeId: number) => api<SiegeData>(`/api/sieges/${siegeId}`);
export const getCitySiege = (cityId: number) => api<SiegeData | { siege: null }>(`/api/cities/${cityId}/siege`);
export const getMySieges  = () => api<SiegeData[]>(`/api/players/me/sieges`);
export const reinforceSiege = (siegeId: number, fromCityId: number, troops: Record<string, number>) =>
  api<unknown>(`/api/sieges/${siegeId}/reinforce`, { method: "POST", body: JSON.stringify({ fromCityId, troops }) });
export const attackSiege  = (siegeId: number, fromCityId: number, troops: Record<string, number>, includeHeroId: number | null = null) =>
  api<unknown>(`/api/sieges/${siegeId}/attack`, { method: "POST", body: JSON.stringify({ fromCityId, troops, includeHeroId }) });
export const withdrawSiege = (siegeId: number) =>
  api<{ ok: boolean }>(`/api/sieges/${siegeId}/withdraw`, { method: "POST" });
export const getSiegeMovements = (siegeId: number) => api<Movement[]>(`/api/sieges/${siegeId}/movements`);

export interface TroopsAbroadRow { locationType: "ALLY_CITY" | "NODE" | "SIEGE"; locationId: number; locationName: string; troops: Record<string, number>; }
export interface ForeignTroopsRow { ownerPlayerId: number; ownerName: string; ownerAlliance: string | null; troops: Record<string, number>; }
export const getTroopsAbroad = (cityId: number) => api<TroopsAbroadRow[]>(`/api/cities/${cityId}/troops-abroad`);
export const getForeignTroops = (cityId: number) => api<ForeignTroopsRow[]>(`/api/cities/${cityId}/foreign-troops`);
export const recallAbroad = (cityId: number, locationType: string, locationId: number) =>
  api<{ ok: boolean }>(`/api/cities/${cityId}/recall-abroad`, { method: "POST", body: JSON.stringify({ locationType, locationId }) });
export const dismissForeign = (cityId: number, ownerPlayerId: number) =>
  api<{ ok: boolean }>(`/api/cities/${cityId}/dismiss-foreign`, { method: "POST", body: JSON.stringify({ ownerPlayerId }) });

export interface SimSide { race: string; troops: Record<string, number>; heroAttack?: number; attackBuff?: number; defenseBuff?: number; }
export interface SimResult {
  layer: string; winner: "ATTACKER" | "DEFENDER"; outcome: string; globalRatio: number;
  attackerAttackPower: number; defenderDefencePower: number;
  attacker: { sent: Record<string, number>; lost: Record<string, number>; survived: Record<string, number> };
  defender: { present: Record<string, number>; lost: Record<string, number>; survived: Record<string, number> };
  attackByElement: Record<string, number>; defenseByElement: Record<string, number>;
}
export const simulateCombat = (attacker: SimSide, defender: SimSide, layer: "SEA" | "LAND") =>
  api<SimResult>(`/api/simulator/combat`, { method: "POST", body: JSON.stringify({ attacker, defender, layer }) });
export const importSpyForSim = (spyReportId: number) =>
  api<{ targetCityName: string; troops: Record<string, number>; buildings: Record<string, number>; capturedAt: string }>(`/api/simulator/import-spy/${spyReportId}`);
export const chooseRace   = (cityId: number, race: string) =>
  api<{ ok: boolean }>(`/api/cities/${cityId}/choose-race`, { method: "POST", body: JSON.stringify({ race }) });

// Altar / Festivals / progression
export const getAltar = (c: number) => api<AltarState>(`/api/cities/${c}/altar`);
export const runFestival = (c: number, festivalType: string, fuelType: string) =>
  api<{ ok: boolean }>(`/api/cities/${c}/altar/festival`, { method: "POST", body: JSON.stringify({ festivalType, fuelType }) });
export const getProgression = () => api<Progression>("/api/players/me/progression");

// --- city founding (hero settle + race choice) ---
export const getIslandSlots = (islandId: number) => api<IslandSlots>(`/api/islands/${islandId}/slots`);
export const settleSlot = (islandId: number, slotIndex: number, fromCityId: number, heroId: number) =>
  api<{ ok: boolean; movementId: number; arriveAt: string | null }>(
    `/api/islands/${islandId}/slots/${slotIndex}/settle`, { method: "POST", body: JSON.stringify({ fromCityId, heroId }) });
export const foundCity = (islandId: number, slotIndex: number, race: string, cityName: string, heroReturnCityId?: number | null) =>
  api<{ ok: boolean; cityId?: number; message?: string }>(
    `/api/islands/${islandId}/slots/${slotIndex}/found-city`,
    { method: "POST", body: JSON.stringify({ race, cityName, heroReturnCityId: heroReturnCityId ?? null }) });
export const getFoundingStatus = () => api<FoundingStatus>("/api/players/me/founding-status");
export const settlePreview = (islandId: number, fromCityId: number, heroId?: number | null) =>
  api<AttackPreview>(`/api/islands/${islandId}/settle/preview?fromCityId=${fromCityId}${heroId ? `&heroId=${heroId}` : ""}`);

// --- troop movements ---
export const getCityMovements = (cityId: number) => api<Movement[]>(`/api/cities/${cityId}/movements`);
export const getMyMovements = () => api<PlayerMovements>("/api/players/me/movements");
export const previewAttack = (cityId: number, targetCityId: number, units: Record<string, number>, heroId?: number | null) =>
  api<AttackPreview>(`/api/cities/${cityId}/attack/preview?targetCityId=${targetCityId}&units=${encodeURIComponent(JSON.stringify(units))}${heroId ? `&heroId=${heroId}` : ""}`);

// --- colossi (daily roaming world bosses) ---
export const getColossi = () => api<ColossusDto[]>("/api/world/colossi");
export const getColossus = (id: number) => api<ColossusDto>(`/api/world/colossi/${id}`);
export const getColossusLeaderboard = (id: number) => api<ColossusDamageRow[]>(`/api/world/colossi/${id}/leaderboard`);
export const attackColossus = (id: number, fromCityId: number, troops: Record<string, number>, includeHeroId?: number | null) =>
  api<{ ok?: boolean; status: string; travelSeconds?: number; arriveAt?: string }>(
    `/api/world/colossi/${id}/attack`, { method: "POST", body: JSON.stringify({ fromCityId, troops, includeHeroId: includeHeroId ?? null }) });
export const spawnColossusNow = () => api<{ ok: boolean }>("/api/world/colossi/spawn-now", { method: "POST" });

// --- espionage (Watchtower) ---
export const getWatchtower = (cityId: number) => api<WatchtowerDto>(`/api/cities/${cityId}/watchtower`);
export const launchSpy = (fromCityId: number, targetCityId: number) =>
  api<{ ok?: boolean; status: string; missionId?: number; resolvesAt?: string }>(
    `/api/cities/${fromCityId}/spy`, { method: "POST", body: JSON.stringify({ targetCityId }) });
export const getSpyReports = () => api<SpyReportDto[]>("/api/players/me/spy-reports");
export const getSpyAlerts = () => api<SpyAlertDto[]>("/api/players/me/spy-alerts");
export const getIntel = (targetCityId: number) =>
  api<{ hasIntel: boolean; intel?: SpyIntel }>(`/api/players/me/intel?targetCityId=${targetCityId}`);

// --- battle reports ---
export interface ReportFilters { page?: number; size?: number; outcome?: BattleOutcome; read?: boolean; cityId?: number; role?: "ATTACKER" | "DEFENDER"; }
function reportQuery(f: ReportFilters): string {
  const p = new URLSearchParams();
  if (f.page != null) p.set("page", String(f.page));
  if (f.size != null) p.set("size", String(f.size));
  if (f.outcome) p.set("outcome", f.outcome);
  if (f.read != null) p.set("read", String(f.read));
  if (f.cityId != null) p.set("cityId", String(f.cityId));
  if (f.role) p.set("role", f.role);
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

// --- heroes (Leo + Titania) ---
export const getHeroes = () => api<Hero[]>("/api/players/me/heroes");
export const setHeroAttributes = (heroId: number, leadership: number, cunning: number, valor: number) =>
  api<Hero>(`/api/players/me/heroes/${heroId}/attributes`, { method: "POST", body: JSON.stringify({ leadership, cunning, valor }) });
export const stationHero = (heroId: number, cityId: number) =>
  api<Hero>(`/api/players/me/heroes/${heroId}/station`, { method: "POST", body: JSON.stringify({ cityId }) });
export const assignHero = (heroId: number, cityId: number) =>
  api<Hero>(`/api/players/me/heroes/${heroId}/assign`, { method: "POST", body: JSON.stringify({ cityId }) });
export const deassignHero = (heroId: number) =>
  api<Hero>(`/api/players/me/heroes/${heroId}/deassign`, { method: "POST" });
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
export const callCityGuard = (cityId: number) =>
  api<{ ok: boolean; summoned: number; readyAt: string }>(`/api/cities/${cityId}/library/call-guard`, { method: "POST" });

// --- trade / marketplace + convoy logistics ---
export const getTradeMarket = (cityId: number) => api<TradeMarket>(`/api/cities/${cityId}/trade`);
export const tradeBuyPreview = (cityId: number, resourceType: string, bundles: number, maxPricePerBundle: number, deliveryCityId: number) =>
  api<BuyPreview>(`/api/cities/${cityId}/trade/buy/preview?resourceType=${resourceType}&bundles=${bundles}&maxPricePerBundle=${maxPricePerBundle}&deliveryCityId=${deliveryCityId}`);
export const tradeBuy = (cityId: number, body: { resourceType: string; bundles: number; maxPricePerBundle: number; deliveryCityId: number }) =>
  api<{ ok: boolean; filledBundles: number; totalGold: number; convoyCount: number; gold: number }>(
    `/api/cities/${cityId}/trade/buy`, { method: "POST", body: JSON.stringify(body) });
export const tradeSell = (cityId: number, body: { resourceType: string; bundles: number; pricePerBundle: number }) =>
  api<{ ok: boolean; listingId: number; escrowedUnits: number }>(
    `/api/cities/${cityId}/trade/sell`, { method: "POST", body: JSON.stringify(body) });
export const cancelTradeListing = (cityId: number, listingId: number) =>
  api<{ ok: boolean }>(`/api/cities/${cityId}/trade/listings/${listingId}/cancel`, { method: "POST" });
export const getTradeDeliveries = () => api<TradeConvoyDto[]>("/api/players/me/trade-deliveries");

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
export const supportNode = (nodeId: number, b: NodeMove) =>
  api<{ ok: boolean }>(`/api/nodes/${nodeId}/support`, { method: "POST", body: JSON.stringify(b) });
export const attackNode = (nodeId: number, b: NodeMove) =>
  api<{ ok: boolean }>(`/api/nodes/${nodeId}/attack`, { method: "POST", body: JSON.stringify(b) });
export const withdrawNode = (nodeId: number, troops?: Record<string, number>) =>
  api<{ ok: boolean }>(`/api/nodes/${nodeId}/withdraw`, { method: "POST", body: JSON.stringify({ troops: troops ?? null }) });

// --- endgame: Wonders of the Aegean ---
export const getWorldState = () => api<WorldEndgame>("/api/world/state");
export const getWonderLeaderboard = () => api<WonderLeader[]>("/api/world/leaderboard");
type WonderMove = { cityId: number; troops: Record<string, number>; heroId?: number | null };
export const occupyWonder = (id: number, b: WonderMove) =>
  api<{ ok: boolean }>(`/api/world/wonders/${id}/occupy`, { method: "POST", body: JSON.stringify(b) });
export const attackWonder = (id: number, b: WonderMove) =>
  api<{ ok: boolean }>(`/api/world/wonders/${id}/attack`, { method: "POST", body: JSON.stringify(b) });
export const withdrawWonder = (id: number, troops?: Record<string, number>) =>
  api<{ ok: boolean }>(`/api/world/wonders/${id}/withdraw`, { method: "POST", body: JSON.stringify({ troops: troops ?? null }) });
export const investWonder = (id: number, cityId: number, each: number) =>
  api<WonderDto>(`/api/world/wonders/${id}/invest`, { method: "POST", body: JSON.stringify({ cityId, each }) });
export const forceEndgame = () => api<{ ok: boolean }>("/api/world/force-endgame", { method: "POST" });

// --- bandit tower (account-wide 100-level PvE climb) ---
export const getBanditTower = () => api<BanditTowerState>("/api/players/me/bandit-tower");
export const getBanditTowerLevels = () => api<BanditTowerLevelRow[]>("/api/players/me/bandit-tower/levels");
export const attackBanditTower = (fromCityId: number, troops: Record<string, number>, includeHeroId?: number | null) =>
  api<BanditTowerAttackResult>("/api/players/me/bandit-tower/attack",
    { method: "POST", body: JSON.stringify({ fromCityId, troops, includeHeroId: includeHeroId ?? null }) });

// --- alliances ---
export const createAlliance = (tag: string, name: string) =>
  api<{ id: number; tag: string; name: string }>("/api/alliances", { method: "POST", body: JSON.stringify({ tag, name }) });
export const getMyAlliance = () => api<AllianceView>("/api/alliances/me");
export const getMyTierProgress = () => api<TierProgress>("/api/alliances/me/tier-progress");
export const setAllianceEmblem = (emblem: string) =>
  api<{ ok: boolean }>("/api/alliances/emblem", { method: "POST", body: JSON.stringify({ emblem }) });
export const inviteToAlliance = (username: string) =>
  api<{ ok: boolean }>("/api/alliances/invite", { method: "POST", body: JSON.stringify({ username }) });
export const acceptAllianceInvite = (allianceId: number) =>
  api<{ ok: boolean }>(`/api/alliances/invites/${allianceId}/accept`, { method: "POST" });
export const declineAllianceInvite = (allianceId: number) =>
  api<{ ok: boolean }>(`/api/alliances/invites/${allianceId}/decline`, { method: "POST" });
export const postAllianceForum = (body: string) =>
  api<{ ok: boolean }>("/api/alliances/forum", { method: "POST", body: JSON.stringify({ body }) });

// --- island boss ---
export const getIslandBoss = (islandId: number) => api<IslandBoss>(`/api/islands/${islandId}/boss`);
export const attackBoss = (islandId: number, cityId: number, troops: Record<string, number>, heroId: number | null = null) =>
  api<BossDispatch>(`/api/islands/${islandId}/boss/attack`, { method: "POST", body: JSON.stringify({ cityId, troops, heroId }) });
