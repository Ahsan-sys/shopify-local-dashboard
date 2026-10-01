# Shopify Dashboard Learning and Walkthrough Guide

This guide explains the completed assessment application in easy wording. Read it with the source open. The goal is to understand the decisions, follow each important code path and explain the trade-offs in your interview. The companion Local Setup Guide contains Windows and Linux commands, Git instructions and troubleshooting.

The application is a small local administration tool. React displays the store, products and orders. Spring Boot validates requests, applies fulfillment rules and talks to Shopify. Shopify remains the source of truth. A successful save means Shopify accepted the change, not merely that a browser variable changed.

## 1 What Shopify does and what the assessment asks

Shopify is a commerce platform. A merchant can use it to maintain a catalog, track inventory, receive customer orders, take payments through configured payment providers and organize fulfillment. A storefront is the customer-facing shopping experience. Shopify admin is the merchant-facing management experience.

This assessment asks you to build a small alternative management interface for selected admin operations. It is not a storefront, a checkout or a payment gateway. Customers do not sign in to this dashboard. The installed app acts on one development store using backend credentials.

| Shopify concept | Easy meaning | How this project uses it |
| --- | --- | --- |
| Shop | The merchant's store account | Read name, email, domains, currency and timezone |
| Product | A catalog item such as a T-shirt | List and edit the requested descriptive fields |
| Variant | One sellable version such as Blue Medium | Display title, SKU and price |
| SKU | Merchant's stock identifier | Show it; do not change it |
| Inventory item | Inventory identity associated with a variant | Find stock levels |
| Location | Warehouse, shop or fulfillment origin | Display available quantity per location |
| Order | The customer's purchase record | Show totals, statuses, contact, date, note and tags |
| Fulfillment order | Shopify's assignment of items to a location | Decide what can be fulfilled |
| Fulfillment | The record of items being fulfilled | Create one shipment with tracking information |

The distinction between an order and a fulfillment order matters. One purchase can be assigned to more than one warehouse. An ordinary order-line ID is not a fulfillment-order ID. Passing the wrong kind of ID to fulfillmentCreate is a common integration mistake.

Financial status and fulfillment status answer different questions. Financial status concerns payment. Fulfillment status concerns completion of fulfillment work. An order can be paid and still unfulfilled. This application does not mark an order paid, capture money, refund it or purchase a shipping label.

The editable fields are intentionally limited. Product title, HTML description, tags and status can change. Order note and tags can change. Variant prices, SKUs and inventory quantities are read only. Fulfillment includes tracking number, carrier, URL and whether Shopify should notify the customer.

## 2 The architecture and why it is small

The design has three major responsibilities: display, application policy and external integration. They are separated without introducing microservices or a local database.

