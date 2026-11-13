import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import Dashboard from "./Dashboard";

function dashboardResponse(statusCount: number) {
  const today = new Date();
  const postedPerDay = Array.from({ length: 7 }, (_, index) => {
    const date = new Date(today.getFullYear(), today.getMonth(), today.getDate() - 6 + index);
    const isoDate = [
      date.getFullYear(),
      String(date.getMonth() + 1).padStart(2, "0"),
      String(date.getDate()).padStart(2, "0"),
    ].join("-");
    return { date: isoDate, count: index === 6 ? statusCount : 0 };
  });

  return new Response(JSON.stringify({
    byStatus: [{ label: "POSTED", count: statusCount }],
    byCarrier: [{ label: "CORREIOS", count: statusCount }],
    postedPerDay,
  }), { status: 200 });
}

describe("Dashboard", () => {
  afterEach(() => {
    cleanup();
  });

  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("loads the three charts from the dashboard API", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(dashboardResponse(3));
    render(<Dashboard />);

    const statusChart = await screen.findByRole("region", { name: "Pacotes por status" });
    expect(screen.getByRole("region", { name: "Pacotes por transportadora" })).toBeInTheDocument();
    expect(screen.getByRole("region", { name: "Postagens nos últimos 7 dias" })).toBeInTheDocument();
    expect(within(statusChart).getByText("Postado")).toBeInTheDocument();
    expect(within(statusChart).getByText("3")).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith("/api/web/dashboard", expect.objectContaining({
      signal: expect.any(AbortSignal),
    }));
  });

  it("reloads fresh values when the user refreshes the dashboard", async () => {
    vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(dashboardResponse(1))
      .mockResolvedValueOnce(dashboardResponse(4));
    render(<Dashboard />);
    const statusChart = await screen.findByRole("region", { name: "Pacotes por status" });
    expect(within(statusChart).getByText("1")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Atualizar" }));

    await waitFor(() => {
      const updatedStatusChart = screen.getByRole("region", { name: "Pacotes por status" });
      expect(within(updatedStatusChart).getByText("4")).toBeInTheDocument();
    });
  });

  it("shows a retry action when the API is unavailable", async () => {
    vi.spyOn(globalThis, "fetch")
      .mockRejectedValueOnce(new Error("network error"))
      .mockResolvedValueOnce(dashboardResponse(2));
    render(<Dashboard />);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "network error",
    );
    fireEvent.click(screen.getByRole("button", { name: "Tentar novamente" }));

    expect(await screen.findByRole("region", { name: "Pacotes por status" })).toBeInTheDocument();
  });
});
