import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import App from "./App";

describe("home and login", () => {
  afterEach(() => {
    cleanup();
  });

  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it("shows the homepage and login form", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(new Response(null, { status: 401 }));
    render(<App />);

    expect(await screen.findByRole("heading", { name: /encontre espaço/i })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: /acesse sua conta/i })).toBeInTheDocument();
    expect(screen.getByLabelText("Usuário")).toBeInTheDocument();
    expect(screen.getByLabelText("Senha")).toBeInTheDocument();
  });

  it("logs in with the CSRF token and reports the authenticated user", async () => {
    const fetchMock = vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ token: "csrf-token" }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ username: "findmypacksadmin", role: "ADMIN" }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        byStatus: [],
        byCarrier: [],
        postedPerDay: [],
      }), { status: 200 }));
    render(<App />);

    await screen.findByLabelText("Usuário");
    fireEvent.change(screen.getByLabelText("Usuário"), { target: { value: "findmypacksadmin" } });
    fireEvent.change(screen.getByLabelText("Senha"), { target: { value: "findmypacksadmin" } });
    fireEvent.click(screen.getByRole("button", { name: /entrar/i }));

    await waitFor(() => expect(screen.getByRole("status")).toHaveTextContent("findmypacksadmin"));
    expect(fetchMock).toHaveBeenNthCalledWith(3, "/api/web/login", expect.objectContaining({
      method: "POST",
      headers: expect.objectContaining({ "X-XSRF-TOKEN": "csrf-token" }),
    }));
  });

  it("shows a clear message when credentials are rejected", async () => {
    vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ token: "csrf-token" }), { status: 200 }))
      .mockResolvedValueOnce(new Response("", { status: 401 }));
    render(<App />);

    await screen.findByLabelText("Usuário");
    fireEvent.change(screen.getByLabelText("Usuário"), { target: { value: "unknown" } });
    fireEvent.change(screen.getByLabelText("Senha"), { target: { value: "wrong" } });
    fireEvent.click(screen.getByRole("button", { name: /entrar/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Usuário ou senha inválidos.");
  });

  it("restores the authenticated session after a page reload", async () => {
    vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(new Response(JSON.stringify({ username: "findmypacksadmin", role: "ADMIN" }), { status: 200 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({
        byStatus: [],
        byCarrier: [],
        postedPerDay: [],
      }), { status: 200 }));
    render(<App />);

    expect(await screen.findByText("Bem-vindo, findmypacksadmin.")).toBeInTheDocument();
    expect(await screen.findByRole("heading", { name: "Dashboard de pacotes" })).toBeInTheDocument();
  });
});
