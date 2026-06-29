export interface PlayerDto {
  id: number; username: string; level: number;
  combatPoints: number; combatToNext: number;
  citySlots: number; ownedCities: number; totalPoints: number; gold: number; alliance?: string;
}
export interface CitySummary { id: number; name: string; points: number; capital: boolean; island: string; }
export type ResourceId = "WOOD" | "STONE" | "WHEAT" | "COAL" | "CRYSTALS" | "IRON" | "PEARLS";
export interface Resources {
  wood: number; stone: number; wheat: number; capacity: number;
  special: number; specialResource: ResourceId;
  woodProd: number; stoneProd: number; wheatProd: number; specialProd: number;
  otherSpecials: Record<string, number>;
}
export interface BuildingDto { type: string; level: number; max: number; pop: number; cost: number[]; seconds: number; atMax: boolean; benefit?: string; }
export interface QueueJob { id: number; position: number; totalSeconds: number; finishAt: string | null; label: string; toLevel?: number; batch?: number; }
export interface UnitDto { type: string; count: number; }
export type Element = "FIRE" | "WIND" | "EARTH" | "WATER";
export type MovementClass = "LAND" | "FLYING" | "SWIMMING";
export interface Trainable {
  type: string; from: string; kind: string; siege: boolean; attackElement: Element | null;
  atk: number; defFire: number; defWind: number; defEarth: number; defWater: number;
  speed: number; pop: number; carry: number; cost: number[]; seconds: number; unlocked: boolean;
  elite: boolean; costSpecial: number; specialResource: ResourceId | null;
  movementClass: MovementClass; transportCapacity: number; requiresTransport: boolean;
}
export interface ResearchDto { type: string; req: number; done: boolean; cost: number[]; }
export interface MovementDto { id: number; phase: string; arriveAt: string; target: string; }

// --- races & city founding ---
export type RaceId = "HUMANS" | "GIANTS" | "FAIRIES" | "NEWTS";
/** Seeded race config sent by the server (city detail, world slots). */
export interface RaceInfo {
  id: RaceId; name: string; description: string; icon: string;
  element?: Element; specialResource?: ResourceId;
  bonuses: Record<string, number>;   // signed percentages: production, attack, defense, travel, loot, researchSpeed
}
export interface IslandSlot {
  slotIndex: number;
  status: "EMPTY" | "OCCUPIED";
  // occupied
  cityId?: number; cityName?: string; points?: number;
  ownerName?: string; faction?: string; alliance?: string | null; race?: RaceInfo | null;
  // empty
  canSettle?: boolean; reason?: string | null;
}
export interface IslandSlots {
  islandId: number; islandName: string; slotsPerIsland: number; occupied: number; slots: IslandSlot[];
}
export interface FoundingStatus {
  founding: {
    movementId: number;
    phase: "MARCHING" | "AWAITING_RACE";
    islandId: number; islandName: string; slotIndex: number;
    fromCityId: number; arriveAt: string | null; arrivedAt: string | null;
  } | null;
}

/** Rich movement view from /api/cities/{id}/movements and /api/players/me/movements. */
export interface Movement {
  id: number;
  type: "ATTACK" | "RETURN" | "COLONY" | "SUPPORT" | "SETTLE" | "TRADE";
  status: "TRAVELLING" | "RETURNING" | "SETTLING" | "PENDING";
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
  // movement-class / transport summary (present on city attack previews)
  routeCrossesWater?: boolean;
  requiredTransportCapacity?: number;
  providedTransportCapacity?: number;
  transportSufficient?: boolean;
  transportShipsShort?: number;
  transportWarning?: string;
}
export interface MovementsSummary { attacksOut: number; incomingThreats: number; returning: number; idleCities: number; }
export interface PlayerMovements { summary: MovementsSummary; movements: Movement[]; }
export interface CityDetail {
  id: number; name: string; capital: boolean; island: string; points: number; race: RaceInfo | null;
  resources: Resources; pop: number; maxPop: number;
  buildings: BuildingDto[];
  queues: { BUILDING: QueueJob[]; BARRACKS: QueueJob[]; HARBOR: QueueJob[] };
  units: UnitDto[]; trainable: Trainable[]; research: ResearchDto[]; movements: MovementDto[];
}
export interface GameState { player: PlayerDto; cities: CitySummary[]; active: CityDetail; }

export interface WorldCity { id: number; slot: number; name: string; points: number; power: number; faction: string; playerId: number | null; owner: string; race: RaceId | null; }
export interface WorldIsland { id: number; name: string; px: number; py: number; cities: WorldCity[]; resource?: boolean; }
export interface WorldPlayer { id: number; name: string; level: number; combatPoints: number; }
export interface WorldData { islands: WorldIsland[]; players: WorldPlayer[]; }
export interface InboxMsg { id: number; from: string; body: string; sentAt: string; read: boolean; }

