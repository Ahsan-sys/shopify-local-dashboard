import { useState } from "react";
import { api, numericId, parseTags } from "../api.js";
import { usePagination } from "../hooks.js";
import {
  Badge,
  Empty,
  ErrorNotice,
  Loading,
  PageHeader,
  Pagination,
  Tags,
} from "../components/Common.jsx";
import { Variants } from "../components/Inventory.jsx";

export default function ProductsPage() {
  const pagination = usePagination("/api/products");
  return (
    <>
      <PageHeader
        eyebrow="02 / YOUR CATALOG"
        title="Make every product count."
        action={
          <button
            className="button secondary"
            disabled={pagination.loading}
            onClick={pagination.reload}
          >
            Refresh products
          </button>
        }
      >
        Browse your catalog, check stock, and keep product details up to date.
      </PageHeader>
      <Pagination pagination={pagination} />
      {pagination.loading && (
        <Loading label="Loading products and inventory…" />
      )}
      <ErrorNotice error={pagination.error} retry={pagination.reload} />
      {!pagination.loading &&
        !pagination.error &&
        (pagination.data?.nodes.length ? (
          <div className="card-list">
            {pagination.data.nodes.map((product) => (
              <ProductCard key={product.id} initial={product} />
            ))}
          </div>
        ) : (
          <Empty title="No products on this page">
            Add products in Shopify, then refresh, or return to the previous
            page.
          </Empty>
        ))}
    </>
  );
}
export function ProductCard({ initial }) {
  const [product, setProduct] = useState(initial);
  const [editing, setEditing] = useState(false);
  const [busy, setBusy] = useState(false);
  const [success, setSuccess] = useState("");
  return (
    <article className="record-card">
      <div className="record-header">
        <div>
          <div className="record-title">
            <h2>{product.title}</h2>
            <Badge value={product.status} />
          </div>
          <p className="muted mono small">/{product.handle}</p>
        </div>
        <button
          className="button secondary compact"
          disabled={busy}
          onClick={() => {
            setEditing(!editing);
            setSuccess("");
          }}
        >
          {editing ? "Close editor" : "Edit product"}
        </button>
      </div>
      <Tags tags={product.tags} />
      {success && (
        <p className="notice success" role="status">
          {success}
        </p>
      )}
      {editing && (
        <ProductForm
          product={product}
          onBusy={setBusy}
          onSaved={(saved) => {
            setProduct((current) => ({ ...current, ...saved }));
            setEditing(false);
            setSuccess("Product saved to Shopify.");
          }}
        />
      )}
      <Variants productId={product.id} initial={product.variants} />
    </article>
  );
}
function ProductForm({ product, onSaved, onBusy }) {
  const [form, setForm] = useState({
    title: product.title,
    descriptionHtml: product.descriptionHtml || "",
    tags: product.tags.join("\n"),
    status: product.status,
  });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);
  const change = (event) =>
    setForm({ ...form, [event.target.name]: event.target.value });
  async function save(event) {
    event.preventDefault();
    setSaving(true);
    onBusy(true);
    setError(null);
    try {
      onSaved(
        await api(`/api/products/${numericId(product.id)}`, {
          method: "PUT",
          body: { ...form, tags: parseTags(form.tags) },
        }),
      );
    } catch (e) {
      setError(e);
    } finally {
      setSaving(false);
      onBusy(false);
    }
  }
  return (
    <form className="edit-form" onSubmit={save}>
      <fieldset disabled={saving}>
        <legend>Edit product details</legend>
        <div className="form-grid">
          <label>
            Title
            <input
              name="title"
              required
              maxLength={255}
              value={form.title}
              onChange={change}
            />
          </label>
          <label>
            Status
            <select name="status" value={form.status} onChange={change}>
              {["ACTIVE", "DRAFT", "ARCHIVED"].map((status) => (
                <option key={status}>{status}</option>
              ))}
            </select>
          </label>
        </div>
        <label>
          Description HTML
          <textarea
            name="descriptionHtml"
            className="mono"
            rows={5}
            maxLength={100000}
            value={form.descriptionHtml}
            onChange={change}
          />
          <span className="field-hint">
            HTML is edited as text. It is not executed in this dashboard.
          </span>
        </label>
        <label>
          Tags
          <textarea name="tags" rows={2} value={form.tags} onChange={change} />
          <span className="field-hint">
            One tag per line. Clear the field to remove all tags.
          </span>
        </label>
        <ErrorNotice error={error} />
        <button className="button" type="submit">
          {saving ? "Saving…" : "Save product"}
        </button>
      </fieldset>
    </form>
  );
}
