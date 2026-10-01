export class ApiError extends Error {
  constructor(message, details = [], status = 0) {
    super(message);
    this.name = "ApiError";
    this.details = details;
    this.status = status;
  }
}

// All application requests are relative calls to our backend. No Shopify credentials here.
export async function api(path, { method = "GET", body, signal } = {}) {
  if (!path.startsWith("/api/"))
    throw new Error("Only backend API paths are allowed.");
  let response;
  try {
    response = await fetch(path, {
      method,
      signal,
      credentials: "same-origin",
      headers:
        body === undefined
          ? { Accept: "application/json" }
          : { Accept: "application/json", "Content-Type": "application/json" },
      ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    });
  } catch (error) {
    if (error.name === "AbortError") throw error;
    throw new ApiError(
      "Cannot reach the backend. Check that it is running. If you submitted a change, refresh before retrying.",
    );
  }
  let data;
  try {
    data = await response.json();
  } catch {
    throw new ApiError(
      "The backend returned an unexpected response. Refresh before retrying a change.",
      [],
      response.status,
    );
  }
  if (!response.ok)
    throw new ApiError(
      data.message || "The request failed.",
      data.details || [],
      response.status,
    );
  return data;
}

export const numericId = (gid) => gid.split("/").at(-1);
export const parseTags = (text) => [
  ...new Set(
    text
      .split("\n")
      .map((tag) => tag.trim())
      .filter(Boolean),
  ),
];
export const readable = (value) =>
  value
    ? value
        .toLowerCase()
        .replaceAll("_", " ")
        .replace(/^./, (c) => c.toUpperCase())
    : "Unavailable";
export const pageUrl = (path, first, after) =>
  `${path}?${new URLSearchParams({ first, ...(after ? { after } : {}) })}`;
export function safeUrl(value) {
  try {
    const url = new URL(value);
    return ["http:", "https:"].includes(url.protocol) &&
      !url.username &&
      !url.password
      ? url.href
      : null;
  } catch {
    return null;
  }
}
