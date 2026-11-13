import { useCallback, useEffect, useState } from "react";
import type { FormEvent } from "react";
import type { PackageRecord } from "./PackageForm";

type PageResult = {
  content: PackageRecord[];
  number: number;
  totalPages: number;
  totalElements: number;
};

const PAGE_SIZE = 5;
const SORT_FIELDS = [
  ["trackingCode", "Código"],
  ["customer", "Cliente"],
  ["destination", "Destino"],
  ["postedAt", "Data de postagem"],
] as const;

type PackageListProps = {
  onEdit: (record: PackageRecord) => void;
  refreshKey: number;
};

type SearchFilters = {
  customer: string;
  status: string;
  carrier: string;
};

const EMPTY_FILTERS: SearchFilters = { customer: "", status: "", carrier: "" };

export default function PackageList({ onEdit, refreshKey }: PackageListProps) {
  const [page, setPage] = useState(0);
  const [sort, setSort] = useState("postedAt,desc");
  const [filters, setFilters] = useState<SearchFilters>(EMPTY_FILTERS);
  const [activeFilters, setActiveFilters] = useState<SearchFilters>(EMPTY_FILTERS);
  const [result, setResult] = useState<PageResult | null>(null);
  const [selected, setSelected] = useState<number[]>([]);
  const [loading, setLoading] = useState(true);
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const loadPackages = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const filterParams = new URLSearchParams();
      Object.entries(activeFilters).forEach(([key, value]) => {
        if (value.trim()) filterParams.set(key, value.trim());
      });
      const hasFilters = Object.values(activeFilters).some((value) => value.trim().length > 0);
      const endpoint = hasFilters ? "/api/web/records/search" : "/api/web/records";
      const query = `page=${page}&size=${PAGE_SIZE}&sort=${sort}${hasFilters ? `&${filterParams.toString()}` : ""}`;
      const response = await fetch(`${endpoint}?${query}`);
      if (response.status === 401) throw new Error("Sua sessão expirou. Entre novamente.");
      if (!response.ok) throw new Error("Não foi possível carregar os pacotes.");
      const data = (await response.json()) as PageResult;
      setResult(data);
      setSelected([]);
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : "Não foi possível carregar os pacotes.");
    } finally {
      setLoading(false);
    }
  }, [activeFilters, page, sort]);

  useEffect(() => { void loadPackages(); }, [loadPackages, refreshKey]);

  function changeSort(field: string) {
    const [currentField, direction] = sort.split(",");
    setSort(`${field},${currentField === field && direction === "asc" ? "desc" : "asc"}`);
    setPage(0);
  }

  function changeFilter(field: keyof SearchFilters, value: string) {
    setFilters((current) => ({ ...current, [field]: value }));
    setPage(0);
  }

  function searchPackages(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPage(0);
    setActiveFilters({ ...filters });
  }

  function clearFilters() {
    setFilters(EMPTY_FILTERS);
    setActiveFilters(EMPTY_FILTERS);
    setPage(0);
  }

  function toggleSelected(id: number) {
    setSelected((current) => current.includes(id) ? current.filter((item) => item !== id) : [...current, id]);
  }

  function togglePageSelection() {
    const ids = result?.content.map((item) => item.id) ?? [];
    const allSelected = ids.length > 0 && ids.every((id) => selected.includes(id));
    setSelected((current) => allSelected ? current.filter((id) => !ids.includes(id)) : [...new Set([...current, ...ids])]);
  }

  async function deleteSelected() {
    if (selected.length === 0 || !window.confirm(`Excluir ${selected.length} pacote(s) selecionado(s)?`)) return;
    setDeleting(true);
    setError("");
    setNotice("");
    try {
      const csrfResponse = await fetch("/api/web/csrf");
      if (!csrfResponse.ok) throw new Error("Não foi possível iniciar a sessão segura.");
      const { token } = (await csrfResponse.json()) as { token: string };
      for (const id of selected) {
        const response = await fetch(`/api/web/records/${id}`, {
          method: "DELETE",
          headers: { "X-XSRF-TOKEN": token },
        });
        if (!response.ok) {
          if (response.status === 401) throw new Error("Sua sessão expirou. Entre novamente.");
          throw new Error("Não foi possível excluir todos os pacotes selecionados.");
        }
      }
      setNotice("Pacote(s) excluído(s). ");
      if (result && result.content.length === selected.length && page > 0) setPage((current) => current - 1);
      else await loadPackages();
    } catch (deleteError) {
      setError(deleteError instanceof Error ? deleteError.message : "Não foi possível excluir os pacotes.");
      await loadPackages();
    } finally {
      setDeleting(false);
    }
  }

  const pageIds = result?.content.map((item) => item.id) ?? [];
  const allSelected = pageIds.length > 0 && pageIds.every((id) => selected.includes(id));

  return (
    <section className="package-card package-list" aria-labelledby="package-list-title">
      <div className="list-heading">
        <div>
          <h2 id="package-list-title">Pacotes cadastrados</h2>
          <p className="form-description">Selecione registros para excluir ou abra um pacote para editar.</p>
        </div>
        <button className="danger-button" disabled={selected.length === 0 || deleting} onClick={() => void deleteSelected()} type="button">
          {deleting ? "Excluindo..." : `Excluir selecionados${selected.length ? ` (${selected.length})` : ""}`}
        </button>
      </div>
      <form className="search-panel" aria-label="Filtros de busca" onSubmit={searchPackages}>
        <div className="search-fields">
          <label className="form-field" htmlFor="search-customer">Cliente
            <input id="search-customer" aria-label="Filtrar por cliente" onChange={(event) => changeFilter("customer", event.target.value)} placeholder="Nome do cliente" value={filters.customer} />
          </label>
          <label className="form-field" htmlFor="search-status">Status
            <select id="search-status" aria-label="Filtrar por status" onChange={(event) => changeFilter("status", event.target.value)} value={filters.status}>
              <option value="">Todos os status</option>
              <option value="POSTED">Postado</option>
              <option value="IN_TRANSIT">Em trânsito</option>
              <option value="OUT_FOR_DELIVERY">Saiu para entrega</option>
              <option value="DELIVERED">Entregue</option>
            </select>
          </label>
          <label className="form-field" htmlFor="search-carrier">Transportadora
            <select id="search-carrier" aria-label="Filtrar por transportadora" onChange={(event) => changeFilter("carrier", event.target.value)} value={filters.carrier}>
              <option value="">Todas as transportadoras</option>
              <option value="CORREIOS">Correios</option>
              <option value="JADLOG">Jadlog</option>
              <option value="LOGGI">Loggi</option>
              <option value="OTHER">Outra</option>
            </select>
          </label>
        </div>
        <div className="search-actions">
          <button type="submit">Buscar</button>
          <button className="secondary-button" onClick={clearFilters} type="button">Limpar filtros</button>
        </div>
      </form>
      {error && <p className="error-message" role="alert">{error}</p>}
      {notice && <p className="success-message" role="status">{notice}</p>}
      <div className="table-scroll">
        <table>
          <thead><tr>
            <th><input aria-label="Selecionar todos os pacotes desta página" checked={allSelected} onChange={togglePageSelection} type="checkbox" /></th>
            {SORT_FIELDS.map(([field, label]) => <th key={field} aria-sort={sort.startsWith(`${field},`) ? (sort.endsWith(",asc") ? "ascending" : "descending") : "none"}>
              <button className="sort-button" onClick={() => changeSort(field)} type="button">{label}{sort.startsWith(`${field},`) ? (sort.endsWith(",asc") ? " ↑" : " ↓") : ""}</button>
            </th>)}
            <th>Ações</th>
          </tr></thead>
          <tbody>
            {loading ? <tr><td colSpan={6}>Carregando pacotes...</td></tr> : error ? <tr><td colSpan={6}>Não foi possível carregar os pacotes.</td></tr> : result?.content.length ? result.content.map((record) => (
              <tr key={record.id}>
                <td><input aria-label={`Selecionar pacote ${record.trackingCode}`} checked={selected.includes(record.id)} onChange={() => toggleSelected(record.id)} type="checkbox" /></td>
                <td>{record.trackingCode}</td><td>{record.customer}</td><td>{record.destination}</td><td>{record.postedAt}</td>
                <td><button className="table-action" onClick={() => onEdit(record)} type="button">Editar</button></td>
              </tr>
            )) : <tr><td colSpan={6}>{Object.values(activeFilters).some((value) => value.trim()) ? "Nenhum pacote encontrado." : "Nenhum pacote cadastrado."}</td></tr>}
          </tbody>
        </table>
      </div>
      <div className="pagination" aria-label="Paginação">
        <span>{result?.totalElements ?? 0} pacote(s)</span>
        <div>
          <button className="secondary-button" disabled={page <= 0 || loading} onClick={() => setPage((current) => current - 1)} type="button">Anterior</button>
          <span>Página {result ? result.number + 1 : 1} de {Math.max(result?.totalPages ?? 0, 1)}</span>
          <button className="secondary-button" disabled={!result || page + 1 >= result.totalPages || loading} onClick={() => setPage((current) => current + 1)} type="button">Próxima</button>
        </div>
      </div>
    </section>
  );
}
