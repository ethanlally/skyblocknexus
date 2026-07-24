# SkyBlock Nexus

A personal project for exploring Hypixel SkyBlock data. The current version is a
Spring Boot API and React page that can look up a player by Minecraft username
and browse their SkyBlock profiles with shareable profile URLs.
Selected profiles show skill levels and collection tier progress calculated
from Hypixel's current game resource definitions, along with currencies and a
summary of equipped armor and equipment.

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

The backend tracks Hypixel's rate-limit response headers in memory. When the
current request window is exhausted, it responds locally with HTTP 429 and a
retry delay instead of sending another request to Hypixel.
