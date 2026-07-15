import { useState, type FormEvent } from 'react'
import './App.css'

type Player = {
  uuid: string
  displayName: string
  firstLogin: number | null
  lastLogin: number | null
}

function App() {
  const [uuid, setUuid] = useState('')
  const [player, setPlayer] = useState<Player | null>(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  async function findPlayer(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setLoading(true)
    setError('')
    setPlayer(null)

    try {
      const response = await fetch(`/api/players/${uuid.trim()}`)
      if (!response.ok) throw new Error('Could not find that player')
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
        <label htmlFor="uuid">Minecraft UUID</label>
        <div className="search-row">
          <input
            id="uuid"
            value={uuid}
            onChange={(event) => setUuid(event.target.value)}
            placeholder="Enter a dashed or undashed UUID"
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

export default App
