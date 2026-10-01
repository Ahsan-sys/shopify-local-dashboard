# Shopify Dashboard Local Setup Guide

This guide takes you from a downloaded project to a running dashboard on Windows or Linux. It also explains how to test, troubleshoot, use Git and prepare the public repository for the assessment. The application runs on your own computer and communicates with Shopify over HTTPS. Nothing needs to be deployed.

## 1 Understand what you need

| Tool or resource | Purpose | Version or condition |
| --- | --- | --- |
| Java Development Kit | Compiles and runs Spring Boot | JDK 17 or newer; JDK 21 is suitable |
| Node.js and npm | Installs, tests and builds React | Node.js 24 LTS recommended; package minimum 22.12 |
| Git | Clones the source and publishes commits | A current supported version |
| Maven Wrapper | Downloads and uses the pinned build tool | Included; Maven 3.9.11 |
| Shopify app | Supplies client ID and secret | Installed with the assessment scopes |
| Shopify development store | Holds products and test orders | App and store in the same organization |
| Internet connection | Downloads dependencies and reaches Shopify | Needed for initial build and live use |

You do not need MariaDB, a separate Apache Tomcat, Nginx, Docker, an email server or a Shopify access token copied into the code. Spring Boot embeds Tomcat in the JAR. Shopify stores the business data.

The frontend source uses JavaScript and JSX. React is compiled into ordinary browser assets. Once the complete JAR has been built, Node is not needed to run that JAR.

## 2 Install tools on Windows

Install a JDK from a reputable vendor such as Eclipse Temurin, Node.js 24 LTS from the Node.js website and Git for Windows. During installation, enable PATH integration where offered. Open a new PowerShell terminal after installation so it receives the updated PATH.

Run these checks:

```powershell
java -version
javac -version
node --version
npm.cmd --version
git --version
```

Both `java` and `javac` should exist. A Java Runtime Environment alone may run an existing JAR but cannot compile the project. If Java is missing, point `JAVA_HOME` to the JDK installation directory and add its `bin` directory to PATH through Windows environment-variable settings. `JAVA_HOME` is the JDK directory, not the path to `java.exe`.

Use a normal writable working directory, for example `C:\Projects`. Avoid editing files inside Program Files. You can use IntelliJ IDEA, Eclipse, VS Code or any editor; the command-line build remains the reference.

## 3 Install tools on Linux

Install a JDK and Git through your distribution's package manager. For Ubuntu or Debian, these commands are a common starting point:

```bash
sudo apt update
sudo apt install openjdk-21-jdk git curl unzip
java -version
javac -version
git --version
```

Install Node.js 24 LTS using the official Node.js instructions or your existing version manager. Distribution repositories may contain an older Node version, so verify it instead of assuming it is recent enough:

```bash
node --version
npm --version
```

If your distribution does not offer JDK 21, use a supported JDK 17 or later. The project targets Java 17 bytecode. Build as a normal user; running npm or Maven with sudo is unnecessary and can create ownership problems in the project directory.

## 4 Get the source

After the public repository exists, clone its real URL:

```bash
git clone https://github.com/YOUR_USERNAME/shopify-local-dashboard.git
cd shopify-local-dashboard
```

`YOUR_USERNAME` is a placeholder. If you received a ZIP, extract it and open a terminal in the extracted `shopify-local-dashboard` directory instead. The root directory contains README.md, backend, frontend, scripts, docs and .env.example.

On Linux, files extracted from an archive may lose executable permissions:

```bash
chmod +x backend/mvnw scripts/*.sh
```

## 5 Build the complete application

The easiest route runs both test suites and produces the complete executable JAR.

Linux, from the repository root:

```bash
./scripts/build.sh
```

Windows PowerShell, from the repository root:

```powershell
.\scripts\build.ps1
```

If PowerShell blocks scripts, the following manual commands give the same result without changing the machine-wide execution policy:

```powershell
cd frontend
npm.cmd ci
npm.cmd test
npm.cmd run build
cd ..\backend
.\mvnw.cmd clean verify
cd ..
```

The Linux manual equivalent is:

```bash
cd frontend
npm ci
npm test
npm run build
cd ../backend
./mvnw clean verify
cd ..
```

