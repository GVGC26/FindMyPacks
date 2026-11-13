import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import PackageList from "./PackageList";
import type { PackageRecord } from "./PackageForm";

const records: PackageRecord[] = Array.from({ length: 6 }, (_, index) => ({
  id: index + 1,
  trackingCode: `BR1234567${index}`,
  customer: `Cliente ${index + 1}`,
  destination: "Fortaleza",
  carrier: "CORREIOS",
  status: "POSTED",
  postedAt: `2026-01-0${index + 1}`,
}));

function pageResponse(content: PackageRecord[], number = 0, totalElements = content.length) {
  return new Response(JSON.stringify({
    content,
    number,
    size: 5,
    totalElements,
    totalPages: Math.ceil(totalElements / 5),
  }), { status: 200 });
}

describe("PackageList", () => {
  afterEach(() => cleanup());

  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(window, "confirm").mockReturnValue(true);
  });

  it("loads five packages on the first page and displays the required fields", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockResolvedValue(pageResponse(records.slice(0, 5), 0, 6));
    render(<PackageList onEdit={vi.fn()} refreshKey={0} />);

    expect(await screen.findByText("BR12345670")).toBeInTheDocument();
    expect(screen.getByText("Cliente 1")).toBeInTheDocument();
    expect(screen.getAllByText("Fortaleza")).toHaveLength(5);
    expect(screen.getByText("2026-01-01")).toBeInTheDocument();
    expect(screen.getAllByRole("row")).toHaveLength(6);
    expect(fetchMock).toHaveBeenCalledWith("/api/web/records?page=0&size=5&sort=postedAt,desc");
    expect(screen.getByText("Página 1 de 2")).toBeInTheDocument();
  });

  it("changes sort order and loads the next page", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockImplementation(async (input) => {
      const url = String(input);
      if (url.includes("page=1")) return pageResponse([records[5]], 1, 6);
      return pageResponse(records.slice(0, 5), 0, 6);
    });
    render(<PackageList onEdit={vi.fn()} refreshKey={0} />);
    await screen.findByText("BR12345670");

    fireEvent.click(screen.getByRole("button", { name: "Cliente" }));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith("/api/web/records?page=0&size=5&sort=customer,asc"));

    fireEvent.click(screen.getByRole("button", { name: "Próxima" }));
    expect(await screen.findByText("BR12345675")).toBeInTheDocument();
    expect(screen.getByText("Página 2 de 2")).toBeInTheDocument();
    expect(fetchMock).toHaveBeenLastCalledWith("/api/web/records?page=1&size=5&sort=customer,asc");
  });

  it("passes a chosen package to the edit action", async () => {
    const onEdit = vi.fn();
    vi.spyOn(globalThis, "fetch").mockResolvedValue(pageResponse(records.slice(0, 5), 0, 6));
    render(<PackageList onEdit={onEdit} refreshKey={0} />);
    await screen.findByText("BR12345670");

    fireEvent.click(screen.getAllByRole("button", { name: "Editar" })[0]);

    expect(onEdit).toHaveBeenCalledWith(records[0]);
  });

  it("deletes selected packages with a CSRF token and reloads the list", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(pageResponse(records.slice(0, 5), 0, 6))
      .mockResolvedValueOnce(new Response(JSON.stringify({ token: "csrf-token" }), { status: 200 }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(pageResponse(records.slice(1, 5), 0, 5));
    render(<PackageList onEdit={vi.fn()} refreshKey={0} />);
    await screen.findByText("BR12345670");

    fireEvent.click(screen.getByRole("checkbox", { name: "Selecionar pacote BR12345670" }));
    fireEvent.click(screen.getByRole("button", { name: "Excluir selecionados (1)" }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(4));
    expect(window.confirm).toHaveBeenCalledWith("Excluir 1 pacote(s) selecionado(s)?");
    expect(fetchMock).toHaveBeenNthCalledWith(2, "/api/web/csrf");
    expect(fetchMock).toHaveBeenNthCalledWith(3, "/api/web/records/1", {
      method: "DELETE",
      headers: { "X-XSRF-TOKEN": "csrf-token" },
    });
    expect(await screen.findByRole("status")).toHaveTextContent("Pacote(s) excluído(s).");
    expect(screen.queryByText("BR12345670")).not.toBeInTheDocument();
  });

  it("shows an authentication message when listing requires a new login", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(new Response(null, { status: 401 }));
    render(<PackageList onEdit={vi.fn()} refreshKey={0} />);

    expect(await screen.findByRole("alert")).toHaveTextContent("Sua sessão expirou. Entre novamente.");
  });
});
