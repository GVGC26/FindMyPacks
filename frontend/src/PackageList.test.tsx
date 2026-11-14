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
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

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

  it("submits customer, status, and carrier filters to the search API", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockImplementation(async (input) => {
      const url = String(input);
      return url.includes("/search?") ? pageResponse([records[0]]) : pageResponse(records.slice(0, 5), 0, 6);
    });
    render(<PackageList onEdit={vi.fn()} refreshKey={0} />);
    await screen.findByText("BR12345670");

    fireEvent.change(screen.getByRole("textbox", { name: "Filtrar por cliente" }), { target: { value: "Maria Silva" } });
    fireEvent.change(screen.getByRole("combobox", { name: "Filtrar por status" }), { target: { value: "IN_TRANSIT" } });
    fireEvent.change(screen.getByRole("combobox", { name: "Filtrar por transportadora" }), { target: { value: "JADLOG" } });
    fireEvent.click(screen.getByRole("button", { name: "Buscar" }));

    expect(await screen.findByText("BR12345670")).toBeInTheDocument();
    await waitFor(() => expect(fetchMock).toHaveBeenLastCalledWith(
      "/api/web/records/search?page=0&size=5&sort=postedAt,desc&customer=Maria+Silva&status=IN_TRANSIT&carrier=JADLOG",
    ));
  });

  it("clears filters and restores the complete listing", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockImplementation(async (input) => {
      const url = String(input);
      return url.includes("/search?") ? pageResponse([records[0]]) : pageResponse(records.slice(0, 5), 0, 6);
    });
    render(<PackageList onEdit={vi.fn()} refreshKey={0} />);
    await screen.findByText("BR12345670");
    fireEvent.change(screen.getByRole("textbox", { name: "Filtrar por cliente" }), { target: { value: "Maria" } });
    fireEvent.click(screen.getByRole("button", { name: "Buscar" }));
    await waitFor(() => expect(fetchMock).toHaveBeenLastCalledWith(expect.stringContaining("/api/web/records/search?")));

    fireEvent.click(screen.getByRole("button", { name: "Limpar filtros" }));

    await waitFor(() => expect(fetchMock).toHaveBeenLastCalledWith("/api/web/records?page=0&size=5&sort=postedAt,desc"));
    expect(screen.getByRole("textbox", { name: "Filtrar por cliente" })).toHaveValue("");
    expect(screen.getByRole("combobox", { name: "Filtrar por status" })).toHaveValue("");
    expect(screen.getByRole("combobox", { name: "Filtrar por transportadora" })).toHaveValue("");
    expect(screen.getByText("Página 1 de 2")).toBeInTheDocument();
  });

  it("shows an empty state when search returns no packages", async () => {
    vi.spyOn(globalThis, "fetch").mockImplementation(async () => pageResponse([], 0, 0));
    render(<PackageList onEdit={vi.fn()} refreshKey={0} />);

    expect(await screen.findByText("Nenhum pacote cadastrado.")).toBeInTheDocument();
    fireEvent.change(screen.getByRole("textbox", { name: "Filtrar por cliente" }), { target: { value: "Inexistente" } });
    fireEvent.click(screen.getByRole("button", { name: "Buscar" }));

    expect(await screen.findByText("Nenhum pacote encontrado.")).toBeInTheDocument();
  });

  it("exports all packages with the default sort and triggers a CSV download", async () => {
    const createObjectURL = vi.fn((_blob: Blob) => "blob:packages");
    const revokeObjectURL = vi.fn();
    vi.stubGlobal("URL", { createObjectURL, revokeObjectURL });
    const clickLink = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
    const fetchMock = vi.spyOn(globalThis, "fetch").mockImplementation(async (input) =>
      String(input).includes("/export?")
        ? new Response("trackingCode,customer", { status: 200 })
        : pageResponse(records.slice(0, 5), 0, 6));
    render(<PackageList onEdit={vi.fn()} refreshKey={0} />);
    await screen.findByText("BR12345670");

    fireEvent.click(screen.getByRole("button", { name: "Exportar CSV" }));

    expect(await screen.findByRole("status")).toHaveTextContent("Pacotes exportados com sucesso.");
    expect(fetchMock).toHaveBeenLastCalledWith("/api/web/records/export?sort=postedAt,desc");
    expect(createObjectURL).toHaveBeenCalledOnce();
    expect(createObjectURL.mock.calls[0][0].size).toBe(21);
    expect(clickLink).toHaveBeenCalledOnce();
    expect(revokeObjectURL).toHaveBeenCalledWith("blob:packages");
  });

  it("exports using applied filters and current sort instead of draft filters", async () => {
    vi.stubGlobal("URL", { createObjectURL: vi.fn(() => "blob:packages"), revokeObjectURL: vi.fn() });
    vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {});
    const fetchMock = vi.spyOn(globalThis, "fetch").mockImplementation(async (input) =>
      String(input).includes("/export?")
        ? new Response("trackingCode,customer", { status: 200 })
        : pageResponse(records.slice(0, 5), 0, 6));
    render(<PackageList onEdit={vi.fn()} refreshKey={0} />);
    await screen.findByText("BR12345670");
    fireEvent.change(screen.getByRole("textbox", { name: "Filtrar por cliente" }), { target: { value: "Ana Silva" } });
    fireEvent.click(screen.getByRole("button", { name: "Buscar" }));
    await waitFor(() => expect(fetchMock).toHaveBeenLastCalledWith(expect.stringContaining("/search?")));
    fireEvent.change(screen.getByRole("textbox", { name: "Filtrar por cliente" }), { target: { value: "Rascunho" } });
    fireEvent.click(screen.getByRole("button", { name: "Cliente" }));
    await waitFor(() => expect(fetchMock).toHaveBeenLastCalledWith(expect.stringContaining("sort=customer,asc")));

    fireEvent.click(screen.getByRole("button", { name: "Exportar CSV" }));

    await waitFor(() => expect(fetchMock).toHaveBeenLastCalledWith(
      "/api/web/records/export?sort=customer,asc&customer=Ana+Silva",
    ));
  });

  it("shows the session expiration message when export returns 401", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch").mockImplementation(async (input) =>
      String(input).includes("/export?")
        ? new Response(null, { status: 401 })
        : pageResponse(records.slice(0, 5), 0, 6));
    render(<PackageList onEdit={vi.fn()} refreshKey={0} />);
    await screen.findByText("BR12345670");

    fireEvent.click(screen.getByRole("button", { name: "Exportar CSV" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Sua sessão expirou. Entre novamente.");
    expect(fetchMock).toHaveBeenLastCalledWith("/api/web/records/export?sort=postedAt,desc");
  });
});
