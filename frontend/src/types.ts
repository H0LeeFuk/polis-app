export interface PlayerDto {
  id: number; username: string; level: number;
  combatPoints: number; combatToNext: number;
  citySlots: number; ownedCities: number; totalPoints: number; gold: number; alliance?: string;
}
export interface CitySummary { id: number; name: string; points: number; capital: boolean; island: string; raceId?: string | null; raceName?: string | null; }

// ---- City Groups (per-player, UI/navigation only) ----
export interface CityGroup { id: number; name: string; icon: string; sortOrder: number; cityIds: number[]; }
export interface CityGroupsView { groups: CityGroup[]; iconChoices: string[]; }
export interface CityOverview {
  id: number; name: string; capital: boolean; island: string; raceName?: string | null;
  groupIds: number[]; underAttack: boolean;
  building?: { type: string; toLevel: number | null } | null;
}
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
export type CombatLayer = "LAND" | "SEA";
export type ShipRole = "TRANSPORT" | "DEFENSE" | "ATTACK";
export interface Trainable {
  type: string; from: string; kind: string; siege: boolean; attackElement: Element | null;
  atk: number; defFire: number; defWind: number; defEarth: number; defWater: number;
  speed: number; pop: number; carry: number; cost: number[]; seconds: number; unlocked: boolean;
  elite: boolean; costSpecial: number; specialResource: ResourceId | null;
  movementClass: MovementClass; transportCapacity: number; requiresTransport: boolean;
  combatLayer: CombatLayer; shipRole: ShipRole | null;
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
  type: "ATTACK" | "RETURN" | "COLONY" | "SUPPORT" | "SETTLE" | "TRADE" | "SPY";
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
  combatLayer?: "SEA" | "LAND" | "MIXED" | null;   // detected attack layer
}
export interface Progression {
  level: number; maxLevel: number;
  culturePoints: number; cultureForNextLevel: number | null; culturePointsTotal: number;
  combatPoints: number; citiesOwned: number; maxCities: number; cap: number; atMax: boolean;
}
export interface AltarState {
  altarLevel: number; combatPoints: number;
  resourceCost: number; combatCost: number; cultureReward: number; durationSeconds: number;
  canAffordResources: boolean; canAffordCombat: boolean;
  running: { festivalType: string; fuelType: string; completesAt: string; culturePointsReward: number }[];
  progression: Progression;
}
export interface MovementsSummary { attacksOut: number; incomingThreats: number; returning: number; idleCities: number; }
export interface PlayerMovements { summary: MovementsSummary; movements: Movement[]; }
export interface CityDetail {
  id: number; name: string; capital: boolean; island: string; points: number; race: RaceInfo | null;
  conqueredPendingRace?: boolean;
  resources: Resources; pop: number; maxPop: number;
  buildings: BuildingDto[];
  queues: { BUILDING: QueueJob[]; BARRACKS: QueueJob[]; HARBOR: QueueJob[] };
  units: UnitDto[]; trainable: Trainable[]; research: ResearchDto[]; movements: MovementDto[];
  cityGuardEnabled?: boolean; cityGuardReadyAt?: string | null;
}
export interface GameState { player: PlayerDto; cities: CitySummary[]; active: CityDetail; }

export interface WorldCity { id: number; slot: number; name: string; points: number; power: number; faction: string; playerId: number | null; owner: string; race: RaceId | null; }
export interface WorldIsland { id: number; name: string; px: number; py: number; cities: WorldCity[]; resource?: boolean; tier?: number; spawnable?: boolean; clusterId?: number; resourceLevel?: number; controlEmblems?: string[]; }
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
export interface RankRow { name: string; value: number; sub: string; playerId?: number | null; }

export interface PublicProfile {
  id: number;
  username: string;
  level: number;
  totalPoints: number;
  combatPoints: number;
  alliance: string | null;
  pointsRank: number | null;
  combatRank: number | null;
  cities: { id: number; name: string; points: number; raceName: string | null; capital: boolean }[];
}

