import { useState } from "react";
import { api, numericId, pageUrl } from "../api.js";
import { ErrorNotice } from "./Common.jsx";

export function Variants({ productId, initial }) {
  const [connection, setConnection] = useState(initial);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  async function more() {
    setLoading(true);
    setError(null);
    try {
      const next = await api(
        pageUrl(
          `/api/products/${numericId(productId)}/variants`,
          10,
          connection.pageInfo.endCursor,
        ),
      );
      setConnection((current) => ({
        ...next,
        nodes: [...current.nodes, ...next.nodes],
      }));
    } catch (e) {
      setError(e);
    } finally {
      setLoading(false);
    }
  }
  return (
    <section className="variant-section" aria-label="Variants and inventory">
      <h3>Variants &amp; inventory</h3>
      {!connection?.nodes.length ? (
        <p className="muted">No variants available.</p>
      ) : (
        <div className="table-scroll">
          <table className="variant-table">
            <thead>
              <tr>
                <th>Variant</th>
                <th>SKU</th>
                <th>
                  Price <span className="muted small">(store currency)</span>
                </th>
                <th>Available by location</th>
              </tr>
            </thead>
            <tbody>
              {connection.nodes.map((variant) => (
                <tr key={variant.id}>
                  <td>{variant.title}</td>
                  <td className="mono">{variant.sku || "No SKU"}</td>
                  <td className="amount">{variant.price}</td>
                  <td>
                    <Inventory item={variant.inventoryItem} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <ErrorNotice error={error} />
      {connection?.pageInfo.hasNextPage && (
        <button className="text-button" disabled={loading} onClick={more}>
          {loading ? "Loading variants…" : "Load more variants"}
        </button>
      )}
    </section>
  );
}
function Inventory({ item }) {
  const [connection, setConnection] = useState(item?.inventoryLevels);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  async function more() {
    setLoading(true);
    setError(null);
    try {
      const next = await api(
        pageUrl(
          `/api/inventory/${numericId(item.id)}/levels`,
          10,
          connection.pageInfo.endCursor,
        ),
      );
      setConnection((current) => ({
        ...next,
        nodes: [...current.nodes, ...next.nodes],
      }));
    } catch (e) {
      setError(e);
    } finally {
      setLoading(false);
    }
  }
  if (!item?.tracked)
    return <span className="muted">Inventory not tracked</span>;
  return (
    <>
      {!connection?.nodes.length ? (
        <span className="muted">No stock locations</span>
      ) : (
        <ul className="stock-list">
          {connection.nodes.map((level) => (
            <li key={level.location.id}>
              <span>{level.location.name}</span>
              <b>
                {level.quantities.find((q) => q.name === "available")
                  ?.quantity ?? "Unavailable"}
              </b>
            </li>
          ))}
        </ul>
      )}
      <ErrorNotice error={error} />
      {connection?.pageInfo.hasNextPage && (
        <button className="text-button" disabled={loading} onClick={more}>
          {loading ? "Loading locations…" : "More locations"}
        </button>
      )}
    </>
  );
}
