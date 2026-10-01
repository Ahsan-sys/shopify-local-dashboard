# Shopify Local Dashboard

A local management dashboard built with **Spring Boot 4.1.1**, **Java 17+**, and **React 19.3 in JavaScript**. React Router 8.4 handles navigation. Shopify Admin GraphQL is pinned to **2026-07**, as required by the assessment. There is no database, login screen, OAuth redirect, or deployment.

The browser calls only `/api/*` on the Spring Boot backend. The backend obtains and caches an access token using Shopify's **client credentials grant**. The React build is bundled in the executable JAR.

## Quick start

Install a **JDK 17 or later** (JDK 21 is a suitable choice), **Node.js 24 LTS**, and **Git**. Maven 3.9.11 is downloaded automatically by the included Maven Wrapper. Initial dependency downloads require internet access.

Clone this repository, or extract the source ZIP, and open a terminal in `shopify-local-dashboard`.

```bash
git clone https://github.com/Ahsan-sys/shopify-local-dashboard.git
cd shopify-local-dashboard
```

### Linux

```bash
chmod +x backend/mvnw scripts/*.sh
./scripts/build.sh
java "-DSHOPIFY_SHOP=your-shop-subdomain" \
  "-DSHOPIFY_CLIENT_ID=your-client-id" \
  "-DSHOPIFY_CLIENT_SECRET=your-client-secret" \
  "-DSHOPIFY_API_VERSION=2026-07" \
  -jar backend/target/shopify-dashboard.jar
```

### Windows PowerShell

```powershell
.\scripts\build.ps1
java "-DSHOPIFY_SHOP=your-shop-subdomain" `
  "-DSHOPIFY_CLIENT_ID=your-client-id" `
  "-DSHOPIFY_CLIENT_SECRET=your-client-secret" `
  "-DSHOPIFY_API_VERSION=2026-07" `
  -jar .\backend\target\shopify-dashboard.jar
```

The placeholders above are not credentials. Put each `-D` option **before `-jar`**. No space is allowed after a PowerShell continuation backtick. If script execution is restricted, run the manual build commands below rather than changing a machine-wide policy.

Open **http://127.0.0.1:8080**. Stop with **Ctrl+C**. No separate Tomcat installation is needed.

For secrets containing shell-special characters, use the interactive launchers. They avoid putting literal secrets in shell history and pass the collected values as JVM arguments:

```bash
./scripts/run.sh
```

```powershell
.\scripts\run.ps1
```

JVM arguments can still be visible to local process-inspection tools. Use only your trusted local machine. No actual credentials are stored in this repository.

## Configuration

| JVM system property | Meaning | Required |
| --- | --- | --- |
| `SHOPIFY_SHOP` | Shop subdomain only, without `https://` or `.myshopify.com` | Yes |
| `SHOPIFY_CLIENT_ID` | Installed app's client ID, not an access token | Yes |
| `SHOPIFY_CLIENT_SECRET` | Installed app's client secret | Yes |
| `SHOPIFY_API_VERSION` | Must be `2026-07`; this is also the default | Explicitly pass as shown |

Spring also accepts backend environment variables with these names; JVM system properties take precedence. `.env.example` documents the names with blank credential values. **The app does not automatically read `.env`**. Never put secrets in frontend files or `VITE_*` variables.

**Email:** the app does not expect a configured email address and does not send SMTP email. The Store screen reads the shop email from Shopify. `notifyCustomer` asks Shopify to notify the existing order customer. There is therefore no hardcoded recipient or email JVM property to supply.

The app must be installed on a store in the same Shopify organization as the app for the client credentials grant to work. The provided scope contract is:

- `write_products`
- `write_orders`
- `write_merchant_managed_fulfillment_orders`
- `read_locations`
- `read_inventory`

Write scopes include the corresponding read permission. No access token needs to be manually generated or committed.

## Features and scope

| Area | Implemented behavior |
| --- | --- |
| Store | Read-only name, email, myshopify domain, currency, primary domain and time zone |
| Products | Title, handle, status, tags, variant title/SKU/price, available quantity per location |
| Product editing | Saves title, `descriptionHtml`, tags and ACTIVE/DRAFT/ARCHIVED through `productUpdate` |
| Pagination | Shopify `first` / `after` / `pageInfo`; Previous, Next and page size; more variants and locations |
| Orders | Order number, customer/contact name, total and currency, payment status, fulfillment status and creation date |
| Order editing | Saves note and tags through `orderUpdate`; blank values clear existing data |
| Fulfillment | One `fulfillmentCreate` call for all remaining eligible merchant-managed fulfillment orders at one location |
| Tracking | Number, carrier, URL and explicit customer notification choice |
| Feedback | Loading, empty and visible API error states; mutation `userErrors` shown in readable language |

