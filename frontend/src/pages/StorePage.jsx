import { useApi } from "../hooks.js";
import { safeUrl } from "../api.js";
import {
  Empty,
  ErrorNotice,
  Loading,
  PageHeader,
} from "../components/Common.jsx";

export default function StorePage() {
  const { data: shop, loading, error, reload } = useApi("/api/shop");
  return (
    <>
      <PageHeader
        eyebrow="01 / STORE OVERVIEW"
        title="Your store, at a glance."
        action={
          <button
            className="button secondary"
            disabled={loading}
            onClick={reload}
          >
            Refresh store
          </button>
        }
      >
        The essentials of your Shopify store, in one place.
      </PageHeader>
      {loading && <Loading label="Loading store details…" />}
      <ErrorNotice error={error} retry={reload} />
      {!loading &&
        !error &&
        (!shop?.name ? (
          <Empty title="No store details available">
            Check your shop configuration, then refresh.
          </Empty>
        ) : (
          <>
            <section className="store-hero">
              <div>
                <span className="eyebrow">YOUR SHOPIFY STORE</span>
                <h2>{shop.name}</h2>
                <p>{shop.myshopifyDomain}</p>
              </div>
              <span className="store-monogram" aria-hidden="true">
                {shop.name.slice(0, 1)}
              </span>
            </section>
            <div className="detail-grid">
              <Detail label="STORE NAME" value={shop.name} />
              <Detail label="STORE EMAIL" value={shop.email} />
              <Detail label="STORE CURRENCY" value={shop.currencyCode} />
              <Detail label="MYSHOPIFY DOMAIN" value={shop.myshopifyDomain} />
              <Detail
                label="PRIMARY DOMAIN"
                value={shop.primaryDomain?.host}
                url={safeUrl(shop.primaryDomain?.url)}
              />
              <Detail label="TIME ZONE" value={shop.ianaTimezone} />
            </div>
            <p className="footnote">
              Store details are read only. Product and order changes are saved
              directly to Shopify.
            </p>
          </>
        ))}
    </>
  );
}
function Detail({ label, value, url }) {
  return (
    <section className="detail-card">
      <h3>{label}</h3>
      {url ? (
        <a href={url} target="_blank" rel="noreferrer">
          {value} ↗
        </a>
      ) : (
        <p>{value || "Not provided"}</p>
      )}
    </section>
  );
}
