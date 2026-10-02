# Shopify Local Dashboard

A local Shopify management dashboard using Spring Boot 4.1.1, Java 17+, and React 19.3 (JavaScript). All Shopify Admin GraphQL calls use API version **2026-07** and run in the backend. The React build is bundled into the executable Spring Boot JAR.

## Requirements

- JDK 17 or later, with `JAVA_HOME` pointing to the JDK and its `bin` directory on `PATH`.
- Node.js 24 LTS recommended (minimum supported by this frontend: 22.12).
- Internet access for dependencies and Shopify.
- An app installed on the development store, with valid client ID and secret. The app and store must belong to the same Shopify organization for the client credentials grant.

Maven 3.9.11 is supplied through the Maven Wrapper; no separate Maven or Tomcat installation is required.

## Configure the backend

Set these variables in the backend process environment (for example, IntelliJ's Run configuration):

| Variable | Value |
| --- | --- |
| `SHOPIFY_SHOP` | Store subdomain, without `https://` or `.myshopify.com` |
| `SHOPIFY_CLIENT_ID` | Installed app's client ID |
| `SHOPIFY_CLIENT_SECRET` | Installed app's client secret |
| `SHOPIFY_API_VERSION` | `2026-07` |

`.env.example` lists the variable names with blank credential values. **Spring Boot does not automatically load `.env` files.** Never place credentials in frontend files, `VITE_*` variables, or Git. JVM `-DNAME=value` arguments are also supported and take precedence over environment variables.

The backend exchanges the client ID and secret for a Shopify access token, caches it in memory, and renews it before expiry. No user login or OAuth redirect is needed.

Required installed scopes:

```text
write_products
write_orders
write_merchant_managed_fulfillment_orders
read_locations
read_inventory
```

## Build and run on Windows

From the repository root:

```powershell
cd frontend
npm.cmd ci
npm.cmd test
npm.cmd run build
cd ../backend
.\mvnw.cmd clean verify
java -jar .\target\shopify-dashboard.jar
```

The launch command inherits the backend environment variables described above. Alternatively, return to the repository root and run `scripts\run.ps1`, which prompts for configuration without writing literal secrets into shell history. The collected values are passed as JVM arguments and can be visible to local process tools.

`scripts\build.ps1` automates the build and tests. If PowerShell blocks scripts, use the manual commands above without changing machine-wide execution policy.

## Build and run on Linux

Set the backend environment variables, then run from the repository root:

```bash
chmod +x backend/mvnw scripts/*.sh
./scripts/build.sh
java -jar backend/target/shopify-dashboard.jar
```

`./scripts/run.sh` is an interactive alternative for supplying configuration.

Open **http://127.0.0.1:8080**. Stop with **Ctrl+C**. The application binds to loopback and is intended for local use; no deployment is required.

## Development with IntelliJ and VS Code

1. Open `backend/pom.xml` as a Maven project in IntelliJ, select JDK 17+, and set the four environment variables in the `DashboardApplication` Run configuration.
2. Run `DashboardApplication`; the backend listens on `127.0.0.1:8080`.
3. In a separate terminal:

```powershell
cd frontend
npm.cmd ci
npm.cmd run dev
```

Open **http://127.0.0.1:5173**. Vite proxies `/api` to the backend. React changes reload automatically; restart the backend after Java changes. For the single-JAR workflow, rebuild React before Maven packaging.

## Features

| Screen | Functionality |
| --- | --- |
| Store | Read-only name, email, myshopify domain, currency, primary domain, and time zone |
| Products | Cursor pagination; title, handle, status, tags, variants, SKU, price, and available inventory per location; additional variant/location pages |
| Product editing | Save title, description HTML, tags, and ACTIVE/DRAFT/ARCHIVED status to Shopify |
| Orders | Order number, order contact, total/currency, financial and fulfillment statuses, and creation date |
| Order editing | Save or clear note and tags in Shopify |
| Fulfillment | Check eligibility, then fulfill all remaining accessible merchant-managed items at one location with tracking number, carrier, tracking URL, and notification choice |

Every screen includes loading, empty, and API error states. Shopify mutation `userErrors` are presented as readable field messages.

Customer display uses the order's billing name, falling back to its shipping name, with the source labeled. This avoids requiring `read_customers`, which is absent from the assessment's supplied scope list. Missing names show "Guest or customer unavailable". Protected customer data access remains subject to Shopify's permissions. Standard order access covers the last 60 days.

Fulfillment uses `fulfillmentCreate` and rechecks the order immediately before writing. Cancelled orders, already fulfilled orders, inaccessible locations, held/scheduled work, unsupported actions, fulfillment-service locations, and multiple-location work show a clear reason. Individual partial quantities and multiple shipments are outside scope. No inventory quantities or location assignments are edited directly.

`notifyCustomer` asks Shopify to notify the order's customer; no SMTP configuration or hardcoded recipient is used. Failed or uncertain fulfillment writes require a fresh eligibility check before another attempt.

## Tests and troubleshooting

- Frontend: `npm.cmd test` on Windows or `npm test` on Linux, from `frontend` (Vitest and React Testing Library).
- Backend: `mvnw.cmd clean verify` on Windows or `./mvnw clean verify` on Linux, from `backend` (JUnit, Mockito, MockMvc, and a loopback HTTP transport test).
- Backend test reports: `backend/target/surefire-reports/`; coverage: `backend/target/site/jacoco/index.html`.
- Formatting: `npm run format:check` from `frontend`.

Automated tests use artificial data and never modify a live Shopify store. Live updates and fulfillment should be checked with designated test records and an appropriate customer-notification choice.

Backend logs include request IDs, operation names, HTTP statuses, and failure locations; browser console logs show the corresponding `X-Request-ID`. Credentials, tokens, and request/response bodies are omitted.

- **Unexpected backend response / Vite 502:** confirm the backend is running on port 8080. An empty or HTML proxy response cannot be parsed as JSON.
- **Authentication failure:** verify the actual app credentials, installation, and organization. A client ID is not an access token. Restart after changing environment variables.
- **Java startup failure:** check `java -version` and `JAVA_HOME`; Windows may resolve an obsolete Java launcher before the intended JDK.
- **Test server cannot establish loopback connection:** this is an environment/JDK socket failure; use a working supported JDK and check local networking. Do not treat a skipped transport test as a full pass.

## Structure

```text
backend/    REST controllers, services, Shopify transport, GraphQL resources, tests
frontend/   React screens, shared components, API boundary, tests
scripts/    Windows and Linux build/run helpers
```

No database, user-management screens, deployment, or production infrastructure is included.

## References

- [Shopify client credentials grant](https://shopify.dev/docs/apps/build/authentication-authorization/client-credentials-grant)
- [Shopify Admin GraphQL 2026-07](https://shopify.dev/docs/api/admin-graphql/2026-07)
- [fulfillmentCreate](https://shopify.dev/docs/api/admin-graphql/2026-07/mutations/fulfillmentCreate)
