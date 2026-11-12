import { useEffect, useState } from "react";
import type { FormEvent } from "react";

export type PackageRecord = {
  id: number;
  trackingCode: string;
  customer: string;
  destination: string;
  carrier: string;
  status: string;
  postedAt: string;
};

export type PackageValues = Omit<PackageRecord, "id">;
export type PackageErrors = Partial<Record<keyof PackageValues, string>>;

// Os valores (value) são os códigos aceitos pela API; os rótulos são só da tela.
export const CARRIERS = [
  { value: "CORREIOS", label: "Correios" },
  { value: "JADLOG", label: "Jadlog" },
  { value: "LOGGI", label: "Loggi" },
  { value: "OTHER", label: "Outra" },
];

export const STATUSES = [
  { value: "POSTED", label: "Postado" },
  { value: "IN_TRANSIT", label: "Em trânsito" },
  { value: "OUT_FOR_DELIVERY", label: "Saiu para entrega" },
  { value: "DELIVERED", label: "Entregue" },
];

const TEXT_FIELDS: {
  name: "trackingCode" | "customer" | "destination";
  label: string;
  placeholder: string;
  maxLength: number;
}[] = [
  { name: "trackingCode", label: "Código de rastreio", placeholder: "Ex.: BR123456789", maxLength: 20 },
  { name: "customer", label: "Cliente", placeholder: "Nome do cliente", maxLength: 100 },
  { name: "destination", label: "Destino", placeholder: "Cidade de destino", maxLength: 100 },
];

const EMPTY_VALUES: PackageValues = {
  trackingCode: "",
  customer: "",
  destination: "",
  carrier: "",
  status: "",
  postedAt: "",
};