// --- alliances ---
export interface AllianceMember { id: number; name: string; level: number; leader: boolean; }
export interface AllianceForumPostDto { id: number; author: string; body: string; at: string; }
export interface AllianceInviteDto { allianceId: number; tag: string; name: string; invitedBy: string | null; }
export interface AllianceView {
  inAlliance: boolean;
  id?: number; tag?: string; name?: string; isLeader?: boolean;
  emblem?: string; emblemChoices?: string[];
  members?: AllianceMember[]; pendingInvites?: { name: string }[]; forum?: AllianceForumPostDto[];
  invites?: AllianceInviteDto[];
}

export interface TierProgressRow { tier: number; unlocksTier: number; bossKills: number; bossKillsRequired: number; controlHours: number; controlHoursRequired: number; unlocked: boolean; }
export interface TierProgress { tier1: TierProgressRow; tier2: TierProgressRow; tier2Unlocked: boolean; tier3Unlocked: boolean; }

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
  siegeStarted: boolean;
}

export interface BattleReport {
  id: number;
  foughtAt: string;
  outcome: BattleOutcome;
  role: ReportRole;
  combatLayer: CombatLayer;
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
  combatPointsEarned: number;
  combatPointsReason: string | null;
  heroName: string | null;
  heroLevel: number;
  heroAttackBonusPct: number;
  heroLossReductionPct: number;
  heroSkillUsed: string | null;
  heroXpGained: number;
  heroLeveledTo: number | null;
  heroWounded: boolean;
  unread: boolean;
  siegeStarted: boolean;
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
  pointCost: number; durationSeconds: number; minLibraryLevel: number; tierOk: boolean;
  state: "LOCKED" | "RESEARCHING" | "COMPLETED"; available: boolean; completesAt?: string;
}
export interface LibraryData {
  level: number; maxLevel: number; totalPoints: number; spentPoints: number; availablePoints: number;
  fullTreeCost: number; race: string | null; raceAffinity: string | null; tree: LibraryNode[];
}