`npm ci` installs the exact dependency tree recorded in package-lock.json. `npm test` runs frontend tests. `npm run build` creates frontend/dist. Maven compiles Java, runs JUnit, copies the React build into static resources and packages the application. `verify` also produces the JaCoCo coverage report.

The completed file is `backend/target/shopify-dashboard.jar`. A successful Maven build ends with BUILD SUCCESS. If a test fails, fix the cause before submission; do not treat skipping tests as a solution.

Build React before packaging Java. If Maven runs first, the JAR can contain the backend without the frontend assets. The build scripts enforce the right sequence.

## 6 Start with your own JVM arguments

Use the app's real client ID and secret from the Shopify Dev Dashboard. They are different from an access token. The shop value is only the subdomain: if a shop were `example-shop.myshopify.com`, use `example-shop`.

Linux exact command, run from the repository root:

```bash
java "-DSHOPIFY_SHOP=your-shop-subdomain" \
  "-DSHOPIFY_CLIENT_ID=your-client-id" \
  "-DSHOPIFY_CLIENT_SECRET=your-client-secret" \
  "-DSHOPIFY_API_VERSION=2026-07" \
  -jar backend/target/shopify-dashboard.jar
```

Windows PowerShell exact command:

```powershell
java "-DSHOPIFY_SHOP=your-shop-subdomain" `
  "-DSHOPIFY_CLIENT_ID=your-client-id" `
  "-DSHOPIFY_CLIENT_SECRET=your-client-secret" `
  "-DSHOPIFY_API_VERSION=2026-07" `
  -jar .\backend\target\shopify-dashboard.jar
```

Place all `-D` properties before `-jar`. They are JVM system properties, not arguments to the Java application's main method. In PowerShell, a continuation backtick must be the last character on the line. You can put the entire command on one line if continuation syntax causes trouble.

For actual secrets, the interactive launchers are easier and avoid literal secrets in shell history:

```bash
./scripts/run.sh
```

```powershell
.\scripts\run.ps1
```

The launchers ask for the shop, client ID and secret, then pass them to Java using the same `-D` properties. Secret input is hidden. The operating system can still expose JVM arguments to authorized local process inspection, so use a trusted local computer.

Open `http://127.0.0.1:8080`. Stop the server with Ctrl+C in its terminal. Do not close that terminal while using the dashboard.

## 7 Know where configuration belongs

`.env.example` is a reference file containing variable names and blank credential values. The application does not automatically load `.env`. Spring can also read the same names from the backend environment, but JVM system properties take priority.

Keep secrets out of application.properties, source files, frontend code, screenshots, README examples and Git commits. Variables beginning with `VITE_` are intended for browser builds and must never contain a Shopify secret or access token.

No email address needs to be configured. Store email is returned by Shopify. The customer-notification checkbox sends `notifyCustomer` to Shopify, which uses the order's existing customer contact. The dashboard has no SMTP integration and no hidden recipient.

If you change credentials, restart the Java process. The token cache is in memory and will be recreated. API version is fixed to 2026-07 for this assessment even when Shopify's documentation has a newer latest version.

## 8 Use the screens

Store is read only. Compare its name, email, domains, currency and time zone with Shopify admin. A missing field is labeled instead of inventing a value.

Products displays the first page of products with initial variants and stock locations. Use Next and Previous to move between product pages. Use Load more variants or More locations for nested lists. Zero stock is shown as zero, and untracked inventory is explicitly labeled. Prices are in store currency.

Choose Edit product to change the title, HTML description, tags or status. Tags use one line per tag. Clear the tags area to remove all tags. HTML remains text in the editor and is not executed. Save waits for Shopify to accept the mutation; errors stay visible and your form values remain available for correction.

Orders displays recent orders accessible to the app. Customer/contact names use the order's billing name, then shipping name, with the source labeled. This avoids requesting an extra customer-profile scope. Standard order access covers 60 days. Names can be unavailable for guest or restricted records.