Customer names come from the order's billing name, falling back to shipping name. The UI labels the source. This supports the supplied scopes without requiring `read_customers`; it is an order contact, not a separately fetched customer profile. Missing names display "Guest or customer unavailable". Shopify's protected customer data settings may restrict these fields; authorization errors are shown rather than hidden.

Standard order access is limited to the last 60 days. Older orders require Shopify approval for `read_all_orders`, which is outside the supplied scope set.

### Fulfillment rules

Before a shipment, the backend reloads the order and all accessible fulfillment orders and line-item pages. It checks cancellation, overall fulfillment status, remaining quantities, merchant-managed location, status and the `CREATE_FULFILLMENT` supported action.

All eligible remaining items must share one location. Orders requiring multiple locations, holds, scheduled work or fulfillment services receive a clear explanation. No partial-quantity selector is provided. Already fulfilled or cancelled orders cannot be fulfilled again. Shopify controls which fulfillment orders the app's scopes expose.

The mutation includes one `lineItemsByFulfillmentOrder` entry per fulfillment order and intentionally omits `fulfillmentOrderLineItems`: Shopify fulfills all remaining items in each specified fulfillment order. It does not use product, variant or ordinary order-line IDs in their place.

A bounded local lock pool serializes fulfillment attempts for the same order in one JVM. This is not distributed exactly-once processing. Shopify still makes the final decision if another app modifies an order. Network failures and throttling are not automatically retried for mutations; after an uncertain result, the UI requires a fresh eligibility check.

## Build and test separately

The build scripts run frontend tests, build React, then run Maven `clean verify`. For manual steps:

```bash
cd frontend
npm ci
npm test
npm run build
cd ../backend
./mvnw clean verify
```

On Windows use `npm.cmd` if PowerShell blocks `npm.ps1`, and use `mvnw.cmd`:

```powershell
cd frontend
npm.cmd ci
npm.cmd test
npm.cmd run build
cd ..\backend
.\mvnw.cmd clean verify
```

Tests use artificial data and a mocked Shopify transport or a loopback HTTP server. **No live store credentials or Shopify mutations are needed for tests.**

- Backend: JUnit Jupiter, Mockito, AssertJ and Spring Boot/MockMvc integration tests.
- Frontend: Vitest and React Testing Library.
- Backend test results: `backend/target/surefire-reports/`.
- Backend coverage: `backend/target/site/jacoco/index.html`, generated by `verify`.
- Frontend watch mode: `cd frontend` then `npm run test:watch`.
- Frontend formatting: `npm run format:check` or `npm run format` from `frontend`.

Run one backend class with `./mvnw -Dtest=FulfillmentServiceTest test` (Windows: `.\mvnw.cmd "-Dtest=FulfillmentServiceTest" test`). Backend-only tests do not require a built frontend. To create the complete JAR, build React **before** Maven packaging.

## Development mode

Use the same JAR command above for the backend. In a second terminal:

```bash
cd frontend
npm ci
npm run dev
```

Open **http://127.0.0.1:5173**. Vite proxies `/api` to `127.0.0.1:8080`; browser code does not call Shopify. Frontend changes reload automatically. After backend changes, stop the JAR, run `./mvnw package` in `backend`, and restart it. Rebuild React and repackage when returning to the single-JAR workflow.

## Structure

| Path | Responsibility |
| --- | --- |
| `backend/src/main/java/com/example/shopify/web` | REST routes, validated request DTOs, error responses, local-request filter, SPA routes |
| `backend/src/main/java/com/example/shopify/service` | Catalog operations and fulfillment policy |
| `backend/src/main/java/com/example/shopify/shopify` | Token lifecycle, GraphQL errors, transport and query loading |
| `backend/src/main/resources/graphql` | Versioned queries and mutations with variables |
| `backend/src/test` | JUnit tests and Mockito configuration |
| `frontend/src/pages` | Store, Products and Orders screens |
| `frontend/src/components` | Shared states, pagination and inventory components |
| `frontend/src/api.js` | Browser-to-backend JSON boundary |
| `frontend/src/hooks.js` | Request lifecycle and cursor history |
| `frontend/src/test` | UI and API tests |
| `scripts` | Cross-platform build and interactive run commands |
| `docs` | Learning, setup, testing and requirements guides |

