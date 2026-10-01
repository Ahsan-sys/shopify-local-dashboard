import React from "react";
import { expect, it, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Variants } from "../components/Inventory.jsx";

const level = (id, name, quantity) => ({
  location: { id, name },
  quantities: [{ name: "available", quantity }],
});
const variant = (id, title, levels, more = false) => ({
  id,
  title,
  sku: "SKU",
  price: "10.00",
  inventoryItem: {
    id: "gid://shopify/InventoryItem/4",
    tracked: true,
    inventoryLevels: {
      nodes: levels,
      pageInfo: { hasNextPage: more, endCursor: "stock-next" },
    },
  },
});
it("loads additional variants without losing the existing variants", async () => {
  const user = userEvent.setup();
  const fetch = vi.fn().mockResolvedValue({
    ok: true,
    json: async () => ({
      nodes: [variant("2", "Blue", [])],
      pageInfo: { hasNextPage: false },
    }),
  });
  vi.stubGlobal("fetch", fetch);
  render(
    <Variants
      productId="gid://shopify/Product/1"
      initial={{
        nodes: [variant("1", "Red", [])],
        pageInfo: { hasNextPage: true, endCursor: "variant-next" },
      }}
    />,
  );
  await user.click(screen.getByRole("button", { name: "Load more variants" }));
  expect(await screen.findByText("Blue")).toBeInTheDocument();
  expect(screen.getByText("Red")).toBeInTheDocument();
  expect(fetch.mock.calls[0][0]).toContain(
    "/api/products/1/variants?first=10&after=variant-next",
  );
});
it("shows zero and negative availability and paginates locations", async () => {
  const user = userEvent.setup();
  const fetch = vi.fn().mockResolvedValue({
    ok: true,
    json: async () => ({
      nodes: [level("2", "South warehouse", -2)],
      pageInfo: { hasNextPage: false },
    }),
  });
  vi.stubGlobal("fetch", fetch);
  render(
    <Variants
      productId="gid://shopify/Product/1"
      initial={{
        nodes: [variant("1", "Red", [level("1", "North warehouse", 0)], true)],
        pageInfo: { hasNextPage: false },
      }}
    />,
  );
  expect(screen.getByText("0")).toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "More locations" }));
  expect(await screen.findByText("-2")).toBeInTheDocument();
  expect(fetch.mock.calls[0][0]).toContain(
    "/api/inventory/4/levels?first=10&after=stock-next",
  );
});
