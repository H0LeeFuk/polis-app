import type { GameState, WorldData, RankRow, InboxMsg, Movement, AttackPreview, PlayerMovements } from "./types";

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
export const doColonize = (c: number, islandId: number, slot: number) => post(c, "colonize", { islandId, slot });
export const doRaid     = (c: number, targetCityId: number, units: Record<string, number>) => post(c, "raid", { targetCityId, units });

// --- troop movements ---
export const getCityMovements = (cityId: number) => api<Movement[]>(`/api/cities/${cityId}/movements`);
export const getMyMovements = () => api<PlayerMovements>("/api/players/me/movements");
export const previewAttack = (cityId: number, targetCityId: number, units: Record<string, number>) =>
  api<AttackPreview>(`/api/cities/${cityId}/attack/preview?targetCityId=${targetCityId}&units=${encodeURIComponent(JSON.stringify(units))}`);
