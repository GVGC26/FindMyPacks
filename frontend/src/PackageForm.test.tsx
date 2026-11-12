import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import PackageForm, { validatePackage } from "./PackageForm";
import type { PackageValues } from "./PackageForm";

const VALID: PackageValues = {
  trackingCode: "BR12345678",
  customer: "Maria Silva",
  destination: "Fortaleza",
  carrier: "CORREIOS",
  status: "POSTED",
  postedAt: "2020-01-15",
};

const csrfResponse = () => new Response(JSON.stringify({ token: "csrf-token" }), { status: 200 });
const jsonResponse = (body: unknown, status: number) => new Response(JSON.stringify(body), { status });

function fillForm(values: PackageValues) {
  fireEvent.change(screen.getByLabelText("Código de rastreio"), { target: { value: values.trackingCode } });
  fireEvent.change(screen.getByLabelText("Cliente"), { target: { value: values.customer } });
  fireEvent.change(screen.getByLabelText("Destino"), { target: { value: values.destination } });
  fireEvent.change(screen.getByLabelText("Transportadora"), { target: { value: values.carrier } });
  fireEvent.change(screen.getByLabelText("Status"), { target: { value: values.status } });
  fireEvent.change(screen.getByLabelText("Data de postagem"), { target: { value: values.postedAt } });
}

describe("validatePackage", () => {
  it("accepts valid values", () => {
    expect(validatePackage(VALID)).toEqual({});
  });

  it("rejects invalid values field by field", () => {
    const errors = validatePackage({
      trackingCode: "abc",
      customer: "A",
      destination: "",
      carrier: "UNKNOWN",
      status: "",
      postedAt: "",
    });

    expect(Object.keys(errors).sort()).toEqual(
      ["carrier", "customer", "destination", "postedAt", "status", "trackingCode"],
    );
  });

  it("rejects future posted dates", () => {
    const errors = validatePackage({ ...VALID, postedAt: "2999-01-01" });

    expect(errors.postedAt).toMatch(/futura/i);
  });
});

describe("PackageForm", () => {
  afterEach(() => {
    cleanup();
  });

  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("shows validation errors and does not call the API when the form is invalid", () => {
    const fetchMock = vi.spyOn(globalThis, "fetch");
    render(<PackageForm />);

    fireEvent.click(screen.getByRole("button", { name: /cadastrar pacote/i }));

    expect(screen.getAllByRole("alert").length).toBeGreaterThan(0);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("creates a package and then edits the saved record", async () => {
    const saved = { id: 7, ...VALID };
    const fetchMock = vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(jsonResponse(saved, 201))
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(jsonResponse({ ...saved, customer: "João Souza" }, 200));
    render(<PackageForm />);

    fillForm(VALID);
    fireEvent.click(screen.getByRole("button", { name: /cadastrar pacote/i }));
    expect(await screen.findByText("Pacote cadastrado.")).toBeInTheDocument();
    expect(fetchMock).toHaveBeenNthCalledWith(2, "/api/web/records", expect.objectContaining({
      method: "POST",
      headers: expect.objectContaining({ "X-XSRF-TOKEN": "csrf-token" }),
    }));

    fireEvent.change(screen.getByLabelText("Cliente"), { target: { value: "João Souza" } });
    fireEvent.click(screen.getByRole("button", { name: /salvar alterações/i }));
    expect(await screen.findByText("Pacote atualizado.")).toBeInTheDocument();
    expect(fetchMock).toHaveBeenNthCalledWith(4, "/api/web/records/7", expect.objectContaining({
      method: "PUT",
    }));
  });

  it("maps a duplicate tracking code (409) to the field", async () => {
    vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(jsonResponse({ message: "Tracking code already registered" }, 409));
    render(<PackageForm />);

    fillForm(VALID);
    fireEvent.click(screen.getByRole("button", { name: /cadastrar pacote/i }));

    await waitFor(() =>
      expect(screen.getByText("Este código de rastreio já está cadastrado.")).toBeInTheDocument(),
    );
  });

  it("shows field errors returned by the API (400)", async () => {
    vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(jsonResponse({ message: "Validation failed", fieldErrors: { customer: "Invalid customer" } }, 400));
    render(<PackageForm />);

    fillForm(VALID);
    fireEvent.click(screen.getByRole("button", { name: /cadastrar pacote/i }));

    expect(await screen.findByText("Invalid customer")).toBeInTheDocument();
  });

  it("asks the user to sign in again when the session expired (401)", async () => {
    vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(csrfResponse())
      .mockResolvedValueOnce(new Response("", { status: 401 }));
    render(<PackageForm />);

    fillForm(VALID);
    fireEvent.click(screen.getByRole("button", { name: /cadastrar pacote/i }));

    expect(await screen.findByText("Sua sessão expirou. Entre novamente.")).toBeInTheDocument();
  });

  it("loads an initial record for editing and can start a new registration", () => {
    render(<PackageForm initialRecord={{ id: 3, ...VALID }} />);

    expect(screen.getByRole("heading", { name: /editar pacote #3/i })).toBeInTheDocument();
    expect(screen.getByLabelText("Cliente")).toHaveValue("Maria Silva");

    fireEvent.click(screen.getByRole("button", { name: /novo cadastro/i }));

    expect(screen.getByRole("heading", { name: /cadastrar pacote/i })).toBeInTheDocument();
    expect(screen.getByLabelText("Cliente")).toHaveValue("");
  });
});
