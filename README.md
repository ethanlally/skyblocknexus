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

## Bazaar API

With the backend running, open `http://localhost:8080/api/bazaar/products` or run:

```powershell
Invoke-RestMethod http://localhost:8080/api/bazaar/products
```

This returns Hypixel's `lastUpdated` timestamp (Unix milliseconds) and a
`products` list sorted by product ID. Each product includes `productId`,
`buyPrice`, `sellPrice`, `buyVolume`, `sellVolume`, `buyMovingWeek`,
`sellMovingWeek`, `buyOrders`, and `sellOrders` from Hypixel's `quick_status`.
Prices keep Hypixel's names and are weighted summary prices, not guaranteed
execution prices; `movingWeek` includes the previous seven days plus live state.
See the [Hypixel Bazaar documentation](https://api.hypixel.net/#tag/SkyBlock/paths/~1v2~1skyblock~1bazaar/get).

Bazaar requests do not require or send an API key. They reuse the existing
60-second in-memory cache, timeouts, and shared local rate-limit guard. Failed
or malformed snapshots are not cached. The timestamp is Hypixel's snapshot
time, not the time you requested it; cached results are not real-time quotes.
The fixture used by tests is synthetic, not a captured market snapshot.
The Bazaar UI, calculated metrics, database, and historical collection come in
later commits; the existing profile page is unchanged.

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
