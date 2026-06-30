// City-view scene data — maps each game building `type` to its art icon and a
// fixed ground point on terrain.svg (viewBox 0 0 1000 760). Coordinates come
// straight from the asset pack's placements.json; the data layer is untouched.
import terrainUrl from "./assets/terrain.svg";
import townHallUrl from "./assets/town-hall.png";
import altarUrl from "./assets/altar.png";
import barracksUrl from "./assets/barracks.png";
import marketUrl from "./assets/market.png";
import libraryUrl from "./assets/library.png";
import lumberUrl from "./assets/lunder_mill.png";
import wheatUrl from "./assets/wheat-farm.svg";
import farmUrl from "./assets/farm.png";
import quarryUrl from "./assets/quarry.svg";
import warehouseUrl from "./assets/warehouse.svg";
import harborUrl from "./assets/harbor.svg";
import watchTowerUrl from "./assets/watch-tower.png";

export const TERRAIN_URL = terrainUrl;
export const SCENE_W = 1000;
export const SCENE_H = 760;

export interface Placement {
  type: string;            // game BuildingType — binds to active.buildings[].type
  name: string;            // pack display name
  icon: string;            // bundled svg url
  tag: string;             // shown as "Level N · {tag}"
  resource: "wood" | "stone" | "wheat" | "food" | null;
  x: number; y: number;    // ground point in scene units
  w: number;               // icon size; base sits on (x, y)
  base?: number;           // vertical anchor override (fraction of h where the building's base sits); default ICON_BASE
}

// imgX = x - w/2, imgY = y - w*0.786  (icon base lands on the ground point)
export const ICON_BASE = 0.786;

export const PLACEMENTS: Placement[] = [
  { type: "SENATE",    name: "Town Hall",   icon: townHallUrl,  tag: "Civic",     resource: null,    x: 570, y: 322, w: 142 },
  { type: "ALTAR",     name: "Altar",       icon: altarUrl,     tag: "Ritual",    resource: null,    x: 480, y: 254, w: 104 },
  { type: "BARRACKS",  name: "Barracks",    icon: barracksUrl,  tag: "Military",  resource: null,    x: 658, y: 256, w: 108 },
  { type: "MARKET",    name: "Market",      icon: marketUrl,    tag: "Trade",     resource: null,    x: 498, y: 392, w: 106 },
  { type: "LIBRARY",   name: "Library",     icon: libraryUrl,   tag: "Knowledge", resource: null,    x: 658, y: 394, w: 104 },
  { type: "TIMBER",    name: "Lumber Mill", icon: lumberUrl,    tag: "Wood",      resource: "wood",  x: 246, y: 336, w: 112 },
  { type: "MINE",      name: "Wheat Farm",  icon: wheatUrl,     tag: "Wheat",     resource: "wheat", x: 296, y: 544, w: 116 },
  { type: "FARM",      name: "Farm",        icon: farmUrl,      tag: "Food",      resource: "food",  x: 432, y: 574, w: 110 },
  { type: "QUARRY",    name: "Quarry",      icon: quarryUrl,    tag: "Stone",     resource: "stone", x: 800, y: 432, w: 112 },
  { type: "WAREHOUSE", name: "Warehouse",   icon: warehouseUrl, tag: "Storage",   resource: null,    x: 758, y: 548, w: 108 },
  { type: "HARBOR",    name: "Harbor",      icon: harborUrl,    tag: "Naval",     resource: null,    x: 606, y: 666, w: 128 },
  { type: "WATCHTOWER",name: "Watchtower",  icon: watchTowerUrl,tag: "Espionage", resource: null,    x: 392, y: 452, w: 124, base: 0.85 },
  { type: "EXTRACTOR", name: "Extractor",   icon: quarryUrl,    tag: "Special",   resource: null,    x: 168, y: 232, w: 104 },
];

export const PLACEMENT_BY_TYPE: Record<string, Placement> =
  Object.fromEntries(PLACEMENTS.map(p => [p.type, p]));
