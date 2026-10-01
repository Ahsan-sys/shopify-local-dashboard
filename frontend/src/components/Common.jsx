import { readable } from "../api.js";

export function ErrorNotice({ error, retry }) {
  if (!error) return null;
  return (
    <div className="notice error" role="alert">
      <strong>{error.message || "Something went wrong."}</strong>
      {error.details?.length > 0 && (
        <ul>
          {error.details.map((detail, i) => (
            <li key={i}>{detail}</li>
          ))}
        </ul>
      )}
      {retry && (
        <button className="button secondary compact" onClick={retry}>
          Try again
        </button>
      )}
    </div>
  );
}
export function Loading({ label = "Loading your data…" }) {
  return (
    <div className="loading" role="status">
      <span className="spinner" aria-hidden="true" />
      {label}
    </div>
  );
}
export function Empty({ title, children }) {
  return (
    <div className="empty">
      <span className="empty-icon" aria-hidden="true">
        ◇
      </span>
      <h2>{title}</h2>
      <p>{children}</p>
    </div>
  );
}
export function Badge({ value }) {
  return (
    <span className={`badge ${String(value).toLowerCase()}`}>
      {readable(value)}
    </span>
  );
}
export function Tags({ tags }) {
  return tags?.length ? (
    <div className="tags">
      {tags.map((tag) => (
        <span key={tag}>{tag}</span>
      ))}
    </div>
  ) : (
    <span className="muted small">No tags</span>
  );
}
export function PageHeader({ eyebrow, title, children, action }) {
  return (
    <header className="page-header">
      <div>
        <p className="eyebrow">{eyebrow}</p>
        <h1>{title}</h1>
        <p className="lead">{children}</p>
      </div>
      {action}
    </header>
  );
}
export function Pagination({ pagination }) {
  const p = pagination;
  return (
    <div className="pagination">
      <label>
        Per page{" "}
        <select
          value={p.first}
          onChange={(e) => p.resize(e.target.value)}
          disabled={p.loading}
        >
          {[5, 10, 20].map((size) => (
            <option key={size}>{size}</option>
          ))}
        </select>
      </label>
      <div className="page-controls">
        <span className="muted">Page {p.page}</span>
        <button
          className="button secondary compact"
          disabled={p.loading || p.page === 1}
          onClick={p.previous}
        >
          Previous
        </button>
        <button
          className="button secondary compact"
          disabled={p.loading || !p.data?.pageInfo.hasNextPage}
          onClick={p.next}
        >
          Next
        </button>
      </div>
    </div>
  );
}
