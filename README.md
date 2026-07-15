# SkyBlock Nexus

A personal project for exploring Hypixel SkyBlock data. The first version is a
Spring Boot API and React page that can look up a player by Minecraft UUID.

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
