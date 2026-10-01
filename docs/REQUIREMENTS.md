# Assessment requirement checklist

| Requirement | Implementation | Verification |
| --- | --- | --- |
| Run locally without deployment | Loopback-only embedded server; no hosting or deployment configuration | Runnable JAR and local browser smoke check |
| Latest stable Spring Boot | Version 4.1.1 pinned in Maven; verified against Spring's stable listing on 1 October 2026 | Maven compile and Spring context tests |
| React in JavaScript | JSX components with React 19.3 and React Router 8.4 | Vitest and Vite build |
| Backend owns Shopify calls | Browser calls relative `/api` paths; backend owns transport | API boundary tests and source review |
| API 2026-07 | Required version enforced by `ShopifySettings` | Configuration test and request-path assertion |
| Client ID and secret are not an access token | Form POST with client credentials grant; in-memory token cache and renewal | Token transport, expiry, concurrency and 401 tests |
| Credentials from JVM arguments | `SHOPIFY_*` system properties, environment fallback | System-property test and README exact launch commands |
| No committed credentials | Blank `.env.example`; ignored local config; artificial test values only | Source/archive review |
| Email configuration | No application email input or SMTP; Shopify supplies shop email and sends customer notifications | README explanation and fulfillment notification tests |
| Store read-only fields | Name, email, myshopify domain, currency, primary domain, timezone | Store integration and UI state tests |
| Product cursor pagination | `first`, `after`, `pageInfo`, opaque cursor history | Backend variable test and UI navigation test |
| Product fields and inventory | Title, handle, status, tags, variants, SKU, price, quantity at each location | Query review and nested pagination tests |
| Product edits saved to Shopify | `productUpdate` for title, descriptionHtml, tags, status | Backend mutation and frontend form tests |
| Order fields | Number, order contact, total/currency, financial/fulfillment status, createdAt | Query and UI review |
| Customer display under supplied scopes | Billing name then shipping name, source labeled; no read_customers request | Contact fallback test; limitations documented |
| Note and tags editable | `orderUpdate`, including clearing both fields | Mutation and UI tests |
| Fulfill remaining items | Fresh eligibility check then `fulfillmentCreate` | Fulfillment service tests |
| Tracking and notify choice | Number, carrier, valid URL, explicit boolean | DTO, mutation and UI tests |
| Merchant-managed workflow only | Location type, status and supportedActions checked | Blocked and fulfillment-service tests |
| No partial or multiple-shipment workflow | All remaining eligible orders at one location; clear refusal otherwise | Hold, multiple-location and all-items tests |
| Loading, empty, API errors | Shared state components on every screen | Parameterized frontend tests |
| Plain-language GraphQL userErrors | Field labels and messages returned as 422 | Client, integration and UI tests |
| Source, README and env example | Included at repository root | Package inventory |
| Walkthrough, local setup and Git information | Learning guide, setup guide, test record and Word versions | Documentation review |
| Public GitHub link | https://github.com/Ahsan-sys/shopify-local-dashboard | Public repository; source can be cloned with the README command |

The contact name is taken from the order record because the supplied scopes omit `read_customers`. It does not claim to be a separately fetched customer profile. Protected customer data access and live store permissions must be validated on the reviewer’s installed app.

The API reference's `latest` route may move forward. All runtime GraphQL requests remain on `2026-07` as requested.
