# Test cases and verification

## What the automated tests establish

The automated suite exercises application behavior without contacting a live Shopify store. It checks both successful operations and failures, including cases where HTTP 200 still contains a GraphQL error. Assertions inspect outgoing mutation variables, so a test checks what would be sent to Shopify rather than only a returned success message.

| Backend class | Cases | Coverage of behavior |
| --- | --- | --- |
| ShopifyClientTest | 24 | Form encoding, token cache and expiry, concurrent acquisition, late invalidation, one 401 retry, HTTP failures, malformed responses, top-level errors, userErrors, throttling and uncertain writes |
| JdkShopifyTransportTest | 1 | Real loopback HTTP request, exact body/header and no redirect following |
| CatalogServiceTest | 15 | Cursor forwarding, bounds, product variables, clearing fields, nested pagination, numeric IDs, not-found and order contact fallback |
| FulfillmentServiceTest | 20 | Remaining quantities, one-location shipment, notification choice, blocked statuses, service locations, cancellation, stale state, pagination, URL validation and concurrent submissions |
| DashboardIntegrationTest | 11 | Spring context, MVC validation, actual services/client with fake transport, error response contract, no secret exposure, local-origin filter and SPA routes |
| ShopifySettingsTest | 7 | JVM configuration, endpoint validation, fixed API version and nonrevealing toString |
| Total | 78 | No failures or errors in the verified run |

| Frontend area | Cases | Coverage of behavior |
| --- | --- | --- |
| api.test.js | 8 | Backend-only paths, JSON writes, visible errors, no mutation retry, invalid JSON, cursors, tags and URLs |
| screens.test.jsx | 18 | Loading/empty/error on all screens, router navigation, pagination, product and order writes, HTML safety, fulfillment and recheck after failure |
| inventory.test.jsx | 2 | More variants, more locations, zero and negative quantities |
| Total | 28 | No failures in the verified run |

## How to run

From `backend`: `./mvnw test` on Linux, or `.\mvnw.cmd test` on Windows. Use `verify` for the coverage report as well. From `frontend`: `npm ci`, then `npm test`. Build the complete application with `scripts/build.sh` or `scripts/build.ps1`.

Test reports are generated under `backend/target/surefire-reports`. Open `backend/target/site/jacoco/index.html` after `verify` to inspect line and branch coverage. Coverage is a diagnostic, not proof that an integration works with every live store.

Mockito uses its subclass mock maker because the application only needs to mock ordinary classes and interfaces. This avoids dynamic agent attachment requirements on restricted JDK environments. The setting is test-only in `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker`.

## Manual live acceptance checklist

Perform this on the development store with valid backend credentials. Automated tests do not make live changes. These steps intentionally change test data, so use disposable products and test orders.

1. Build from a clean clone, run the exact JVM command and open the local URL.
2. Check Store name, email, domains and currency against Shopify admin.
3. Open Products. Compare a variant's SKU, price and available quantity at every location.
4. Select a smaller page size, move Next and Previous, then load more variants and locations where present.
5. Edit a test product's title, HTML description, tags and each supported status. Verify each saved value in Shopify admin after refreshing.
6. Clear all tags and the description. Confirm old values were removed.
7. Open Orders. Check the number, contact source, amount, currency, statuses and creation date against a recent test order.
8. Save a note and tags; then clear both. Verify Shopify holds the same data.
9. Create a test order assigned to one merchant-managed location. Enter tracking number, carrier and URL. Leave notifications off for the first test. Confirm only one fulfillment exists in Shopify and all eligible remaining items were included.
10. On a separate test order, choose customer notification. Use a test customer address you control in Shopify. Confirm the option reaches Shopify; delivery of email is managed by Shopify, not this app.
11. Try an already fulfilled order, a cancelled order, an order on hold and one with remaining items at multiple locations. Confirm clear explanations and no unintended shipment.
12. Refresh after a successful fulfillment. Confirm that Shopify's current fulfillment status is displayed and no remaining shipment can be created for completed items.
13. Verify unknown or guest names display clearly. If protected-data errors occur, check the app's permissions rather than adding credentials to browser code.
14. In browser DevTools Network, filter requests by `api`. They should target the local backend only. No client secret or access token should appear in response bodies, page source or built JavaScript.
15. Stop the backend and refresh a screen. Confirm an actionable network error. Restart with wrong test credentials and confirm a safe authentication error with no raw secret.

## Verification boundaries

Verified in the development workspace: Java compilation, 78 backend tests, 28 frontend tests and the Vite production build. The Chromium browser smoke check passed using artificial API data and the packaged local application: direct route load, navigation, product save, fulfillment submit and a 390-pixel mobile layout, with no browser errors or body overflow. Windows commands are provided and reviewed; the current verification environment is Linux. No live Shopify store mutation, notification delivery, Windows execution or GitHub publication is implied by these results.

HTTP and GraphQL contracts can evolve independently of mocks. Keep versioned queries aligned with official Shopify documentation and complete the manual live acceptance checklist before the final assessment submission.