Edit note and tags works like product editing. For fulfillment, open Fulfill remaining items. The backend checks whether a single shipment is possible. If eligible, enter number, carrier and a complete HTTP or HTTPS tracking URL. Choose whether to notify the customer, then confirm. If a failure leaves the outcome uncertain, recheck status before trying again.

Fulfillment changes real store data. Practice with disposable development-store orders. The dashboard does not reverse or cancel a fulfillment.

## 9 Run tests and inspect results

Backend only, from backend:

```bash
./mvnw test
./mvnw verify
./mvnw -Dtest=FulfillmentServiceTest test
```

Windows equivalents:

```powershell
.\mvnw.cmd test
.\mvnw.cmd verify
.\mvnw.cmd "-Dtest=FulfillmentServiceTest" test
```

Frontend, from frontend:

```bash
npm test
npm run test:watch
```

Maven's individual results are under `backend/target/surefire-reports`. Open `backend/target/site/jacoco/index.html` after verify for coverage. Frontend test results are printed in the terminal. Tests use artificial responses and need no real credentials, email address or store access.

The verified suite contains 78 backend cases and 28 frontend cases. Parameterized tests count each input case separately. Read `docs/TESTING.md` for the case matrix and live acceptance checklist. Linux was used for execution; Windows instructions have been reviewed but were not executed in that environment.

## 10 Work on frontend changes

Keep the backend running at port 8080. In a second terminal run:

```bash
cd frontend
npm run dev
```

Open `http://127.0.0.1:5173`. Vite updates the page as JavaScript or CSS changes. The browser calls local `/api` URLs, and Vite proxies them to Spring Boot. It never needs Shopify credentials.

For a Java change, stop the backend, run Maven package in backend and start the JAR again. For a final single-JAR build, stop the server and rerun the full build script. Avoid testing an old JAR after changing source.

An IDE can import `backend/pom.xml` as a Maven project. Put `-DSHOPIFY_SHOP=...` and the other properties in the run configuration's VM options, not Program arguments. Keep local run configurations out of Git. Open frontend separately or as another folder in the same workspace.

## 11 Troubleshoot common problems

| Symptom | Likely cause | Action |
| --- | --- | --- |
| java or javac not found | JDK missing or PATH stale | Install a JDK and reopen the terminal |
| Unsupported class version | Wrong Java selected | Check java -version and JAVA_HOME |
| Vite refuses Node version | Old Node release | Switch to Node 24 LTS |
| npm.ps1 blocked | PowerShell script policy | Run npm.cmd |
| mvnw permission denied | ZIP lost executable bit | chmod +x backend/mvnw |
| Dependency download fails | Network, proxy or certificate issue | Configure your organization's trusted proxy or CA; do not disable TLS verification |
| Port 8080 already in use | Another process owns the port | Stop that process or temporarily use -Dserver.port=8081 |
| Page blank after Java build | React was not built first | Build frontend, then repackage backend |
| Authentication failed | Wrong values, app not installed or different organization | Check JVM properties and Shopify app/store ownership |
| Access denied | Missing scope or protected-data permission | Check the installed app version and Shopify data settings |
| No old orders | Standard 60-day order limit | Use recent test orders; read_all_orders is outside the supplied contract |
| No fulfillable items | Routing, completed work, inaccessible fulfillment orders or holds | Check the Shopify order and refresh |
| Multiple locations message | One shipment would require different origins | Fulfill through Shopify; multiple shipments are out of scope |
| Rate limited | Shopify query-cost budget exhausted | Wait briefly, then refresh; avoid repeated clicks |
| Timeout after a write | Shopify may have completed it | Inspect current Shopify state before retrying |

If you change the backend port for the packaged app, open the matching URL. For Vite development you must also update its `/api` proxy target. Stay on a loopback host. Do not make the app publicly reachable to solve a local connection problem.

## 12 Understand Git before publishing

Git records snapshots of tracked files. Your working directory contains current edits. The staging area contains changes chosen for the next commit. A commit is a named snapshot with an author and message. GitHub hosts the shared remote repository.