## Backend endpoints

Path IDs are numeric Shopify IDs. The backend constructs the correct `gid://shopify/...` ID. Lists accept `first` from 1 to 20 and an optional opaque `after` cursor.

| Method | Endpoint | Purpose |
| --- | --- | --- |
| GET | `/api/shop` | Store fields |
| GET | `/api/products` | Products with initial variant and inventory pages |
| GET | `/api/products/{id}/variants` | Additional variants |
| GET | `/api/inventory/{id}/levels` | Additional locations |
| PUT | `/api/products/{id}` | Save editable product fields |
| GET | `/api/orders` | Orders and contact names |
| PUT | `/api/orders/{id}` | Save note and tags |
| GET | `/api/orders/{id}/fulfillment-plan` | Preview eligibility and reason |
| POST | `/api/orders/{id}/fulfill` | Revalidate and create one fulfillment |

Errors use `{ "message": "Readable summary", "details": ["field: explanation"] }`. Validation is 400, inaccessible/missing records 404, ineligible fulfillment 409, Shopify `userErrors` 422, throttling 429, and upstream failures 502.

## Safety and limitations

The backend binds to `127.0.0.1`. API responses are not cached by the browser. API requests reject nonlocal Host/Origin values. Tokens live only in backend memory; secrets and raw authentication failures are not logged or returned. Product HTML is edited in a textarea and is never rendered with `dangerouslySetInnerHTML`.

This is deliberately a local, single-user application. Do not expose it through a public tunnel. It has no database, webhook synchronization, SMTP server, deployment files or production identity system. There is no automatic retry of uncertain writes. Query cost is constrained with small page sizes and further data loaded on demand; Shopify can still throttle requests and the UI shows that error.

Two people editing the same product or order in different apps can overwrite the same fields: these updates use last-write-wins semantics. Refresh before editing important data. Fulfillment revalidation reduces stale-state risk but cannot remove a race with external systems.

## Git and submission

`.gitignore` excludes real `.env` files, local configuration, key files, build output, dependencies, logs and editor files. `.env.example`, `package-lock.json` and Maven Wrapper files are committed. `.gitignore` does **not** remove a secret from existing Git history.

Create an empty **public** repository named `shopify-local-dashboard` in GitHub. If the source came from a ZIP and is not already a Git repository:

```bash
git init -b main
git add .
git diff --cached --stat
git diff --cached
git commit -m "Build local Shopify management dashboard"
git remote add origin https://github.com/YOUR_USERNAME/shopify-local-dashboard.git
git push -u origin main
```

Replace `YOUR_USERNAME`; authenticate using your Git credential manager or GitHub CLI, never by putting a token in the remote URL. Check staged changes for secrets **before** committing. If the repository was cloned, skip `git init` and `git remote add` because they already exist.

The submission is the public repository URL, **not** a deployed application URL. Validate a fresh clone with the build and launch commands before sending it. The setup guide explains branches, commits, pull, push, merge conflicts, `.gitignore`, lockfiles and recovery examples.

## Guides and verification

- [Learning and walkthrough guide](docs/LEARNING_GUIDE.md) ([Word version](docs/Shopify_Dashboard_Learning_Guide.docx))
- [Windows and Linux setup guide](docs/LOCAL_SETUP.md) ([Word version](docs/Shopify_Dashboard_Windows_Linux_Setup.docx))
- [Requirement checklist](docs/REQUIREMENTS.md)
- [Test cases and verification record](docs/TESTING.md)

Live Shopify access depends on valid credentials, app installation and store permissions. Automated tests use deterministic responses and cannot establish that a particular external store is configured correctly. See the manual live acceptance checklist before submission.

## Official references

- [Spring Boot stable release](https://spring.io/projects/spring-boot/)
- [Shopify client credentials grant](https://shopify.dev/docs/apps/build/authentication-authorization/client-credentials-grant)
- [Shopify Admin GraphQL 2026-07](https://shopify.dev/docs/api/admin-graphql/2026-07)
- [Product update](https://shopify.dev/docs/api/admin-graphql/2026-07/mutations/productUpdate)
- [Order update](https://shopify.dev/docs/api/admin-graphql/2026-07/mutations/orderUpdate)
- [Fulfillment create](https://shopify.dev/docs/api/admin-graphql/2026-07/mutations/fulfillmentCreate)
- [React](https://react.dev/learn) and [React Router](https://reactrouter.com/start/declarative/routing)