// --- trade / marketplace + convoy logistics ---
export interface MarketBookRow {
  listingId: number; pricePerBundle: number; bundles: number; seller: string; mine: boolean;
  sourceCityId: number; sourceCity: string; sourceIsland: string;
}
export interface MyListing { listingId: number; resourceType: string; bundles: number; pricePerBundle: number; sourceCity: string; }
export interface TradeConvoyDto {
  id: number; status: "PENDING" | "IN_TRANSIT" | "DELIVERED";
  origin: string; destination: string; originCityId: number; destinationCityId: number;
  cargo: Record<string, number>; departAt: string | null; arriveAt: string | null;
}
export interface DeliveryCity { id: number; name: string; island: string; }
export interface TradeMarket {
  cityId: number; cityName: string;
  marketLevel: number; convoyCapacity: number; maxSimultaneousConvoys: number;
  convoySpeedMinutesPerTile: number; bundleSize: number; gold: number;
  deliveryCities: DeliveryCity[];
  book: Record<string, MarketBookRow[]>;
  myListings: MyListing[];
  convoys: TradeConvoyDto[];
}
export interface BuyPreview {
  filledBundles: number; requestedBundles: number; totalGold: number; affordable: boolean; gold: number;
  marketLevel: number; convoyCapacity: number; maxSimultaneousConvoys: number;
  convoyCount: number; totalDeliveryTime: number;
  perConvoy: { sourceCity: string; units: number; convoys: number; etaSeconds: number }[];
  splitReason: string | null;
}
export interface RankRow { name: string; value: number; sub: string; }

// --- alliances ---
export interface AllianceMember { id: number; name: string; level: number; leader: boolean; }
export interface AllianceForumPostDto { id: number; author: string; body: string; at: string; }
export interface AllianceInviteDto { allianceId: number; tag: string; name: string; invitedBy: string | null; }
export interface AllianceView {
  inAlliance: boolean;
  id?: number; tag?: string; name?: string; isLeader?: boolean;
  members?: AllianceMember[]; pendingInvites?: { name: string }[]; forum?: AllianceForumPostDto[];
  invites?: AllianceInviteDto[];
}

// --- battle reports ---
export type BattleOutcome = "VICTORY" | "DEFEAT" | "DRAW";
export type ReportRole = "ATTACKER" | "DEFENDER";

export interface BattleReportSummary {
  id: number;
  foughtAt: string;
  outcome: BattleOutcome;
  role: ReportRole;
  attackerCityId: number;
  attackerCityName: string;
  defenderCityId: number;
  defenderCityName: string;
  attackerPlayerName: string;
  defenderPlayerName: string | null;
  attackerSent: number;
  attackerLost: number;
  defenderLost: number;
  resourcesStolen: Record<string, number>;
  unread: boolean;
}

export interface BattleReport {
  id: number;
  foughtAt: string;
  outcome: BattleOutcome;
  role: ReportRole;
  attackerPlayerId: number | null;
  attackerPlayerName: string;
  attackerCityId: number;
  attackerCityName: string;
  defenderPlayerId: number | null;
  defenderPlayerName: string | null;
  defenderCityId: number;
  defenderCityName: string;
  attackerTroopsSent: Record<string, number>;
  attackerTroopsLost: Record<string, number>;
  attackerTroopsSurvived: Record<string, number>;
  defenderTroopsPresent: Record<string, number>;
  defenderTroopsLost: Record<string, number>;
  defenderTroopsSurvived: Record<string, number>;
  resourcesStolen: Record<string, number>;
  attackerTotalAttackPower: number;
  defenderTotalDefencePower: number;
  siegeDamage: number;
  attackByElement: Record<string, number>;
  defenseByElement: Record<string, number>;
  heroName: string | null;
  heroLevel: number;
  heroAttackBonusPct: number;
  heroLossReductionPct: number;
  heroSkillUsed: string | null;
  heroXpGained: number;
  heroLeveledTo: number | null;
  heroWounded: boolean;
  unread: boolean;
}

