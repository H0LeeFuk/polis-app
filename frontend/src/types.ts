export interface PlayerDto {
  id: number; username: string; level: number;
  combatPoints: number; combatToNext: number;
  citySlots: number; ownedCities: number; totalPoints: number; alliance?: string;
}
export interface CitySummary { id: number; name: string; points: number; capital: boolean; island: string; }
export interface Resources { wood: number; stone: number; silver: number; capacity: number; favor: number; }
export interface BuildingDto { type: string; level: number; max: number; pop: number; cost: number[]; seconds: number; atMax: boolean; }
export interface QueueJob { id: number; position: number; totalSeconds: number; finishAt: string | null; label: string; toLevel?: number; batch?: number; }
export interface UnitDto { type: string; count: number; }
export interface Trainable { type: string; from: string; kind: string; atk: number; def: number; speed: number; pop: number; carry: number; cost: number[]; seconds: number; unlocked: boolean; }
export interface ResearchDto { type: string; req: number; done: boolean; cost: number[]; }
export interface MovementDto { id: number; phase: string; arriveAt: string; target: string; }
export interface CityDetail {
  id: number; name: string; capital: boolean; island: string; points: number; god: string | null;
  resources: Resources; pop: number; maxPop: number;
  buildings: BuildingDto[];
  queues: { BUILDING: QueueJob[]; BARRACKS: QueueJob[]; HARBOR: QueueJob[] };
  units: UnitDto[]; trainable: Trainable[]; research: ResearchDto[]; movements: MovementDto[];
}
export interface GameState { player: PlayerDto; cities: CitySummary[]; active: CityDetail; }

export interface WorldCity { id: number; slot: number; name: string; points: number; power: number; faction: string; }
export interface WorldIsland { id: number; name: string; px: number; py: number; cities: WorldCity[]; }
export interface WorldData { islands: WorldIsland[]; }
export interface RankRow { name: string; value: number; sub: string; }
