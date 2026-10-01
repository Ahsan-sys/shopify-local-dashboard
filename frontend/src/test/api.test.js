import { describe, expect, it, vi } from "vitest";
import { api, parseTags, safeUrl, pageUrl } from "../api.js";

describe("backend boundary", () => {
  it("sends edits as JSON to the local backend", async () => {
    const fetch = vi
      .fn()
      .mockResolvedValue({ ok: true, json: async () => ({ id: "1" }) });
    vi.stubGlobal("fetch", fetch);
    await api("/api/orders/1", {
      method: "PUT",
      body: { note: "Edited", tags: [] },
    });
    expect(fetch).toHaveBeenCalledWith(
      "/api/orders/1",
      expect.objectContaining({
        method: "PUT",
        body: '{"note":"Edited","tags":[]}',
      }),
    );
  });
  it("rejects direct Shopify or other remote URLs", async () => {
    const fetch = vi.fn();
    vi.stubGlobal("fetch", fetch);
    await expect(api("https://shop.myshopify.com/admin/api")).rejects.toThrow(
      "Only backend",
    );
    expect(fetch).not.toHaveBeenCalled();
  });
  it("retains readable Shopify errors", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: false,
        status: 422,
        json: async () => ({
          message: "Change rejected",
          details: ["tags: Invalid"],
        }),
      }),
    );
    await expect(api("/api/orders/1")).rejects.toMatchObject({
      message: "Change rejected",
      details: ["tags: Invalid"],
      status: 422,
    });
  });
  it("shows network failures and never retries a mutation", async () => {
    const fetch = vi.fn().mockRejectedValue(new TypeError("Failed to fetch"));
    vi.stubGlobal("fetch", fetch);
    await expect(
      api("/api/orders/1/fulfill", { method: "POST", body: {} }),
    ).rejects.toThrow("refresh before retrying");
    expect(fetch).toHaveBeenCalledTimes(1);
  });
  it("rejects unexpected HTML responses", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => {
          throw new Error();
        },
      }),
    );
    await expect(api("/api/shop")).rejects.toThrow("unexpected response");
  });
  it("preserves opaque cursor characters through URL encoding", () => {
    const query = new URL(
      pageUrl("/api/products", 10, "a+/="),
      "http://localhost",
    ).searchParams;
    expect(query.get("after")).toBe("a+/=");
    expect(query.get("first")).toBe("10");
  });
  it("handles clear and deduplicated tags", () => {
    expect(parseTags("")).toEqual([]);
    expect(parseTags(" A\n B\nA\n")).toEqual(["A", "B"]);
  });
  it("does not allow script or credential URLs", () => {
    expect(safeUrl("javascript:alert(1)")).toBeNull();
    expect(safeUrl("https://u:p@example.test")).toBeNull();
    expect(safeUrl("https://example.test/1")).toBe("https://example.test/1");
  });
});
