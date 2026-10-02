import { NavLink, Navigate, Route, Routes, useLocation } from "react-router";
import { useEffect } from "react";
import StorePage from "./pages/StorePage.jsx";
import ProductsPage from "./pages/ProductsPage.jsx";
import OrdersPage from "./pages/OrdersPage.jsx";
import { Empty } from "./components/Common.jsx";

export default function App() {
  const { pathname } = useLocation();
  useEffect(() => {
    document.title = `${pathname.slice(1) || "Store"} · Shopify Local Dashboard`;
  }, [pathname]);
  return (
    <div className="app-shell">
      <a href="#main" className="skip-link">
        Skip to content
      </a>
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-icon" aria-hidden="true">
            s
          </span>
          <div>
            Shopify<span>LOCAL DASHBOARD</span>
          </div>
        </div>
        <div className="workspace-label">
          <span className="status-dot" />
          Local workspace
        </div>
        <nav aria-label="Main navigation">
          {[
            ["store", "01", "Store"],
            ["products", "02", "Products"],
            ["orders", "03", "Orders"],
          ].map(([path, number, label]) => (
            <NavLink key={path} to={`/${path}`}>
              <span>{number}</span>
              {label}
              <b aria-hidden="true">↗</b>
            </NavLink>
          ))}
        </nav>
        {/* <div className="sidebar-footer">
          <strong>A little closer to your store.</strong>
          <p>Manage your catalog and orders from one local workspace.</p>
          <span>STORE OPERATIONS</span>
        </div> */}
      </aside>
      <main id="main" tabIndex="-1">
        <div className="topbar">
          <span>
            Workspace / <b>{pathname.slice(1) || "Store"}</b>
          </span>
          {/* <span className="local-pill">On your computer</span> */}
        </div>
        <div className="page-content">
          <Routes>
            <Route path="/" element={<Navigate to="/store" replace />} />
            <Route path="/store" element={<StorePage />} />
            <Route path="/products" element={<ProductsPage />} />
            <Route path="/orders" element={<OrdersPage />} />
            <Route
              path="*"
              element={
                <Empty title="Page not found">
                  Choose Store, Products, or Orders from the navigation.
                </Empty>
              }
            />
          </Routes>
        </div>
      </main>
    </div>
  );
}