| Layer | Main files | Responsibility |
| --- | --- | --- |
| React UI | App.jsx, pages, components | Navigation, forms, states and presenting results |
| Browser API boundary | api.js and hooks.js | Local HTTP calls, error handling, cancellation and cursor history |
| Spring MVC | DashboardController and Requests | Routes and input validation |
| Services | CatalogService and FulfillmentService | Allowed operations and shipment eligibility |
| Shopify adapter | ShopifyClient, TokenProvider, JdkShopifyTransport | GraphQL, token lifecycle and HTTPS |
| Query resources | resources/graphql/*.graphql | Explicit requested fields and mutation contracts |

When a user saves a product, React sends JSON to a local REST endpoint. The controller validates the input. CatalogService builds GraphQL variables. ShopifyClient obtains a token from TokenProvider and sends the request through the transport. It checks Shopify errors. Only then does the controller return the accepted result to React.

There is no database because the task does not need local persistence or offline operation. Adding one would create synchronization questions: which copy is correct, when should it refresh, and how would conflicts be resolved? Reading and writing Shopify directly keeps the assessment smaller and easier to review.

This is a modular monolith: one executable application with separate classes for different responsibilities. For one local user, that is simpler to run and debug than multiple backend services. Spring Boot embeds Tomcat, and the compiled React assets are bundled in the JAR. The reviewer therefore needs one Java process after building.

The application is not a GraphQL server. Its browser-facing API is REST with JSON. Its Shopify-facing API is GraphQL because Shopify's Admin API requires that contract. You can use different styles at different boundaries when each makes its consumer simpler.

## 3 The technologies and their jobs

Spring Boot 4.1.1 was the stable version verified for this build. Java source targets version 17, so a JDK 17 or later can compile it. The code uses constructor injection and Java records for request data. It does not use Lombok, JPA or a separate database layer.

Spring MVC maps HTTP routes to Java methods. Jakarta Validation checks required fields and sizes before a service runs. Jackson converts JSON to Java request records and reads Shopify response trees. The project uses Jackson 3, which is managed by Spring Boot 4.

The JDK HttpClient sends backend requests. It has connection and request timeouts and does not follow redirects. A small ShopifyTransport interface makes this boundary replaceable in tests. This keeps the business code independent of a particular HTTP library.

React 19.3 handles UI components and state. JavaScript was chosen as requested. JSX is JavaScript syntax that looks similar to HTML and describes the rendered interface. Vite runs the frontend development server and produces the optimized browser build. React Router 8.4 maps local URLs to the three screens.

JUnit Jupiter is the Java test programming model used by Spring Boot's test starter. Mockito provides controlled substitutes for selected collaborators. AssertJ makes assertions readable. MockMvc exercises the MVC request pipeline in a Spring context. Vitest and React Testing Library test the JavaScript boundary and user interactions.

Maven manages Java dependencies and packaging. npm manages frontend dependencies. Maven Wrapper and package-lock.json support reproducible builds. They solve related problems in different language ecosystems.

## 4 GraphQL in plain language

GraphQL is a language and execution model for asking an API for structured data. The server publishes a schema describing types, fields, arguments and operations. A client selects the fields it needs. The response normally follows that selection's shape.

A query reads data. A mutation requests a change. GraphQL also supports subscriptions for ongoing event streams, but this project does not need them. GraphQL is not a database and does not decide how a server stores its information.

Here is a small query:

```graphql
query StoreSummary {
  shop {
    name
    currencyCode
  }
}
```

Its JSON response can look like this artificial example:

```json
{
  "data": {
    "shop": {
      "name": "Example Store",
      "currencyCode": "USD"
    }
  }
}
```

`shop` is a field returning a shop object. `name` and `currencyCode` are selected fields inside that object. The client did not ask for every store property. This is useful when related information would otherwise require several REST resources or a large fixed response.

GraphQL does not guarantee faster performance. Deep selections can be expensive, and Shopify charges query cost. A single large request can be worse than a few small requests. This application uses bounded product pages and loads additional variants and inventory locations only when needed.

### Variables instead of string concatenation

The query structure should stay constant while values are supplied separately:

```graphql
query ProductPage($first: Int!, $after: String) {
  products(first: $first, after: $after) {
    nodes { id title handle }
    pageInfo { hasNextPage endCursor }
  }
}
```

```json
{ "first": 10, "after": null }
```

The exclamation mark means a value cannot be null. `Int!` requires a number for first. `String` allows after to be null for the first page. JSON variables also prevent a quoted title or HTML description from breaking the query text. They are not a replacement for server-side authorization or input validation.

The HTTP request body contains query and variables. The access token is in the backend request header, not in either query field and not in browser code. GraphQL documents live in dedicated resource files so they are easy to review beside Shopify's versioned schema.

## 5 Mutations and the three error layers

A product edit uses ProductUpdateInput. The backend supplies the Shopify global ID together with title, descriptionHtml, tags and status. The mutation returns selected product fields and userErrors.

```graphql
mutation ProductUpdate($product: ProductUpdateInput!) {
  productUpdate(product: $product) {
    product { id title status }
    userErrors { field message }
  }
}
```

There are three different places where failure can occur:

| Layer | Example | Application response |
| --- | --- | --- |
| Transport or HTTP | Timeout, 401, 403, 429 or 500 | Safe visible error; limited handling by status |
| Top-level GraphQL errors | Invalid field, access denial, throttling | Inspect errors even when HTTP is 200 |
| Mutation userErrors | Invalid value rejected by business rules | Show field and message as a 422 response |

HTTP 200 only means the HTTP exchange succeeded. It does not prove that the business operation succeeded. ShopifyClient.execute checks top-level errors. ShopifyClient.mutate checks userErrors and confirms that the expected result object exists. React shows the backend's message and details in a visible error component.

The frontend only shows a saved confirmation after that process completes. For example, `tags: Tag is too long` tells the user what needs attention. It does not hide the error in the browser console or show a success toast for a rejected mutation.

Unexpected authentication response bodies are never forwarded because they may contain sensitive information. The client also removes configured credentials from forwarded GraphQL messages. The API does not return Java stack traces to the UI.

## 6 How authentication works without a login screen

The client ID identifies the installed app. The client secret proves the app is allowed to authenticate. Neither is the access token used for ordinary Admin API calls.

TokenProvider sends a backend form POST to the shop's `/admin/oauth/access_token` endpoint with grant_type=client_credentials, client_id and client_secret. Shopify returns an access token and expiry information. ShopifyClient then sends that token as X-Shopify-Access-Token when calling the versioned GraphQL endpoint.

There is no user redirect because this grant authenticates the server-side app. The app and store must belong to the same Shopify organization. Installation and granted scopes still matter; avoiding a login screen does not remove authentication from the integration.

The token is held only in backend memory. It is reused until shortly before expiry, using expires_in from the response. A Clock is injected so tests can move time forward without sleeping. The synchronized token method ensures concurrent requests do not all request a fresh token at once.

If Shopify explicitly returns 401, the client invalidates the rejected token and tries once with a new token. Invalidation compares the rejected value, so a late failure cannot erase a newer token another request already obtained. Other HTTP errors and uncertain mutation timeouts are not automatically retried.

Do not describe this as an OAuth redirect flow, a permanent token or an application login system. It is a client credentials grant used by the backend.

## 7 JVM configuration and credential safety

A JVM system property is passed with `-DNAME=value` before `-jar`. ShopifySettings reads these values through Spring's Environment. JVM properties take precedence over environment variables. Missing credentials fail startup with a message naming the missing configuration, without printing its value.

```bash
java "-DSHOPIFY_SHOP=your-shop" \
  "-DSHOPIFY_CLIENT_ID=your-client-id" \
  "-DSHOPIFY_CLIENT_SECRET=your-secret" \
  "-DSHOPIFY_API_VERSION=2026-07" \
  -jar backend/target/shopify-dashboard.jar
```

The shop is validated as a subdomain. Users cannot supply an arbitrary URL that makes the backend send the client secret to another host. The API version must remain 2026-07. Redirect following is disabled for backend HTTP calls.

ShopifySettings is a normal class rather than a record because a generated record toString would include its fields, including the secret. Request records are appropriate for ordinary nonsecret form data. This is a small example of choosing a language feature according to the data's sensitivity.

The backend binds to loopback and checks local Host and Origin values on API requests. API responses use no-store. These are limited local protections, not a replacement for production authentication. The assessment explicitly excludes production infrastructure and user accounts.

No configurable email is needed. The shop email is Shopify data, and customer notifications are Shopify's responsibility. There is no hardcoded recipient in JavaScript or Java. Artificial example addresses in tests are not runtime configuration.

## 8 Cursor pagination and inventory

A cursor is an opaque bookmark created by Shopify. The application should return it unchanged rather than decode it or calculate an offset. The first request uses after=null. The response's endCursor becomes after for the next request. hasNextPage tells the UI whether another page exists.

The browser remembers the sequence of cursors used. Previous removes the current cursor from that history and reloads the earlier page. Changing page size clears the history. There is no invented total-page count because this implementation does not request a total count.

Products contain another connection for variants, and each inventory item contains a connection for inventory levels. Fetching only the first few nested nodes without a way to continue would silently hide data. This project returns pageInfo at each level and offers Load more variants and More locations.

Product pages default to 10 items, with a maximum of 20. Initial product results include up to 3 variants and 3 locations per variant. Additional pages are fetched on demand. Small nested selections help contain Shopify query cost and keep the initial UI responsive.

Available quantity is requested with `quantities(names: ["available"])`. It is not assumed to equal total stock, committed stock or incoming stock. Inventory not tracked, no stock locations, zero quantity and negative availability have different meanings and are displayed distinctly.

Variant prices are Shopify decimal strings. The UI displays the value with a store-currency label rather than using floating-point arithmetic to recalculate money. Order totals include the explicit currency code from MoneyV2 data.

## 9 React concepts in this application

A component is a function that returns UI. StorePage renders store information. ProductCard renders one product and its editing controls. Variants and Inventory handle nested lists. Reusable components such as ErrorNotice and Loading keep behavior consistent across screens.

Props are values passed from a parent component to a child. ProductCard receives its initial product through a prop. State is data the component remembers between renders: whether the editor is open, the current input values, whether a request is pending and the last error.

`useState` creates state and an update function. Calling the update function asks React to render with the new value. Do not mutate a state object in place and expect React to understand every change. The form uses a new object when a field changes.

```javascript
const [form, setForm] = useState({ title: '' });

function changeTitle(event) {
  setForm({ ...form, title: event.target.value });
}
```

The inputs are controlled: their value comes from state, and onChange updates that state. This makes it clear which data will be submitted. On Save, the handler prevents the browser's normal form submission, sends JSON to the backend and waits for the result.

`useEffect` runs the request lifecycle when a screen's path or refresh revision changes. `useApi` uses AbortController during cleanup. If a user navigates away or changes pages quickly, an old response must not overwrite the new screen. The hook checks the abort signal before updating state.

React StrictMode may run an extra effect setup/cleanup cycle in development. That helps reveal missing cleanup. Reads can therefore appear more than once in development, but mutations happen in user event handlers, not effects, so mounting a component does not fulfill an order.

The HTML description is edited in a textarea. The app does not insert it using dangerouslySetInnerHTML. Treating it as text avoids executing scripts or unsafe markup in the dashboard. If an HTML preview were added later, it would need a reviewed sanitization strategy.

## 10 React Router and why it is useful

React Router connects URLs to React screens. BrowserRouter uses browser history. Routes contains the route definitions. Route selects a component for a path. NavLink provides navigation and an active state so the sidebar shows which screen is open.

```jsx
<BrowserRouter>
  <Routes>
    <Route path="/store" element={<StorePage />} />
    <Route path="/products" element={<ProductsPage />} />
    <Route path="/orders" element={<OrdersPage />} />
  </Routes>
</BrowserRouter>
```

This produces useful URLs, lets the browser Back and Forward buttons work and changes screens without reloading the full document for every navigation. It also keeps page selection out of ad hoc boolean variables such as showProducts and showOrders.

Client routing creates a server requirement. Opening `/products` directly or pressing Refresh makes the browser ask Spring Boot for that path. SpaController forwards the three known screen routes to index.html. React then reads the path and renders the correct screen. API paths are not forwarded to the SPA.

The root route redirects to `/store`. Unknown in-app routes show a page-not-found message. This project uses the declarative router style; it does not need server rendering, route loaders or a React Router server framework. Those are separate options for different applications.

To add a new screen later, create the component, add its Route and NavLink, and add a matching backend SPA forward if it should support direct navigation. Add a UI navigation test so a route change does not break the user flow.

## 11 Fulfillment from preview to mutation

Fulfillment is the most important business rule in the project because it changes the shipping state of an order. A visible button alone is not proof that Shopify still allows the operation.

The preview endpoint loads the current order and every accessible fulfillment-order page. It also loads all line-item pages for remaining quantities. Closed and cancelled fulfillment orders are skipped. Orders that are cancelled or already fully fulfilled are rejected early.

For remaining work, the service checks that an assigned location is accessible and is not a fulfillment-service location. It checks OPEN or IN_PROGRESS status and the presence of CREATE_FULFILLMENT in supportedActions. A hold or scheduled item prevents the app from silently fulfilling only the available subset.

All remaining eligible work must have one location. Shopify does not allow one fulfillment to combine assignments from different locations. Multiple shipments are outside the assessment scope, so the service explains the limitation instead of making several calls that could partly succeed.

At submission, the browser sends only tracking data and notifyCustomer. It does not choose arbitrary fulfillment-order IDs or quantities. The backend reloads eligibility under a bounded lock for that order, then creates one mutation input:

```json
{
  "fulfillment": {
    "lineItemsByFulfillmentOrder": [
      { "fulfillmentOrderId": "gid://shopify/FulfillmentOrder/123" }
    ],
    "notifyCustomer": false,
    "trackingInfo": {
      "number": "EXAMPLE-TRACKING",
      "company": "Example carrier",
      "url": "https://example.test/tracking"
    }
  }
}
```

Omitting fulfillmentOrderLineItems is deliberate: Shopify fulfills all remaining items in each selected fulfillment order. The app does not offer partial quantities. It also does not perform fulfillment-service request/accept flows.

The lock pool prevents two same-order requests in this local JVM from racing through the check simultaneously. It is not distributed locking and cannot stop changes made through Shopify admin or another app. The fresh check reduces the risk, and Shopify's mutation validation is still authoritative.

If a network timeout occurs, Shopify might have completed the mutation even though the response was lost. Automatically retrying could duplicate an action. The UI therefore requires a status recheck after a failure. There is no claim of durable exactly-once delivery or a cross-system transaction.

## 12 Read the backend classes with a purpose

Start at DashboardApplication. `@SpringBootApplication` enables application configuration and component discovery. Spring creates the beans and supplies constructor dependencies. BackendConfig creates the settings, Clock and production transport.

DashboardController is intentionally thin. It maps routes and passes validated data to services. Requests contains the record shapes and constraints. A missing title, unknown status, excessive length or missing notification boolean is rejected before a Shopify call.

CatalogService keeps a whitelist of fields it sends to Shopify. A product edit cannot silently add a price or inventory change. It builds the correct global ID from a validated numeric path ID. It also maps order billing/shipping names into the frontend's customer display shape without querying a Customer profile.

FulfillmentService owns the fulfillment policy. It does not know React components or browser state. Its Plan record is a clear response: eligible, reason, location, remaining quantity and the selected fulfillment-order IDs. The POST operation calculates that plan again instead of accepting one sent by the client.

ShopifyClient handles the shared protocol rules. TokenProvider owns token lifetime. JdkShopifyTransport owns HTTP delivery. Queries loads fixed GraphQL resource files and caches their text. Keeping these responsibilities separate makes unit tests focused and future changes easier to explain.

ApiErrorAdvice converts Java exceptions into the common error body. LocalRequestFilter applies the local Host/Origin check and response headers. SpaController serves the React entry point for known screen URLs. None of these classes stores a credential in the browser.

## 13 Testing and how to explain a test

A useful test describes behavior that matters, then fails when that behavior breaks. It should not simply repeat the implementation's calculations and assert that they match themselves.

Use Arrange, Act and Assert. Arrange creates input and controlled collaborators. Act calls the operation. Assert checks the outcome and, when relevant, verifies which external call did or did not happen.

```java
@Test
void rejectsUnsafeTrackingUrlBeforeShopifyCalls() {
    var request = new Requests.Fulfill(
        "TRACK", "Carrier", "javascript:alert(1)", false);

    assertThatThrownBy(() -> service.fulfill("100", request))
        .isInstanceOf(ApiException.class);
    verifyNoInteractions(client);
}
```

This test checks two consequences: invalid input is rejected and Shopify is never contacted. The actual suite also verifies the all-items fulfillment input, one-location restriction, stale state, notification choice and simultaneous attempts.

Token tests use a fake Clock, so renewal tests do not sleep for an hour. Transport tests use a loopback HTTP server to verify the actual header and body. Integration tests start the Spring context and exercise MVC validation, service calls, JSON conversion and error mapping while replacing only the external transport.

Frontend tests interact with labels and buttons the way a user does. They check all three screen states, saving, pagination, visible errors, inert HTML, nested inventory and the required recheck after an uncertain fulfillment response. This makes them less dependent on CSS class names or internal component variables.

The verified counts are 78 backend cases and 28 frontend cases. JaCoCo shows coverage after Maven verify. These tests prove the application behaves as expected for the controlled inputs. They do not prove that a particular Shopify installation has correct permissions or that a customer email was delivered. The live checklist in TESTING.md covers those external concerns.

## 14 Git files and a sensible workflow

Git tracks source history. GitHub hosts a remote copy. A local commit is not uploaded until you push it. A public repository makes source visible; it does not deploy this application.

The important repository hygiene files are .gitignore, .gitattributes, package-lock.json and the Maven Wrapper files. .gitignore prevents normal staging of secrets and generated output. .gitattributes helps preserve suitable line endings across Windows and Linux. The lockfile and wrapper keep dependency resolution and the Maven version reproducible.

Typical work is: inspect status, create a branch, make a focused change, run relevant tests, review the diff, stage intended files, commit with a meaningful message and push for review. The Local Setup Guide gives the exact commands and explains safe recovery options.

Never assume .gitignore removes something already committed. If a credential reaches history, rotate it before relying on cleanup. Do not store a GitHub token in a remote URL. Use a credential manager or the GitHub CLI for publishing.

For this assessment, the reviewer should be able to clone a public repository, build it with the README and supply their own JVM values. A generated JAR or screenshot alone is insufficient because the reviewer needs to inspect and change the source.

## 15 Design trade offs you should be ready to defend

| Choice | Why it fits this assessment | Limitation |
| --- | --- | --- |
| Single Spring Boot application | Easy local startup and clear code ownership | Not a distributed production design |
| No database | Shopify remains authoritative | No offline data or local history |
| REST for browser and GraphQL for Shopify | Simple UI contract with Shopify's required integration | Two boundary styles to maintain |
| Explicit JSON response trees | Small amount of code for selected fields | Less compile-time schema checking than generated types |
| Client credentials token cache | Avoids repeated authentication calls | Cache lasts only for one process |
| Small cursor pages | Controls response size and query cost | More calls for large catalogs |
| One location per fulfillment | One clear shipment operation | Multiple shipments require Shopify admin |
| Fresh check plus local lock | Reduces stale state and duplicate clicks | Does not control external apps or restarts |
| No blind mutation retry | Avoids repeating uncertain side effects | User may need to inspect Shopify before retrying |
| Last-write-wins product and note edits | Matches the small scope and available mutation contract | Concurrent editors can overwrite the same fields |

If this grew into a shared production application, first reconsider identity, authorization, auditing, durable operation tracking, observability, rate-limit handling and conflict management. Those would solve new requirements; they are not reasons to add infrastructure to this local assessment prematurely.

Be clear about authorization limits. Standard scopes expose recent orders and permitted fulfillment orders. A missing name is not always a Java bug. Protected-data settings can restrict access even when the basic app installation is correct. The UI labels unavailable data and surfaces errors rather than inventing customer details.

## 16 Practice the technical walkthrough

For a ten-minute demonstration, start with the requirement and architecture in one minute. Show the README launch command and credential separation. Demonstrate Store, one product edit and one order edit. Then spend the most time on the fulfillment eligibility check, mutation variables and uncertain-outcome behavior. Finish by running a focused JUnit class and showing where the broader test results are recorded.

Open these files in advance: DashboardController, CatalogService, FulfillmentService, ShopifyClient, TokenProvider, products.graphql, fulfillment-create.graphql, App.jsx, hooks.js and one backend test. Explain what each owns rather than reading every line aloud.

### Questions and model answers

**Why can the frontend not call Shopify directly?** The client secret and access token would become accessible to browser users and scripts. Backend ownership also centralizes validation, fulfillment rules and error handling.

**Why not hardcode the development credentials?** The reviewer must run with their own values, and public Git history must not contain secrets. JVM properties configure the process without changing source.

**Why GraphQL?** Shopify's requested Admin API uses it. It also lets this application choose related fields and mutations explicitly. The trade-off is schema knowledge, nested pagination and query-cost management.

**How can a request return 200 but still fail?** HTTP succeeded, but GraphQL can return top-level errors or mutation userErrors. The backend checks both before declaring success.

**Why use variables?** Values stay separate from query syntax. Quotes, HTML and tags remain data. It also makes the query documents reusable and testable.

**Why not fetch all products at once?** Large nested requests cost more and slow the UI. Cursor pages and nested continuation controls handle larger data sets without silent truncation.

**How does Previous work with first and after?** The browser stores previously used cursors and reloads the earlier cursor. It does not pretend the cursor is an offset.

**How do you refresh a token?** Cache until shortly before expires_in is reached, then exchange client credentials again. An explicit 401 triggers one invalidation and retry; concurrent requests share synchronized acquisition.

**Why not retry every failed request?** A timeout can happen after Shopify applied a mutation. Repeating it may duplicate a side effect. The UI asks for a fresh check instead.

**Why is the fulfillment plan recalculated on POST?** The preview can become stale. Browser data is not authoritative, and another user or app may change the order between preview and submission.

**Why reject multiple locations?** Shopify requires a single fulfillment's fulfillment orders to share a location. Multiple shipment calls could partly succeed and are outside the requested scope.

**How are partial quantities avoided?** The UI has no quantity selection, and the mutation omits fulfillmentOrderLineItems so all remaining items in each selected fulfillment order are included.

**Does synchronized make fulfillment exactly once?** No. It only coordinates calls in this JVM. External apps, network uncertainty and process restarts remain outside that lock. Shopify validation and explicit user rechecks are still needed.

**How is a customer shown without read_customers?** The application reads the name on the order's billing address, falling back to shipping address, and labels that source. It does not claim to fetch a separate customer profile.

**What happens with guest or missing customer data?** The UI says Guest or customer unavailable. Protected-data authorization errors are displayed. It does not make up a name or request extra scopes silently.

**What does React Router add?** URLs select screens, navigation works without full reloads, and browser history behaves naturally. Spring forwards known screen paths to index.html for direct loads.

**Why use AbortController?** A response for an old screen or cursor should not overwrite current state. Cleanup aborts the obsolete read, and the hook ignores aborted results.

**How do you prevent HTML description scripts?** The description is only editor text in this dashboard. It is never inserted as executable HTML. A future preview would require sanitization.

**What are the strongest tests?** The all-items fulfillment payload, one-location and stale-state checks, no-retry behavior, token concurrency, MVC validation and visible mutation errors test the highest-risk boundaries.

**What has not been proven by mocks?** Live app permissions, the real store's data, Shopify accepting the exact operations on that store and email delivery. Those require the manual live acceptance run.

**What would you improve next if requirements expanded?** Start with the actual new need. Shared users would require authentication and authorization. Multiple shipments would require explicit per-location outcomes and recovery. Offline work would require persistence and synchronization.

Practice these answers in your own words. Be ready to change a small feature and its relevant test during the call. Understanding the data flow is more useful than memorizing class names.

## Official references for further study

- Shopify Admin GraphQL 2026-07: https://shopify.dev/docs/api/admin-graphql/2026-07
- Shopify client credentials grant: https://shopify.dev/docs/apps/build/authentication-authorization/client-credentials-grant
- Products and mutations: https://shopify.dev/docs/api/admin-graphql/2026-07/mutations/productUpdate
- Order updates: https://shopify.dev/docs/api/admin-graphql/2026-07/mutations/orderUpdate
- Fulfillment creation: https://shopify.dev/docs/api/admin-graphql/2026-07/mutations/fulfillmentCreate
- Fulfillment-order model: https://shopify.dev/docs/api/admin-graphql/2026-07/objects/FulfillmentOrder
- Inventory levels: https://shopify.dev/docs/api/admin-graphql/2026-07/objects/InventoryLevel
- Shopify pagination: https://shopify.dev/docs/api/usage/pagination-graphql
- Shopify limits: https://shopify.dev/docs/api/usage/limits
- Protected customer data: https://shopify.dev/docs/apps/launch/protected-customer-data
- GraphQL learning: https://graphql.org/learn/
- GraphQL queries: https://graphql.org/learn/queries/
- React learning: https://react.dev/learn
- React Router routing: https://reactrouter.com/start/declarative/routing
- Spring Boot configuration: https://docs.spring.io/spring-boot/reference/features/external-config.html
- Spring Boot testing: https://docs.spring.io/spring-boot/reference/testing/spring-boot-applications.html
- Git reference: https://git-scm.com/docs