function todayAsIso(): string {
  const now = new Date();
  const pad = (value: number) => String(value).padStart(2, "0");
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

function toValues(record: PackageRecord): PackageValues {
  return {
    trackingCode: record.trackingCode,
    customer: record.customer,
    destination: record.destination,
    carrier: record.carrier,
    status: record.status,
    postedAt: record.postedAt,
  };
}

function trimTextFields(values: PackageValues): PackageValues {
  return {
    ...values,
    trackingCode: values.trackingCode.trim(),
    customer: values.customer.trim(),
    destination: values.destination.trim(),
  };
}

// Mesmas regras do PackageRequest da API (e da CLI, na #8). Recebe valores já aparados.
export function validatePackage(values: PackageValues): PackageErrors {
  const errors: PackageErrors = {};

  if (!/^[A-Za-z0-9]{8,20}$/.test(values.trackingCode)) {
    errors.trackingCode = "Informe de 8 a 20 letras ou números.";
  }
  if (values.customer.length < 2 || values.customer.length > 100) {
    errors.customer = "O cliente deve ter de 2 a 100 caracteres.";
  }
  if (values.destination.length < 2 || values.destination.length > 100) {
    errors.destination = "O destino deve ter de 2 a 100 caracteres.";
  }
  if (!CARRIERS.some((carrier) => carrier.value === values.carrier)) {
    errors.carrier = "Selecione a transportadora.";
  }
  if (!STATUSES.some((status) => status.value === values.status)) {
    errors.status = "Selecione o status.";
  }
  if (!values.postedAt) {
    errors.postedAt = "Informe a data de postagem.";
  } else if (values.postedAt > todayAsIso()) {
    errors.postedAt = "A data de postagem não pode ser futura.";
  }

  return errors;
}

type ApiError = { message?: string; fieldErrors?: PackageErrors };
type Failure = { fieldErrors: PackageErrors; message: string };

async function readFailure(response: Response): Promise<Failure> {
  if (response.status === 401) {
    return { fieldErrors: {}, message: "Sua sessão expirou. Entre novamente." };
  }
  if (response.status === 404) {
    return { fieldErrors: {}, message: "Pacote não encontrado." };
  }
  if (response.status === 409) {
    return { fieldErrors: { trackingCode: "Este código de rastreio já está cadastrado." }, message: "" };
  }
  if (response.status === 400) {
    try {
      const body = (await response.json()) as ApiError;
      const fieldErrors = body.fieldErrors ?? {};
      return {
        fieldErrors,
        message: Object.keys(fieldErrors).length > 0 ? "" : "Dados inválidos. Revise o formulário.",
      };
    } catch {
      return { fieldErrors: {}, message: "Dados inválidos. Revise o formulário." };
    }
  }
  return { fieldErrors: {}, message: "Não foi possível salvar. Tente novamente." };
}

type PackageFormProps = {
  // Quando a listagem (#3) existir, ela pode passar o registro escolhido para edição.
  initialRecord?: PackageRecord | null;
  onSaved?: (record: PackageRecord) => void;
};

export default function PackageForm({ initialRecord = null, onSaved }: PackageFormProps) {
  const [values, setValues] = useState<PackageValues>(EMPTY_VALUES);
  const [errors, setErrors] = useState<PackageErrors>({});
  const [editingId, setEditingId] = useState<number | null>(null);
  const [formError, setFormError] = useState("");
  const [successMessage, setSuccessMessage] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    if (initialRecord) {
      setEditingId(initialRecord.id);
      setValues(toValues(initialRecord));
      setErrors({});
      setFormError("");
      setSuccessMessage("");
    }
  }, [initialRecord]);

  function setField(name: keyof PackageValues, value: string) {
    setValues((current) => ({ ...current, [name]: value }));
    setErrors((current) => ({ ...current, [name]: undefined }));
  }

  function handleReset() {
    setEditingId(null);
    setValues(EMPTY_VALUES);
    setErrors({});
    setFormError("");
    setSuccessMessage("");
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFormError("");
    setSuccessMessage("");

    const cleaned = trimTextFields(values);
    const found = validatePackage(cleaned);
    setErrors(found);
    if (Object.keys(found).length > 0) {
      return;
    }

    setIsSubmitting(true);
    try {
      const csrfResponse = await fetch("/api/web/csrf");
      if (!csrfResponse.ok) {
        throw new Error("Não foi possível iniciar a sessão segura.");
      }
      const { token } = (await csrfResponse.json()) as { token: string };

      const isEditing = editingId !== null;
      const response = await fetch(isEditing ? `/api/web/records/${editingId}` : "/api/web/records", {
        method: isEditing ? "PUT" : "POST",
        headers: {
          "Content-Type": "application/json",
          "X-XSRF-TOKEN": token,
        },
        body: JSON.stringify(cleaned),
      });

      if (!response.ok) {
        const failure = await readFailure(response);
        setErrors(failure.fieldErrors);
        setFormError(failure.message);
        return;
      }

      // Depois de salvar, o formulário passa a editar o registro salvo.
      const saved = (await response.json()) as PackageRecord;
      setEditingId(saved.id);
      setValues(toValues(saved));
      setSuccessMessage(isEditing ? "Pacote atualizado." : "Pacote cadastrado.");
      onSaved?.(saved);
    } catch (submitError) {
      setFormError(
        submitError instanceof Error
          ? submitError.message
          : "Não foi possível salvar. Tente novamente.",
      );
    } finally {
      setIsSubmitting(false);
    }
  }

  function describedBy(name: keyof PackageValues) {
    return errors[name] ? `${name}-error` : undefined;
  }

  function fieldError(name: keyof PackageValues) {
    return errors[name] ? (
      <p className="error-message" id={`${name}-error`} role="alert">{errors[name]}</p>
    ) : null;
  }

  return (
    <section className="package-card" aria-labelledby="package-form-title">
      <h2 id="package-form-title">
        {editingId === null ? "Cadastrar pacote" : `Editar pacote #${editingId}`}
      </h2>
      <p className="form-description">
        Informe os dados do pacote. Todos os campos são obrigatórios.
      </p>

      <form noValidate onSubmit={handleSubmit}>
        <div className="form-grid">
          {TEXT_FIELDS.map((field) => (
            <div className="form-field" key={field.name}>
              <label htmlFor={field.name}>{field.label}</label>
              <input
                aria-describedby={describedBy(field.name)}
                aria-invalid={errors[field.name] ? true : undefined}
                id={field.name}
                maxLength={field.maxLength}
                name={field.name}
                onChange={(event) => setField(field.name, event.target.value)}
                placeholder={field.placeholder}
                value={values[field.name]}
              />
              {fieldError(field.name)}
            </div>
          ))}

          <div className="form-field">
            <label htmlFor="carrier">Transportadora</label>
            <select
              aria-describedby={describedBy("carrier")}
              aria-invalid={errors.carrier ? true : undefined}
              id="carrier"
              name="carrier"
              onChange={(event) => setField("carrier", event.target.value)}
              value={values.carrier}
            >
              <option value="">Selecione</option>
              {CARRIERS.map((carrier) => (
                <option key={carrier.value} value={carrier.value}>{carrier.label}</option>
              ))}
            </select>
            {fieldError("carrier")}
          </div>

          <div className="form-field">
            <label htmlFor="status">Status</label>
            <select
              aria-describedby={describedBy("status")}
              aria-invalid={errors.status ? true : undefined}
              id="status"
              name="status"
              onChange={(event) => setField("status", event.target.value)}
              value={values.status}
            >
              <option value="">Selecione</option>
              {STATUSES.map((status) => (
                <option key={status.value} value={status.value}>{status.label}</option>
              ))}
            </select>
            {fieldError("status")}
          </div>

          <div className="form-field">
            <label htmlFor="postedAt">Data de postagem</label>
            <input
              aria-describedby={describedBy("postedAt")}
              aria-invalid={errors.postedAt ? true : undefined}
              id="postedAt"
              max={todayAsIso()}
              name="postedAt"
              onChange={(event) => setField("postedAt", event.target.value)}
              type="date"
              value={values.postedAt}
            />
            {fieldError("postedAt")}
          </div>
        </div>

        {formError && <p className="error-message" role="alert">{formError}</p>}
        {successMessage && <p className="success-message" role="status">{successMessage}</p>}

        <div className="form-actions">
          <button disabled={isSubmitting} type="submit">
            {isSubmitting ? "Salvando..." : editingId === null ? "Cadastrar pacote" : "Salvar alterações"}
          </button>
          {editingId !== null && (
            <button className="secondary-button" onClick={handleReset} type="button">
              Novo cadastro
            </button>
          )}
        </div>
      </form>
    </section>
  );
}
