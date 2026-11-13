import { useEffect, useState } from "react";
import { CARRIERS, STATUSES } from "./PackageForm";

type DashboardCount = {
  label: string;
  count: number;
};

type DashboardDayCount = {
  date: string;
  count: number;
};

type DashboardSummary = {
  byStatus: DashboardCount[];
  byCarrier: DashboardCount[];
  postedPerDay: DashboardDayCount[];
};

type DashboardProps = {
  refreshKey?: number;
};

const CHART_WIDTH = 640;
const CHART_HEIGHT = 250;
const CHART_PADDING = { top: 24, right: 20, bottom: 40, left: 36 };

function localizeCounts(counts: DashboardCount[], choices: { value: string; label: string }[]) {
  return choices.map((choice) => ({
    label: choice.label,
    count: counts.find((item) => item.label === choice.value)?.count ?? 0,
  }));
}

function CategoryChart({ title, data }: { title: string; data: DashboardCount[] }) {
  const maximum = Math.max(1, ...data.map((item) => item.count));
  const headingId = `dashboard-${title.toLowerCase().replaceAll(" ", "-")}`;

  return (
    <section className="dashboard-card" aria-labelledby={headingId}>
      <h3 id={headingId}>{title}</h3>
      <ul className="dashboard-bar-chart">
        {data.map((item, index) => (
          <li className="dashboard-bar-row" key={item.label}>
            <span className="dashboard-bar-label">{item.label}</span>
            <div className="dashboard-bar-track" aria-hidden="true">
              <span
                className={`dashboard-bar-fill chart-color-${index % 4}`}
                style={{ width: `${(item.count / maximum) * 100}%` }}
              />
            </div>
            <span className="dashboard-bar-count">{item.count}</span>
          </li>
        ))}
      </ul>
    </section>
  );
}

function DailyChart({ data }: { data: DashboardDayCount[] }) {
  const headingId = "dashboard-posted-per-day";
  const plotWidth = CHART_WIDTH - CHART_PADDING.left - CHART_PADDING.right;
  const plotHeight = CHART_HEIGHT - CHART_PADDING.top - CHART_PADDING.bottom;
  const maximum = Math.max(1, ...data.map((item) => item.count));
  const points = data.map((item, index) => ({
    ...item,
    x: CHART_PADDING.left + (plotWidth * index) / Math.max(data.length - 1, 1),
    y: CHART_PADDING.top + plotHeight - (item.count / maximum) * plotHeight,
  }));
  const linePoints = points.map((point) => `${point.x},${point.y}`).join(" ");

  return (
    <section className="dashboard-card" aria-labelledby={headingId}>
      <h3 id={headingId}>Postagens nos últimos 7 dias</h3>
      <svg
        aria-label="Quantidade de pacotes postados por dia nos últimos sete dias"
        className="dashboard-line-chart"
        role="img"
        viewBox={`0 0 ${CHART_WIDTH} ${CHART_HEIGHT}`}
      >
        {[0, 0.5, 1].map((fraction) => {
          const y = CHART_PADDING.top + plotHeight * fraction;
          return (
            <line
              className="dashboard-grid-line"
              key={fraction}
              x1={CHART_PADDING.left}
              x2={CHART_WIDTH - CHART_PADDING.right}
              y1={y}
              y2={y}
            />
          );
        })}
        <polyline className="dashboard-line" points={linePoints} />
        {points.map((point) => (
          <g key={point.date}>
            <circle className="dashboard-point" cx={point.x} cy={point.y} r="5">
              <title>{`${point.date}: ${point.count} pacote(s)`}</title>
            </circle>
            <text
              className="dashboard-point-count"
              textAnchor="middle"
              x={point.x}
              y={point.y - 10}
            >
              {point.count}
            </text>
            <text
              className="dashboard-date-label"
              textAnchor="middle"
              x={point.x}
              y={CHART_HEIGHT - 12}
            >
              {new Intl.DateTimeFormat("pt-BR", { day: "2-digit", month: "2-digit" })
                .format(new Date(`${point.date}T00:00:00`))}
            </text>
          </g>
        ))}
      </svg>
    </section>
  );
}

export default function Dashboard({ refreshKey = 0 }: DashboardProps) {
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [error, setError] = useState("");
  const [isLoading, setIsLoading] = useState(true);
  const [retryKey, setRetryKey] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setIsLoading(true);
    setError("");

    async function loadSummary() {
      try {
        const response = await fetch("/api/web/dashboard", { signal: controller.signal });
        if (!response.ok) {
          throw new Error(response.status === 401
            ? "Sua sessão expirou. Entre novamente."
            : "Não foi possível carregar os indicadores.");
        }
        const result = (await response.json()) as DashboardSummary;
        if (!controller.signal.aborted) {
          setSummary(result);
        }
      } catch (loadError) {
        if (!controller.signal.aborted) {
          setError(loadError instanceof Error
            ? loadError.message
            : "Não foi possível carregar os indicadores.");
        }
      } finally {
        if (!controller.signal.aborted) {
          setIsLoading(false);
        }
      }
    }

    void loadSummary();
    return () => controller.abort();
  }, [refreshKey, retryKey]);

  return (
    <section className="dashboard" aria-labelledby="dashboard-title">
      <header className="dashboard-header">
        <div>
          <h2 id="dashboard-title">Dashboard de pacotes</h2>
          <p>Indicadores atualizados com os dados salvos.</p>
        </div>
        <button
          className="secondary-button dashboard-refresh"
          disabled={isLoading}
          onClick={() => setRetryKey((current) => current + 1)}
          type="button"
        >
          {isLoading ? "Atualizando..." : "Atualizar"}
        </button>
      </header>

      {isLoading && <p className="dashboard-message" role="status">Carregando indicadores...</p>}
      {error && (
        <div className="dashboard-error" role="alert">
          <p>{error}</p>
          <button
            className="secondary-button"
            onClick={() => setRetryKey((current) => current + 1)}
            type="button"
          >
            Tentar novamente
          </button>
        </div>
      )}
      {!isLoading && !error && summary && (
        <div className="dashboard-grid">
          <CategoryChart
            data={localizeCounts(summary.byStatus, STATUSES)}
            title="Pacotes por status"
          />
          <CategoryChart
            data={localizeCounts(summary.byCarrier, CARRIERS)}
            title="Pacotes por transportadora"
          />
          <DailyChart data={summary.postedPerDay} />
        </div>
      )}
    </section>
  );
}
