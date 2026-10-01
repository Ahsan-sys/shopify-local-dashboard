import React from "react";
import { describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router";
import App from "../App.jsx";
import StorePage from "../pages/StorePage.jsx";
import ProductsPage, { ProductCard } from "../pages/ProductsPage.jsx";
import OrdersPage, {
  FulfillmentForm,
  OrderCard,
} from "../pages/OrdersPage.jsx";

const reply = (data, ok = true, status = 200) => ({
  ok,
  status,
  json: async () => data,
});
const empty = { nodes: [], pageInfo: { hasNextPage: false, endCursor: null } };
const product = {
  id: "gid://shopify/Product/1",
  title: "Canvas bag",
  handle: "canvas-bag",
  status: "ACTIVE",
  tags: ["daily"],
  descriptionHtml: "<p>A bag</p>",
  variants: empty,
};
const order = {
  id: "gid://shopify/Order/2",
  name: "#1002",
  customer: null,
  totalPriceSet: { shopMoney: { amount: "24.50", currencyCode: "USD" } },
  displayFinancialStatus: "PAID",
  displayFulfillmentStatus: "UNFULFILLED",
  createdAt: "2026-07-10T12:00:00Z",
  note: "Old",
  tags: ["tag"],
};

describe("screen states and routing", () => {
  it.each([
    [StorePage, "Loading store details"],
    [ProductsPage, "Loading products"],
    [OrdersPage, "Loading orders"],
  ])("shows loading while a request is pending", (Page, label) => {
    vi.stubGlobal(
      "fetch",
      vi.fn(() => new Promise(() => {})),
    );
    render(<Page />);
    expect(screen.getByRole("status")).toHaveTextContent(label);
  });
  it.each([
    [StorePage, null, "No store details"],
    [ProductsPage, empty, "No products"],
    [OrdersPage, empty, "No orders"],
  ])("shows empty state", async (Page, data, title) => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(reply(data)));
    render(<Page />);
    expect(
      await screen.findByRole("heading", { name: new RegExp(title) }),
    ).toBeInTheDocument();
  });
  it.each([StorePage, ProductsPage, OrdersPage])(
    "shows visible API errors",
    async (Page) => {
      vi.stubGlobal(
        "fetch",
        vi
          .fn()
          .mockResolvedValue(
            reply(
              { message: "Access denied", details: ["Check scopes"] },
              false,
              502,
            ),
          ),
      );
      render(<Page />);
      expect(await screen.findByRole("alert")).toHaveTextContent(
        "Check scopes",
      );
    },
  );
  it("uses router navigation without leaving the application", async () => {
    const user = userEvent.setup();
    vi.stubGlobal(
      "fetch",
      vi.fn((url) =>
        Promise.resolve(
          reply(
            url.startsWith("/api/products")
              ? empty
              : { name: "Demo", myshopifyDomain: "demo.myshopify.com" },
          ),
        ),
      ),
    );
    render(
      <MemoryRouter initialEntries={["/store"]}>
        <App />
      </MemoryRouter>,
    );
    expect(
      await screen.findByRole("heading", { name: "Demo" }),
    ).toBeInTheDocument();
    await user.click(screen.getByRole("link", { name: /02Products/ }));
    expect(
      await screen.findByRole("heading", { name: "No products on this page" }),
    ).toBeInTheDocument();
  });
  it("uses Shopify cursor for next and restores first page for previous", async () => {
    const user = userEvent.setup();
    const fetch = vi
      .fn()
      .mockResolvedValueOnce(
        reply({
          nodes: [],
          pageInfo: { hasNextPage: true, endCursor: "opaque+/=" },
        }),
      )
      .mockResolvedValueOnce(reply(empty))
      .mockResolvedValueOnce(reply(empty));
    vi.stubGlobal("fetch", fetch);
    render(<ProductsPage />);
    await waitFor(() =>
      expect(screen.getByRole("button", { name: "Next" })).toBeEnabled(),
    );
    await user.click(screen.getByRole("button", { name: "Next" }));
    await waitFor(() => expect(fetch).toHaveBeenCalledTimes(2));
    expect(
      new URL(fetch.mock.calls[1][0], "http://localhost").searchParams.get(
        "after",
      ),
    ).toBe("opaque+/=");
    await user.click(screen.getByRole("button", { name: "Previous" }));
    await waitFor(() => expect(fetch).toHaveBeenCalledTimes(3));
    expect(fetch.mock.calls[2][0]).toBe("/api/products?first=10");
  });
});
describe("edit workflows", () => {
  it("writes all editable product fields and shows confirmation", async () => {
    const user = userEvent.setup();
    const fetch = vi
      .fn()
      .mockResolvedValue(
        reply({ ...product, title: "New title", status: "DRAFT", tags: [] }),
      );
    vi.stubGlobal("fetch", fetch);
    render(<ProductCard initial={product} />);
    await user.click(screen.getByRole("button", { name: "Edit product" }));
    await user.clear(screen.getByLabelText("Title"));
    await user.type(screen.getByLabelText("Title"), "New title");
    await user.selectOptions(screen.getByLabelText("Status"), "DRAFT");
    await user.clear(screen.getByLabelText(/^Tags/));
    await user.click(screen.getByRole("button", { name: "Save product" }));
    expect(await screen.findByRole("status")).toHaveTextContent(
      "saved to Shopify",
    );
    expect(JSON.parse(fetch.mock.calls[0][1].body)).toEqual({
      title: "New title",
      descriptionHtml: "<p>A bag</p>",
      status: "DRAFT",
      tags: [],
    });
  });
  it("keeps unsaved product values and displays Shopify userErrors", async () => {
    const user = userEvent.setup();
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValue(
          reply(
            { message: "Rejected", details: ["title: Too long"] },
            false,
            422,
          ),
        ),
    );
    render(<ProductCard initial={product} />);
    await user.click(screen.getByRole("button", { name: "Edit product" }));
    await user.click(screen.getByRole("button", { name: "Save product" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "title: Too long",
    );
    expect(screen.getByLabelText("Title")).toHaveValue("Canvas bag");
  });
  it("renders description HTML as inert editor text", async () => {
    const user = userEvent.setup();
    render(
      <ProductCard
        initial={{
          ...product,
          descriptionHtml: '<img src=x onerror="alert(1)">',
        }}
      />,
    );
    await user.click(screen.getByRole("button", { name: "Edit product" }));
    expect(screen.queryByRole("img")).not.toBeInTheDocument();
  });
  it("clears order note and tags through the backend", async () => {
    const user = userEvent.setup();
    const fetch = vi.fn().mockResolvedValue(reply({ note: "", tags: [] }));
    vi.stubGlobal("fetch", fetch);
    render(<OrderCard initial={order} />);
    await user.click(screen.getByRole("button", { name: "Edit note & tags" }));
    await user.clear(screen.getByLabelText("Order note"));
    await user.clear(screen.getByLabelText(/^Tags/));
    await user.click(screen.getByRole("button", { name: "Save order" }));
    expect(await screen.findByRole("status")).toHaveTextContent("saved");
    expect(JSON.parse(fetch.mock.calls[0][1].body)).toEqual({
      note: "",
      tags: [],
    });
  });
  it("shows an explanation when Shopify cannot fulfill", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        reply({
          eligible: false,
          reason: "Items are at multiple locations.",
        }),
      ),
    );
    render(<FulfillmentForm orderId="2" onSaved={vi.fn()} />);
    expect(screen.getByRole("status")).toHaveTextContent(
      "Checking remaining items",
    );
    expect(
      await screen.findByText("Items are at multiple locations."),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Confirm fulfillment" }),
    ).not.toBeInTheDocument();
  });
  it("collects tracking and notification choice and creates one fulfillment", async () => {
    const user = userEvent.setup();
    const onSaved = vi.fn();
    const fetch = vi
      .fn()
      .mockResolvedValueOnce(
        reply({
          eligible: true,
          remainingQuantity: 2,
          locationName: "Main warehouse",
        }),
      )
      .mockResolvedValueOnce(reply({ id: "f1", status: "SUCCESS" }));
    vi.stubGlobal("fetch", fetch);
    render(<FulfillmentForm orderId="2" onSaved={onSaved} />);
    await user.type(
      await screen.findByLabelText("Tracking number"),
      "TRACK-42",
    );
    await user.type(screen.getByLabelText("Carrier name"), "Carrier");
    await user.type(
      screen.getByLabelText("Tracking URL"),
      "https://example.test/track",
    );
    await user.click(screen.getByRole("checkbox"));
    await user.click(
      screen.getByRole("button", { name: "Confirm fulfillment" }),
    );
    await waitFor(() => expect(onSaved).toHaveBeenCalledTimes(1));
    expect(JSON.parse(fetch.mock.calls[1][1].body)).toEqual({
      trackingNumber: "TRACK-42",
      carrier: "Carrier",
      trackingUrl: "https://example.test/track",
      notifyCustomer: true,
    });
    expect(fetch.mock.calls[1][0]).toBe("/api/orders/2/fulfill");
  });
  it("requires rechecking before retrying a failed fulfillment", async () => {
    const user = userEvent.setup();
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValueOnce(
          reply({ eligible: true, remainingQuantity: 2, locationName: "Main" }),
        )
        .mockResolvedValueOnce(
          reply({ message: "Unknown outcome; refresh." }, false, 502),
        ),
    );
    render(<FulfillmentForm orderId="2" onSaved={vi.fn()} />);
    await user.type(await screen.findByLabelText("Tracking number"), "X");
    await user.type(screen.getByLabelText("Carrier name"), "Y");
    await user.type(
      screen.getByLabelText("Tracking URL"),
      "https://example.test",
    );
    await user.click(
      screen.getByRole("button", { name: "Confirm fulfillment" }),
    );
    expect(
      await screen.findByRole("button", { name: "Recheck fulfillment status" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Confirm fulfillment" }),
    ).not.toBeInTheDocument();
  });
});
