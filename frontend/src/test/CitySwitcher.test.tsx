import { describe, it, expect, vi } from "vitest";
import { render, screen, fireEvent, within } from "@testing-library/react";
import { CitySwitcher } from "../components/CityGroups";
import type { CityGroup, CityOverview, CitySummary } from "../types";

const groups: CityGroup[] = [
  { id: 1, name: "North", icon: "⚔", sortOrder: 0, cityIds: [101] },
  { id: 2, name: "South", icon: "🛡", sortOrder: 1, cityIds: [102] },
];
const overview: CityOverview[] = [
  { id: 101, name: "Alpha", capital: true, island: "Keros", groupIds: [1], underAttack: false, building: null },
  { id: 102, name: "Beta", capital: false, island: "Naxos", groupIds: [2], underAttack: true, building: { type: "TIMBER", toLevel: 3 } },
  { id: 103, name: "Gamma", capital: false, island: "Delos", groupIds: [], underAttack: false, building: null },
];
const cities: CitySummary[] = overview.map(c => ({ id: c.id, name: c.name, points: 0, capital: c.capital, island: c.island }));

function renderSwitcher(onSwitch = vi.fn()) {
  const utils = render(
    <CitySwitcher cities={cities} groups={groups} overview={overview}
      activeId={101} activeName="Alpha" onSwitch={onSwitch}
      onStartRename={() => {}} onManage={() => {}} />
  );
  return { ...utils, onSwitch };
}

describe("CitySwitcher", () => {
  it("panel is closed until the name is clicked", () => {
    const { container } = renderSwitcher();
    expect(container.querySelector(".cg-panel")).toBeNull();
    fireEvent.click(screen.getByTitle("Show all cities"));
    expect(container.querySelector(".cg-panel")).not.toBeNull();
  });

  it("lays cities out by group with an Ungrouped section", () => {
    const { container } = renderSwitcher();
    fireEvent.click(screen.getByTitle("Show all cities"));
    const panel = container.querySelector(".cg-panel") as HTMLElement;
    // ungrouped city (Gamma) sits under the Ungrouped section
    const ungrouped = within(panel).getByText("Ungrouped").closest(".cg-section") as HTMLElement;
    expect(within(ungrouped).getByText("Gamma")).toBeInTheDocument();
    // grouped cities are present too
    expect(within(panel).getByText(/Alpha/)).toBeInTheDocument();
    expect(within(panel).getByText("Beta")).toBeInTheDocument();
  });

  it("search filters cities by name", () => {
    const { container } = renderSwitcher();
    fireEvent.click(screen.getByTitle("Show all cities"));
    const panel = container.querySelector(".cg-panel") as HTMLElement;
    fireEvent.change(within(panel).getByPlaceholderText("Search cities…"), { target: { value: "Beta" } });
    expect(within(panel).getByText("Beta")).toBeInTheDocument();
    expect(within(panel).queryByText(/Alpha/)).toBeNull();
    expect(within(panel).queryByText("Gamma")).toBeNull();
  });

  it("clicking a city switches to it", () => {
    const { container, onSwitch } = renderSwitcher();
    fireEvent.click(screen.getByTitle("Show all cities"));
    const panel = container.querySelector(".cg-panel") as HTMLElement;
    fireEvent.click(within(panel).getByText("Beta"));
    expect(onSwitch).toHaveBeenCalledWith(102);
  });

  it("prev/next arrows cycle through cities in order (wrapping)", () => {
    const { onSwitch } = renderSwitcher();
    fireEvent.click(screen.getByTitle("Next city"));       // 101 -> 102
    expect(onSwitch).toHaveBeenLastCalledWith(102);
    fireEvent.click(screen.getByTitle("Previous city"));   // 101 -> wraps to 103
    expect(onSwitch).toHaveBeenLastCalledWith(103);
  });
});
