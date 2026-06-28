export interface PlayerDto {
  id: number; username: string; level: number;
  combatPoints: number; combatToNext: number;
  citySlots: number; ownedCities: number; totalPoints: number; gold: number; alliance?: string;
}
export interface CitySummary { id: number; name: string; points: number; capital: boolean; island: string; }
export interface Resources { wood: number; stone: number; silver: number; capacity: number; favor: number; woodProd: number; stoneProd: number; silverProd: number; favorProd: number; }
export interface BuildingDto { type: string; level: number; max: number; pop: number; cost: number[]; seconds: number; atMax: boolean; }
export interface QueueJob { id: number; position: number; totalSeconds: number; finishAt: string | null; label: string; toLevel?: number; batch?: number; }
export interface UnitDto { type: string; count: number; }
export interface Trainable { type: string; from: string; kind: string; atk: number; def: number; speed: number; pop: number; carry: number; cost: number[]; seconds: number; unlocked: boolean; }
export interface ResearchDto { type: string; req: number; done: boolean; cost: number[]; }
export interface MovementDto { id: number; phase: string; arriveAt: string; target: string; }

/** Rich movement view from /api/cities/{id}/movements and /api/players/me/movements. */
export interface Movement {
  id: number;
  type: "ATTACK" | "RETURN" | "COLONY" | "SUPPORT";
  status: "TRAVELLING" | "RETURNING";
  originCityId: number | null;
  originCity: string;
  targetCityId: number | null;
  targetCity: string;
  owner: string;
  mine: boolean;
  hostile: boolean;
  unitsKnown: boolean;
  units: Record<string, number> | null;
  loot: Record<string, number> | null;
  departAt: string;
  arriveAt: string;
}
export interface AttackPreview {
  travelSeconds: number;
  distance: number;
  slowestUnit: string | null;
  arriveAt: string;
}
export interface MovementsSummary { attacksOut: number; incomingThreats: number; returning: number; idleCities: number; }
export interface PlayerMovements { summary: MovementsSummary; movements: Movement[]; }
export interface CityDetail {
  id: number; name: string; capital: boolean; island: string; points: number; god: string | null;
  resources: Resources; pop: number; maxPop: number;
  buildings: BuildingDto[];
  queues: { BUILDING: QueueJob[]; BARRACKS: QueueJob[]; HARBOR: QueueJob[] };
  units: UnitDto[]; trainable: Trainable[]; research: ResearchDto[]; movements: MovementDto[];
}
export interface GameState { player: PlayerDto; cities: CitySummary[]; active: CityDetail; }

export interface WorldCity { id: number; slot: number; name: string; points: number; power: number; faction: string; playerId: number | null; owner: string; }
export interface WorldIsland { id: number; name: string; px: number; py: number; cities: WorldCity[]; }
export interface WorldPlayer { id: number; name: string; level: number; combatPoints: number; }
export interface WorldData { islands: WorldIsland[]; players: WorldPlayer[]; }
export interface InboxMsg { id: number; from: string; body: string; sentAt: string; read: boolean; }
export interface RankRow { name: string; value: number; sub: string; }
