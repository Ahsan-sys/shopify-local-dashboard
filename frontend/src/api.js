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
  const logPath = path.split("?")[0];
  console.info(`[API] ${method} ${logPath} started`);
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
    console.error(`[API] ${method} ${logPath}: backend connection failed`);
    throw new ApiError(
      "Cannot reach the backend. Check that it is running. If you submitted a change, refresh before retrying.",
    );
  }
  const requestId = response.headers?.get?.("X-Request-ID") || "unavailable";
  const contentType = response.headers?.get?.("Content-Type") || "unknown";
  console.info(
    `[API] ${method} ${logPath} status=${response.status} contentType=${contentType} requestId=${requestId}`,
  );
  let data;
  try {
    data = await response.json();
  } catch {
    console.error(
      `[API] ${method} ${logPath}: JSON parsing failed; requestId=${requestId}`,
    );
    throw new ApiError(
      "The backend returned an unexpected response. Refresh before retrying a change.",
      [],
      response.status,
    );
  }
  if (!response.ok) {
    console.error(
      `[API] ${method} ${logPath} failed status=${response.status} requestId=${requestId}`,
    );
    throw new ApiError(
      data.message || "The request failed.",
      data.details || [],
      response.status,
    );
  }
  console.info(
    `[API] ${method} ${logPath} completed successfully; requestId=${requestId}`,
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