// --- siege & conquest ---
export interface SiegeData {
  id: number;
  cityId: number;
  cityName: string;
  status: "ACTIVE" | "SUCCEEDED" | "BROKEN";
  startedAt: string | null;
  endsAt: string | null;
  besiegingPlayerId: number;
  besiegingPlayer: string | null;
  defenderPlayerId: number | null;
  besiegingTroops: Record<string, number>;
  besiegingShips: Record<string, number>;
  troopsRemaining: number;
  shipsRemaining: number;
  heroName: string | null;
  heroLevel: number | null;
  isBesieger: boolean;
  isDefender: boolean;
  isAllianceSiege: boolean;
  canReinforce: boolean;
  canBreak: boolean;
  canWithdraw: boolean;
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
export interface NodeHolder { playerId: number; playerName: string; troops: Record<string, number>; pop: number; sharePct: number; }
export interface ResourceNode {
  id: number; islandId: number; islandName: string; tier: number; x: number; y: number;
  nodeType: NodeType; producedResource: "WOOD" | "STONE" | "WHEAT";
  level: number; status: NodeStatus;
  controllingPlayerId: number | null;
  controllingAllianceId: number | null; controllingAllianceName: string | null; controllingAllianceEmblem: string | null;
  garrisonPop: number; garrisonCap: number; ratePerHour: number;
  controlSince: string | null;
  holders: NodeHolder[]; myPop: number; mySharePct: number; viewerControls: boolean; name: string;
}

// --- endgame: Wonders of the Aegean ---
export type WonderStatus = "DORMANT" | "ACTIVE" | "CONTROLLED" | "CONTESTED";
export type WorldPhase = "GROWTH" | "ENDGAME" | "FINISHED";
export interface WonderDto {
  id: number; islandId: number; islandName: string; x: number; y: number;
  kind: "LIGHTHOUSE" | "COLOSSUS" | "SANCTUM"; name: string;
  level: number; maxLevel: number; status: WonderStatus;
  controllingAllianceId: number | null; controllingAllianceName: string | null;
  controllingPlayerName: string | null;
  garrison: Record<string, number>; garrisonPop: number;
  nextLevelCost: number; investedWood: number; investedStone: number; investedWheat: number;
  contestedUntil: string | null;
}
export interface WorldEndgame {
  phase: WorldPhase;
  endgameStartedAt: string | null;
  worldAgeDays: number; cityCount: number; cityThreshold: number; daysThreshold: number;
  consolidationAllianceId: number | null; consolidationAllianceName: string | null;
  consolidationSecondsLeft: number; consolidationTotalSeconds: number;
  winnerAllianceId: number | null; winnerAllianceName: string | null;
  wonders: WonderDto[];
}
export interface WonderLeader {
  allianceId: number; allianceName: string; wondersHeld: number; totalLevels: number;
}
export interface ColossusDamageRow {
  rank: number; allianceId: number; allianceName: string; allianceTag: string;
  damage: number; sharePct: number;
}
export interface ColossusDto {
  id: number; name: string; tier: number; status: "ROAMING" | "DEFEATED" | "DESPAWNED";
  maxHealth: number; currentHealth: number; x: number; y: number; despawnAt: string;
  attackElement: Element;
  defense: Record<string, number>;   // FIRE/WIND/EARTH/WATER -> defence value
  totalDamage: number; myAllianceDamage: number; myAllianceSharePct: number;
  rewardPoolPerResource: number;
  leaderboard?: ColossusDamageRow[];
}
// --- espionage ---
export interface WatchtowerDto {
  cityId: number; level: number; maxLevel: number;
  spySuccessChance: number; spyDefenseChance: number;   // percentages
  cost: number; seconds: number;
}
export interface SpyIntel {
  capturedAt: string;
  troops: Record<string, number> | null;
  resources: Record<string, number> | null;
  buildings: Record<string, number> | null;
}
export interface SpyReportDto {
  id: number; targetCityId: number; targetCityName: string;
  outcome: "SUCCESS" | "CAUGHT"; capturedAt: string;
  troops: Record<string, number> | null;
  resources: Record<string, number> | null;
  buildings: Record<string, number> | null;
}
export interface SpyAlertDto {
  id: number; spyingPlayerName: string; targetCityName: string; caughtAt: string;
}

// --- bandit tower (account-wide 100-level PvE climb) ---
export interface BanditTowerReward {
  headline: string;
  resources: Record<string, number>;
  troops: Record<string, number>;
  itemRarity: string | null;
}
export interface BanditTowerState {
  currentLevel: number;
  highestCleared: number;
  maxLevel: number;
  complete: boolean;
  isMilestone?: boolean;
  resistedElement?: string;
  defendersFull?: Record<string, number>;
  defendersRemaining?: Record<string, number>;
  reward?: BanditTowerReward;
  nextMilestone?: BanditTowerReward & { level: number };
}
export interface BanditTowerLevelRow {
  level: number;
  status: "CLEARED" | "CURRENT" | "LOCKED";
  isMilestone: boolean;
  resistedElement: string;
  reward: BanditTowerReward;
}
export interface BanditTowerAttackResult {
  level: number;
  outcome: "CLEARED" | "REPELLED";
  troopsLost: Record<string, number>;
  defendersDefeated: Record<string, number>;
  defendersRemaining: Record<string, number>;
  cleared: boolean;
  reward?: { resources?: Record<string, number>; troops?: Record<string, number>;
    item?: { name: string; rarity: string; slot: string; buffs: Record<string, number> } };
  heroXp?: number;
  towerComplete?: boolean;
  currentLevel: number;
  highestCleared: number;
}

// --- island boss (resource islands) — Colossus-style shared HP, per-player rewards ---
export interface IslandBossDamageRow { rank: number; playerId: number; playerName: string; damage: number; sharePct: number; }
export interface IslandBoss {
  exists: boolean;
  id?: number; islandId?: number; name?: string; race?: RaceId; level?: number; tier?: number;
  status?: "ACTIVE" | "DEFEATED"; respawnAt?: string | null;
  maxHealth?: number; currentHealth?: number;
  attackElement?: string; defense?: Record<string, number>;
  totalDamage?: number; myDamage?: number; mySharePct?: number; rewardPoolPerResource?: number;
  leaderboard?: IslandBossDamageRow[];
}
export interface BossDispatch { ok: boolean; status: string; movementId: number; travelSeconds: number; arriveAt: string; }
