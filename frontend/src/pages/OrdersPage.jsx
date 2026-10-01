import { useState } from "react";
import { api, numericId, parseTags, safeUrl } from "../api.js";
import { useApi, usePagination } from "../hooks.js";
import {
  Badge,
  Empty,
  ErrorNotice,
  Loading,
  PageHeader,
  Pagination,
  Tags,
} from "../components/Common.jsx";

export default function OrdersPage() {
  const pagination = usePagination("/api/orders");
  return (
    <>
      <PageHeader
        eyebrow="03 / ORDER MANAGEMENT"
        title="Keep orders moving."
        action={
          <button
            className="button secondary"
            disabled={pagination.loading}
            onClick={pagination.reload}
          >
            Refresh orders
          </button>
        }
      >
        Review orders, update notes, and fulfill the remaining items in one
        shipment.
      </PageHeader>
      <p className="footnote">
        Orders available to this app are shown, newest first. Standard order
        access covers the last 60 days.
      </p>
      <Pagination pagination={pagination} />
      {pagination.loading && <Loading label="Loading orders…" />}
      <ErrorNotice error={pagination.error} retry={pagination.reload} />
      {!pagination.loading &&
        !pagination.error &&
        (pagination.data?.nodes.length ? (
          <div className="card-list">
            {pagination.data.nodes.map((order) => (
              <OrderCard key={order.id} initial={order} />
            ))}
          </div>
        ) : (
          <Empty title="No orders on this page">
            Create a test order in Shopify, then refresh, or return to the
            previous page.
          </Empty>
        ))}
    </>
  );
}
export function OrderCard({ initial }) {
  const [order, setOrder] = useState(initial);
  const [editing, setEditing] = useState(false);
  const [busy, setBusy] = useState(false);
  const [checking, setChecking] = useState(false);
  const [success, setSuccess] = useState("");
  const [fulfilled, setFulfilled] = useState(false);
  const money = order.totalPriceSet.shopMoney;
  const blocked = order.cancelledAt
    ? "This order is cancelled."
    : order.displayFulfillmentStatus === "FULFILLED"
      ? "This order is already fulfilled."
      : null;
  return (
    <article className="record-card">
      <div className="record-header">
        <div>
          <div className="record-title">
            <h2>{order.name}</h2>
            <Badge value={order.displayFinancialStatus} />
            <Badge value={order.displayFulfillmentStatus} />
          </div>
          <p className="muted">
            {order.customer?.displayName || "Guest or customer unavailable"}
            {order.customer?.source && (
              <span className="small"> · {order.customer.source}</span>
            )}
          </p>
        </div>
        <strong className="order-total">
          {money.amount} <span>{money.currencyCode}</span>
        </strong>
      </div>
      <div className="order-meta">
        <span>
          Created{" "}
          <time dateTime={order.createdAt}>
            {new Date(order.createdAt).toLocaleString()}
          </time>
        </span>
        <span>Note: {order.note || "No note"}</span>
      </div>
      <Tags tags={order.tags} />
      <div className="action-row">
        <button
          className="button secondary compact"
          disabled={busy}
          onClick={() => {
            setEditing(!editing);
            setSuccess("");
          }}
        >
          {editing ? "Close editor" : "Edit note & tags"}
        </button>
        {!blocked && !fulfilled && (
          <button
            className="button compact"
            disabled={busy}
            onClick={() => setChecking(!checking)}
          >
            {checking ? "Close fulfillment" : "Fulfill remaining items"}
          </button>
        )}
      </div>
      {blocked && <p className="footnote">{blocked}</p>}
      {success && (
        <p className="notice success" role="status">
          {success}
        </p>
      )}
      {editing && (
        <OrderForm
          order={order}
          onBusy={setBusy}
          onSaved={(saved) => {
            setOrder((current) => ({ ...current, ...saved }));
            setEditing(false);
            setSuccess("Order note and tags saved to Shopify.");
          }}
        />
      )}
      {checking && !fulfilled && (
        <FulfillmentForm
          orderId={numericId(order.id)}
          onBusy={setBusy}
          onSaved={(result) => {
            setChecking(false);
            setFulfilled(true);
            setSuccess(
              `Fulfillment created (${result.status.toLowerCase()}). Refresh orders to see the latest overall status.`,
            );
          }}
        />
      )}
    </article>
  );
}
function OrderForm({ order, onSaved, onBusy }) {
  const [note, setNote] = useState(order.note || "");
  const [tags, setTags] = useState(order.tags.join("\n"));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);
  async function save(event) {
    event.preventDefault();
    setSaving(true);
    onBusy(true);
    setError(null);
    try {
      onSaved(
        await api(`/api/orders/${numericId(order.id)}`, {
          method: "PUT",
          body: { note, tags: parseTags(tags) },
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
        <legend>Edit order details</legend>
        <label>
          Order note
          <textarea
            rows={3}
            maxLength={5000}
            value={note}
            onChange={(e) => setNote(e.target.value)}
          />
        </label>
        <label>
          Tags
          <textarea
            rows={2}
            value={tags}
            onChange={(e) => setTags(e.target.value)}
          />
          <span className="field-hint">
            One tag per line. Clear a field to remove its value.
          </span>
        </label>
        <ErrorNotice error={error} />
        <button className="button" type="submit">
          {saving ? "Saving…" : "Save order"}
        </button>
      </fieldset>
    </form>
  );
}
export function FulfillmentForm({ orderId, onSaved, onBusy = () => {} }) {
  const {
    data: plan,
    loading,
    error: planError,
    reload,
  } = useApi(`/api/orders/${orderId}/fulfillment-plan`);
  const [form, setForm] = useState({
    trackingNumber: "",
    carrier: "",
    trackingUrl: "",
    notifyCustomer: false,
  });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);
  const [needsRefresh, setNeedsRefresh] = useState(false);
  const change = (e) =>
    setForm({
      ...form,
      [e.target.name]:
        e.target.type === "checkbox" ? e.target.checked : e.target.value,
    });
  async function submit(event) {
    event.preventDefault();
    if (!safeUrl(form.trackingUrl)) {
      setError(
        new Error(
          "Enter a valid HTTP or HTTPS tracking URL without embedded credentials.",
        ),
      );
      return;
    }
    setSaving(true);
    onBusy(true);
    setError(null);
    try {
      onSaved(
        await api(`/api/orders/${orderId}/fulfill`, {
          method: "POST",
          body: form,
        }),
      );
    } catch (e) {
      setError(e);
      setNeedsRefresh(true);
    } finally {
      setSaving(false);
      onBusy(false);
    }
  }
  return (
    <section className="fulfillment-panel" aria-label="Fulfillment details">
      {loading && <Loading label="Checking remaining items and location…" />}
      <ErrorNotice error={planError} retry={reload} />
      {plan && !plan.eligible && (
        <p className="notice info" role="status">
          {plan.reason}
        </p>
      )}
      {plan?.eligible && (
        <form onSubmit={submit}>
          <fieldset disabled={saving || loading}>
            <legend>Fulfill remaining items</legend>
            <p>
              <strong>{plan.remainingQuantity} items</strong> from{" "}
              <strong>{plan.locationName}</strong>. All remaining eligible items
              will be included in one shipment.
            </p>
            <div className="form-grid">
              <label>
                Tracking number
                <input
                  name="trackingNumber"
                  required
                  maxLength={255}
                  value={form.trackingNumber}
                  onChange={change}
                />
              </label>
              <label>
                Carrier name
                <input
                  name="carrier"
                  required
                  maxLength={255}
                  value={form.carrier}
                  onChange={change}
                />
              </label>
            </div>
            <label>
              Tracking URL
              <input
                name="trackingUrl"
                type="url"
                required
                maxLength={2048}
                placeholder="https://carrier.example/track/…"
                value={form.trackingUrl}
                onChange={change}
              />
            </label>
            <label className="checkbox-label">
              <input
                name="notifyCustomer"
                type="checkbox"
                checked={form.notifyCustomer}
                onChange={change}
              />
              Notify the customer through Shopify
            </label>
            <ErrorNotice error={error} />
            {needsRefresh ? (
              <button
                type="button"
                className="button secondary"
                onClick={() => {
                  setNeedsRefresh(false);
                  setError(null);
                  reload();
                }}
              >
                Recheck fulfillment status
              </button>
            ) : (
              <button className="button" type="submit">
                {saving ? "Creating fulfillment…" : "Confirm fulfillment"}
              </button>
            )}
          </fieldset>
        </form>
      )}
    </section>
  );
}