export interface BattleReportPage {
  content: BattleReportSummary[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasMore: boolean;
}

// --- hero ---
export type HeroState = "IDLE" | "MARCHING" | "SETTLING" | "WOUNDED";
export interface HeroSkillDto {
  id: string; unlockLevel: number; cooldownHours: number;
  unlocked: boolean; armed: boolean; availableAt: string | null;
}
export type ItemSlot = "WEAPON" | "ARMOR" | "RELIC" | "PET";
export type ItemRarity = "COMMON" | "RARE" | "EPIC" | "LEGENDARY";
export interface HeroItemDto {
  id: number; name: string; slot: ItemSlot;
  rarity: ItemRarity; buffs: Record<string, number>;
  specialEffects?: { effectType: string; params: Record<string, unknown> }[];
  effectLabels?: string[];
  equipped: boolean; seen?: boolean;
  equippedOn?: string | null;   // hero name if equipped, else null
}
export type HeroKey = "LEO" | "TITANIA";
export interface Hero {
  id: number; heroKey: HeroKey; name: string; race: RaceId; unlocked: boolean; level: number;
  currentXp: number; xpToNextLevel: number; unspentAttributePoints: number;
  attributes: { leadership: number; cunning: number; valor: number };
  state: HeroState;
  stationedCityId: number | null; stationedCityName: string | null;
  activeMovementId: number | null; woundedUntil: string | null; armedSkill: string | null;
  skills: HeroSkillDto[];
  bonuses: {
    attackPct: number; defensePct: number; lootPct: number; travelPct: number; lossReductionPct: number;
    heroXpPct?: number; dropChancePct?: number; skillCooldownPct?: number; woundRecoveryPct?: number; navalTravelPct?: number;
  };
  equipment: { weapon: HeroItemDto | null; armor: HeroItemDto | null; relic: HeroItemDto | null; pet: HeroItemDto | null };
  specialEffects?: string[];   // active special-effect labels across the loadout
}

// --- library / research tree ---
export interface LibraryNode {
  id: string; branch: "WAR" | "WARDS" | "LORE"; tier: number; name: string; effect: string;
  pointCost: number; durationSeconds: number; minLibraryLevel: number; prereqs: string[];
  state: "LOCKED" | "RESEARCHING" | "COMPLETED"; available: boolean; completesAt?: string;
}
export interface LibraryData {
  level: number; maxLevel: number; totalPoints: number; spentPoints: number; availablePoints: number;
  fullTreeCost: number; race: string | null; raceAffinity: string | null; tree: LibraryNode[];
}

// --- missions ---
export type MissionStatus = "LOCKED" | "ACTIVE" | "COMPLETED" | "CLAIMED";
export interface Mission {
  missionId: number; order: number; title: string; description: string;
  objectiveType: string; target: number; progress: number; status: MissionStatus;
  rewards: Record<string, number>; unlocksHeroKey: HeroKey | null;
}
export interface MissionsData { missions: Mission[]; starterDone: number; starterTotal: number; }

// --- resource nodes ---
export type NodeStatus = "UNCLAIMED" | "CONTROLLED" | "CONTESTED";
export type NodeType = "SACRED_GROVE" | "MARBLE_QUARRY" | "WHEAT_FIELD";
export interface ResourceNode {
  id: number; islandId: number; islandName: string; x: number; y: number;
  nodeType: NodeType; producedResource: "WOOD" | "STONE" | "WHEAT";
  level: number; status: NodeStatus;
  controllingPlayerId: number | null; controllingPlayerName: string | null;
  controllingAllianceId: number | null; controllingAllianceName: string | null;
  garrison: Record<string, number>; garrisonPop: number; garrisonCap: number;
  accumulated: number; ratePerHour: number; contestedUntil: string | null; name: string;
}

// --- bandit camp ---
export interface BanditCamp {
  islandId: number; currentLevel: number; maxLevel: number;
  status: "ACTIVE" | "DEFEATED"; respawnAt: string | null;
  defenderTroops?: Record<string, number>;
  rewardType?: "RESOURCES" | "TROOPS" | "MIXED";
  rewardPayload?: Record<string, number>;
  description?: string;
}
/** Bandit raids are dispatched as a troop movement; the outcome arrives later as a Battle Report. */
export interface BanditAttackResult {
  ok: boolean;
  status: "DISPATCHED";
  movementId: number;
  travelSeconds: number;
  arriveAt: string;
}

// --- island boss (resource islands) ---
export interface IslandBoss {
  exists: boolean;
  islandId?: number; name?: string; race?: RaceId; level?: number;
  status?: "ACTIVE" | "DEFEATED"; respawnAt?: string | null;
  defenderTroops?: Record<string, number>;
}
export interface BossAttackResult {
  outcome: "WIN" | "LOSS";
  troopsLost: Record<string, number>;
  reward?: Record<string, any>;
  heroXp?: number;
  status: "ACTIVE" | "DEFEATED";
  respawnAt: string | null;
}
