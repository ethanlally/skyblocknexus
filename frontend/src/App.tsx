import { useState, type FormEvent } from 'react'
import './App.css'

type Player = {
  uuid: string
  displayName: string
  firstLogin: number | null
  lastLogin: number | null
}

type MinecraftPlayer = {
  uuid: string
  username: string
}

type ApiError = {
  message?: string
  retryAfterSeconds?: number
}

function App() {
  const [username, setUsername] = useState('')
  const [player, setPlayer] = useState<Player | null>(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  async function findPlayer(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setLoading(true)
    setError('')
    setPlayer(null)

    try {
      const minecraftResponse = await fetch(
        `/api/minecraft/players/${encodeURIComponent(username.trim())}`,
      )
      if (!minecraftResponse.ok) {
        throw new Error(
          minecraftResponse.status === 404
            ? 'Could not find that Minecraft username'
            : 'Could not resolve that Minecraft username',
        )
      }

      const minecraftPlayer = await minecraftResponse.json() as MinecraftPlayer
      const response = await fetch(`/api/players/${minecraftPlayer.uuid}`)
      if (!response.ok) {
        const apiError = await readApiError(response)
        if (response.status === 429) {
          const retryAfter = apiError?.retryAfterSeconds
            ?? Number(response.headers.get('Retry-After'))
          throw new Error(
            `Hypixel's request limit has been reached. Try again in ${formatRetryDelay(retryAfter)}.`,
          )
        }
        throw new Error(apiError?.message ?? 'Could not find that player')
      }
      setPlayer(await response.json() as Player)
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Something went wrong')
    } finally {
      setLoading(false)
    }
  }

  return (
    <main>
      <h1>SkyBlock Nexus</h1>
      <p className="intro">A small project for exploring Hypixel SkyBlock data.</p>

      <form onSubmit={findPlayer}>
        <label htmlFor="username">Minecraft username</label>
        <div className="search-row">
          <input
            id="username"
            value={username}
            onChange={(event) => setUsername(event.target.value)}
            placeholder="Enter a Minecraft username"
            minLength={3}
            maxLength={16}
            pattern="[A-Za-z0-9_]+"
            required
          />
          <button type="submit" disabled={loading}>
            {loading ? 'Searching...' : 'Find player'}
          </button>
        </div>
      </form>

      {error && <p className="error">{error}</p>}

      {player && (
        <section className="result">
          <h2>{player.displayName}</h2>
          <dl>
            <div><dt>UUID</dt><dd>{player.uuid}</dd></div>
            <div><dt>First login</dt><dd>{formatDate(player.firstLogin)}</dd></div>
            <div><dt>Last login</dt><dd>{formatDate(player.lastLogin)}</dd></div>
          </dl>
        </section>
      )}
    </main>
  )
}

function formatDate(timestamp: number | null) {
  return timestamp ? new Date(timestamp).toLocaleDateString() : 'Unknown'
}

async function readApiError(response: Response): Promise<ApiError | null> {
  try {
    return await response.json() as ApiError
  } catch {
    return null
  }
}

function formatRetryDelay(seconds: number) {
  if (!Number.isFinite(seconds) || seconds <= 0) return 'about a minute'
  if (seconds < 60) return `${Math.ceil(seconds)} seconds`

  const minutes = Math.ceil(seconds / 60)
  return `${minutes} ${minutes === 1 ? 'minute' : 'minutes'}`
}

export default App
