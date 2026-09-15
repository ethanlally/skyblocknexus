# SkyBlock Nexus

A personal project for exploring Hypixel SkyBlock data. The current version is a
Spring Boot API and React page that can look up a player by Minecraft username
and browse their SkyBlock profiles with shareable profile URLs.
Selected profiles show skill levels and collection tier progress calculated
from Hypixel's current game resource definitions, along with currencies and a
summary of equipped armor and equipment. Missing or private profile data is
shown with a clear unavailable state instead of leaving older lookup data
visible.

## Setup

You will need Java 21, Node.js, pnpm, and a Hypixel API key.

`.env.example` lists the required environment variable. Set your API key in the
terminal before starting the backend:

```powershell
$env:HYPIXEL_API_KEY="your-key-here"
```

Run the backend:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Run the frontend in another terminal:

```powershell
cd frontend
pnpm install
pnpm dev
```

The frontend will be available at `http://localhost:5173`.

## Checks

Run the backend tests from `backend`:

```powershell
.\mvnw.cmd --batch-mode verify
```

Run the frontend tests, lint, and build from `frontend`:

```powershell
pnpm test
pnpm lint
pnpm build
```

The backend tracks Hypixel's rate-limit response headers in memory. When the
current request window is exhausted, it responds locally with HTTP 429 and a
retry delay instead of sending another request to Hypixel.

Successful Hypixel responses are cached in memory for 60 seconds, up to 100
recent entries. The cache reduces repeated API requests and clears whenever the
backend restarts.

Submit the same username again to refresh the current profile or retry a failed
lookup. Refreshes still use the backend cache until its 60-second TTL expires.
Outgoing Minecraft and Hypixel requests have a 5-second connection timeout and
a 10-second read timeout. Upstream failures return HTTP 502, timeouts return
HTTP 504, and missing profile data keeps its existing unavailable or
HTTP 404 response.
