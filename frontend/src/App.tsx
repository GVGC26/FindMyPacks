import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import Dashboard from "./Dashboard";
import PackageForm from "./PackageForm";

type LoginResult = {
  username: string;
  role: string;
};

type CsrfResponse = {
  token: string;
};

export default function App() {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [signedInAs, setSignedInAs] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [dashboardRefreshKey, setDashboardRefreshKey] = useState(0);
  const [isCheckingSession, setIsCheckingSession] = useState(true);

  useEffect(() => {
    const controller = new AbortController();

    async function restoreSession() {
      try {
        const response = await fetch("/api/web/session", { signal: controller.signal });
        if (response.status === 401) {
          return;
        }
        if (!response.ok) {
          throw new Error("Não foi possível verificar sua sessão.");
        }

        const session = (await response.json()) as LoginResult;
        if (!controller.signal.aborted) {
          setSignedInAs(session.username);
        }
      } catch (sessionError) {
        if (!controller.signal.aborted) {
          setError(
            sessionError instanceof Error
              ? sessionError.message
              : "Não foi possível verificar sua sessão.",
          );
        }
      } finally {
        if (!controller.signal.aborted) {
          setIsCheckingSession(false);
        }
      }
    }

    void restoreSession();
    return () => controller.abort();
  }, []);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setIsSubmitting(true);

    try {
      const csrfResponse = await fetch("/api/web/csrf");
      if (!csrfResponse.ok) {
        throw new Error("Não foi possível iniciar a sessão segura.");
      }

      const { token } = (await csrfResponse.json()) as CsrfResponse;
      const loginResponse = await fetch("/api/web/login", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "X-XSRF-TOKEN": token,
        },
        body: JSON.stringify({ username, password }),
      });

      if (!loginResponse.ok) {
        throw new Error(
          loginResponse.status === 401
            ? "Usuário ou senha inválidos."
            : "Não foi possível entrar. Tente novamente.",
        );
      }

      const result = (await loginResponse.json()) as LoginResult;
      setSignedInAs(result.username);
      setPassword("");
    } catch (submitError) {
      setError(
        submitError instanceof Error
          ? submitError.message
          : "Não foi possível entrar. Tente novamente.",
      );
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <div className="page-shell">
      <header className="topbar">
        <a className="brand" href="/" aria-label="FindMyPacks - página inicial">
          <span className="brand-mark" aria-hidden="true">
            F
          </span>
          <span>FindMyPacks</span>
        </a>
        <span className="topbar-note">Sua coleção, em um só lugar</span>
      </header>

      {signedInAs ? (
        <main className="workspace">
          <p className="welcome-note" role="status">
            Bem-vindo, {signedInAs}.
          </p>
          <Dashboard refreshKey={dashboardRefreshKey} />
          <PackageForm onSaved={() => setDashboardRefreshKey((current) => current + 1)} />
        </main>
      ) : isCheckingSession ? (
        <main className="session-loading" role="status">
          Verificando sessão...
        </main>
      ) : (
        <main className="hero">
          <section className="intro" aria-labelledby="welcome-title">
            <span className="eyebrow">BEM-VINDO AO FINDMYPACKS</span>
            <h1 id="welcome-title">
              Encontre espaço para o que você coleciona.
            </h1>
            <p>
              Organize seus pacotes e acompanhe sua coleção com uma experiência
              simples, feita para você.
            </p>
            <div className="feature-list" aria-label="Recursos">
              <div>
                <span aria-hidden="true">01</span> Tudo organizado em um só
                lugar
              </div>
              <div>
                <span aria-hidden="true">02</span> Acesso seguro à sua conta
              </div>
            </div>
          </section>

          <section className="login-card" aria-labelledby="login-title">
            <div className="card-icon" aria-hidden="true">
              ↗
            </div>
            {signedInAs ? (
              <div role="status">
                <h2 id="login-title">Você entrou</h2>
                <p className="form-description">Bem-vindo, {signedInAs}.</p>
              </div>
            ) : (
              <>
                <h2 id="login-title">Acesse sua conta</h2>
                <p className="form-description">
                  Entre para continuar sua jornada.
                </p>
                <form onSubmit={handleSubmit}>
                  <label htmlFor="username">Usuário</label>
                  <input
                    autoComplete="username"
                    id="username"
                    name="username"
                    onChange={(event) => setUsername(event.target.value)}
                    placeholder="Seu usuário"
                    required
                    value={username}
                  />
                  <label htmlFor="password">Senha</label>
                  <input
                    autoComplete="current-password"
                    id="password"
                    name="password"
                    onChange={(event) => setPassword(event.target.value)}
                    placeholder="Sua senha"
                    required
                    type="password"
                    value={password}
                  />
                  {error && (
                    <p className="error-message" role="alert">
                      {error}
                    </p>
                  )}
                  <button disabled={isSubmitting} type="submit">
                    {isSubmitting ? "Entrando..." : "Entrar"}
                    <span aria-hidden="true">→</span>
                  </button>
                </form>
                <p className="security-note">
                  <span aria-hidden="true">●</span> Seus dados são protegidos
                  por uma conexão segura.
                </p>
              </>
            )}
          </section>
        </main>
      )}

      <footer className="footer">
        <span>© {new Date().getFullYear()} FindMyPacks</span>
        <span>Feito para quem gosta de colecionar.</span>
      </footer>
    </div>
  );
}