| Command | Meaning |
| --- | --- |
| git status | Show branch and changed/staged files |
| git diff | Show unstaged changes |
| git diff --cached | Show exactly what the next commit will contain |
| git add path | Stage selected changes |
| git commit -m "message" | Create a local snapshot |
| git log --oneline -5 | Show recent commits |
| git remote -v | Show remote names and URLs |
| git fetch origin | Download remote history without changing your files |
| git pull --ff-only | Update the branch only if it can fast-forward |
| git push | Upload local commits to the configured remote |
| git switch -c feature/name | Create and switch to a feature branch |
| git restore --staged path | Unstage a file while keeping its edits |
| git revert COMMIT_ID | Create a new commit that reverses an earlier commit |

Set your real commit identity locally if Git asks for it. This email is Git author metadata; it is not application configuration. Public commits can expose it, so you may use the GitHub-provided no-reply address from your account settings.

```bash
git config user.name "Your Name"
git config user.email "YOUR_GITHUB_NOREPLY_ADDRESS"
```

`.gitignore` excludes generated files and local secrets from normal staging. The repository keeps `.env.example` but ignores `.env` and other real environment files. It ignores target, dist, node_modules, coverage, logs, key files and editor settings. package-lock.json and Maven Wrapper files stay tracked so others can reproduce the build. `.gitattributes` keeps shell scripts using LF line endings and Windows scripts using CRLF.

If a file was already committed, adding it to .gitignore does not remove it from tracking or history. For an ordinary accidentally tracked generated file, `git rm --cached PATH` stops tracking while keeping the local file. If it was a secret, rotate the secret immediately and then clean the repository history carefully; a later delete commit alone does not remove the exposure.

## 13 Publish a public repository

Create an empty public repository in your GitHub account. Avoid initializing it with a second README when you already have this project locally. GitHub visibility must be Public because the assessor intends to clone it.

For a ZIP download with no existing Git history:

```bash
git init -b main
git add .
git diff --cached --stat
git diff --cached
git commit -m "Build local Shopify management dashboard"
git remote add origin https://github.com/YOUR_USERNAME/shopify-local-dashboard.git
git push -u origin main
```

Review the staged files before committing. Do not commit credentials, generated JARs, node_modules or local logs. Authenticate through a credential manager, SSH setup or GitHub CLI. Do not embed a GitHub token in the repository URL.

If you cloned an existing repository, use `git status` and `git remote -v` first. Do not run git init or add the same origin again. Commit your changes and push to the appropriate branch. If GitHub reports that the remote already has commits, fetch and inspect the history instead of force-pushing blindly.

Use a feature branch for later changes:

```bash
git switch -c feature/improve-order-message
# Edit and test the relevant code.
git add frontend/src/pages/OrdersPage.jsx
git commit -m "Clarify unavailable fulfillment message"
git push -u origin feature/improve-order-message
```

Open a pull request to review and merge. If a merge conflict occurs, inspect both versions, edit the file to the intended combined result, remove conflict markers, rerun tests, stage the resolved file and complete the merge. Do not select all incoming or current changes without reading them.

For an unpublished mistake, first inspect `git status` and `git diff`. `git restore --staged file` safely unstages. `git restore file` discards local edits in that file, so use it only when you intentionally want to lose them. Prefer `git revert` for reversing a published commit because it preserves shared history. Avoid `reset --hard` and force push unless you understand their consequences.

## 14 Prepare the assessment submission

Clone the public repository into a new directory. Build it using only README instructions. Run it with your own JVM arguments, then complete the live development-store checklist in TESTING.md. Confirm the repository can be opened without signing in and contains no credentials.

Submit the actual repository URL. The source repository is enough; there should be no deployed dashboard URL. Before the follow-up call, practice the walkthrough in LEARNING_GUIDE.md and explain any live-store permission limitation accurately.

## Official installation and reference links

- Java distributions and installation: https://adoptium.net/installation/
- Node.js downloads: https://nodejs.org/en/download
- Git downloads: https://git-scm.com/downloads
- Maven Wrapper: https://maven.apache.org/wrapper/
- Shopify client credentials: https://shopify.dev/docs/apps/build/authentication-authorization/client-credentials-grant
- Git ignore documentation: https://git-scm.com/docs/gitignore
- GitHub repository creation: https://docs.github.com/en/repositories/creating-and-managing-repositories/creating-a-new-repository
