import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, Navigate, Route, Routes, useNavigate, useParams } from 'react-router'
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

type SkyBlockProfile = {
  profileId: string
  name: string
  selected: boolean
  gameMode: string | null
}

type SkillProgress = {
  name: string
  level: number
  totalExperience: number
  experienceIntoLevel: number
  experienceForNextLevel: number | null
}

type CollectionProgress = {
  itemId: string
  name: string
  totalAmount: number
  tier: number
  amountIntoTier: number
  amountForNextTier: number | null
}

type ProfileProgress = {
  profileId: string
  skills: SkillProgress[]
  collections: CollectionProgress[]
}

type ApiError = {
  message?: string
  retryAfterSeconds?: number
}

function App() {
  return (
    <Routes>
      <Route path="/" element={<PlayerPage />} />
      <Route path="/players/:routeUsername" element={<PlayerPage />} />
      <Route
        path="/players/:routeUsername/profiles/:profileId"
        element={<PlayerPage />}
      />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

function PlayerPage() {
  const { routeUsername, profileId } = useParams()
  const navigate = useNavigate()
  const loadedUsername = useRef<string | null>(null)
  const [username, setUsername] = useState(routeUsername ?? '')
  const [player, setPlayer] = useState<Player | null>(null)
  const [profiles, setProfiles] = useState<SkyBlockProfile[]>([])
  const [profileProgress, setProfileProgress] = useState<ProfileProgress | null>(null)
  const [progressError, setProgressError] = useState('')
  const [progressLoading, setProgressLoading] = useState(false)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const loadPlayer = useCallback(async (requestedUsername: string) => {
    setLoading(true)
    setError('')
    setPlayer(null)
    setProfiles([])
    setProfileProgress(null)

    try {
      const minecraftResponse = await fetch(
        `/api/minecraft/players/${encodeURIComponent(requestedUsername)}`,
      )
      if (!minecraftResponse.ok) {
        throw new Error(
          minecraftResponse.status === 404
            ? 'Could not find that Minecraft username'
            : 'Could not resolve that Minecraft username',
        )
      }

      const minecraftPlayer = await minecraftResponse.json() as MinecraftPlayer
      const playerResponse = await fetch(`/api/players/${minecraftPlayer.uuid}`)
      const foundPlayer = await readHypixelResponse<Player>(
        playerResponse,
        'Could not find that player',
      )

      const profilesResponse = await fetch(`/api/players/${minecraftPlayer.uuid}/profiles`)
      const foundProfiles = await readHypixelResponse<SkyBlockProfile[]>(
        profilesResponse,
        'Could not load SkyBlock profiles',
      )

      setPlayer(foundPlayer)
      setProfiles(foundProfiles)

      if (!profileId) {
        const defaultProfile = foundProfiles.find((profile) => profile.selected) ?? foundProfiles[0]
        if (defaultProfile) {
          navigate(profilePath(requestedUsername, defaultProfile.profileId), { replace: true })
        }
      }
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Something went wrong')
    } finally {
      setLoading(false)
    }
  }, [navigate, profileId])

  useEffect(() => {
    if (!routeUsername) {
      loadedUsername.current = null
      setUsername('')
      setPlayer(null)
      setProfiles([])
      setProfileProgress(null)
      setError('')
      return
    }

    setUsername(routeUsername)
    const normalizedUsername = routeUsername.toLowerCase()
    if (loadedUsername.current === normalizedUsername) return

    loadedUsername.current = normalizedUsername
    void loadPlayer(routeUsername)
  }, [loadPlayer, routeUsername])

  useEffect(() => {
    const profileExists = profiles.some((profile) => profile.profileId === profileId)
    if (!player || !profileId || !profileExists) {
      setProfileProgress(null)
      setProgressError('')
      setProgressLoading(false)
      return
    }

    const controller = new AbortController()
    setProfileProgress(null)
    setProgressError('')
    setProgressLoading(true)

    void fetch(
      `/api/players/${player.uuid}/profiles/${encodeURIComponent(profileId)}/progress`,
      { signal: controller.signal },
    )
      .then((response) => readHypixelResponse<ProfileProgress>(
        response,
        'Could not load profile progress',
      ))
      .then(setProfileProgress)
      .catch((requestError: unknown) => {
        if (requestError instanceof DOMException && requestError.name === 'AbortError') return
        setProgressError(
          requestError instanceof Error ? requestError.message : 'Could not load profile progress',
        )
      })
      .finally(() => {
        if (!controller.signal.aborted) setProgressLoading(false)
      })

    return () => controller.abort()
  }, [player, profileId, profiles])

  async function findPlayer(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const requestedUsername = username.trim()

    if (routeUsername?.toLowerCase() === requestedUsername.toLowerCase()) {
      const defaultProfile = profiles.find((profile) => profile.selected) ?? profiles[0]
      if (defaultProfile) {
        navigate(profilePath(requestedUsername, defaultProfile.profileId))
      } else {
        loadedUsername.current = requestedUsername.toLowerCase()
        await loadPlayer(requestedUsername)
      }
      return
    }

    navigate(`/players/${encodeURIComponent(requestedUsername)}`)
  }

  const profileRouteIsUnknown = Boolean(
    profileId && profiles.length > 0
      && !profiles.some((profile) => profile.profileId === profileId),
  )

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

      {player && (
        <section className="profiles">
          <h2>SkyBlock profiles</h2>
          {profileRouteIsUnknown && (
            <p className="error">That profile is not available for this player.</p>
          )}
          {profiles.length === 0 ? (
            <p>No SkyBlock profiles found.</p>
          ) : (
            <ul>
              {profiles.map((profile) => (
                <li key={profile.profileId}>
                  <Link
                    to={profilePath(routeUsername ?? username, profile.profileId)}
                    className={profile.profileId === profileId ? 'profile-link active' : 'profile-link'}
                    aria-current={profile.profileId === profileId ? 'page' : undefined}
                  >
                    <strong>{profile.name}</strong>
                    <span>
                      {profile.selected ? 'Current profile' : 'Profile'}
                      {profile.gameMode && ` - ${formatGameMode(profile.gameMode)}`}
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </section>
      )}

      {progressLoading && <p className="progress-status">Loading profile progress...</p>}
      {progressError && <p className="error">{progressError}</p>}
      {profileProgress && (
        <ProfileProgressDetails progress={profileProgress} />
      )}
    </main>
  )
}

function ProfileProgressDetails({ progress }: { progress: ProfileProgress }) {
  const highlightedCollections = progress.collections.slice(0, 12)
  const remainingCollections = progress.collections.slice(12)

  return (
    <section className="profile-progress">
      <div>
        <h2>Skills</h2>
        {progress.skills.length === 0 ? (
          <p>No skill data is available for this profile.</p>
        ) : (
          <ul className="progress-grid">
            {progress.skills.map((skill) => (
              <li key={skill.name} className="progress-card">
                <div className="progress-heading">
                  <strong>{skill.name}</strong>
                  <span>Level {skill.level}</span>
                </div>
                {skill.experienceForNextLevel === null ? (
                  <span className="progress-copy">{formatNumber(skill.totalExperience)} total XP</span>
                ) : (
                  <>
                    <progress
                      value={skill.experienceIntoLevel}
                      max={skill.experienceForNextLevel}
                    />
                    <span className="progress-copy">
                      {formatNumber(skill.experienceIntoLevel)} /{' '}
                      {formatNumber(skill.experienceForNextLevel)} XP
                    </span>
                  </>
                )}
              </li>
            ))}
          </ul>
        )}
      </div>

      <div>
        <h2>Collections</h2>
        {progress.collections.length === 0 ? (
          <p>No collection data is available for this profile.</p>
        ) : (
          <>
            <CollectionProgressGrid collections={highlightedCollections} />
            {remainingCollections.length > 0 && (
              <details className="more-collections">
                <summary>Show {remainingCollections.length} more collections</summary>
                <CollectionProgressGrid collections={remainingCollections} />
              </details>
            )}
          </>
        )}
      </div>
    </section>
  )
}

function CollectionProgressGrid({ collections }: { collections: CollectionProgress[] }) {
  return (
    <ul className="progress-grid">
      {collections.map((collection) => (
        <li key={collection.itemId} className="progress-card">
          <div className="progress-heading">
            <strong>{collection.name}</strong>
            <span>Tier {collection.tier}</span>
          </div>
          {collection.amountForNextTier === null ? (
            <span className="progress-copy">
              {formatNumber(collection.totalAmount)} collected
            </span>
          ) : (
            <>
              <progress
                value={collection.amountIntoTier}
                max={collection.amountForNextTier}
              />
              <span className="progress-copy">
                {formatNumber(collection.amountIntoTier)} /{' '}
                {formatNumber(collection.amountForNextTier)} to next tier
              </span>
            </>
          )}
        </li>
      ))}
    </ul>
  )
}

function profilePath(username: string, profileId: string) {
  return `/players/${encodeURIComponent(username)}/profiles/${encodeURIComponent(profileId)}`
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

async function readHypixelResponse<T>(response: Response, fallbackMessage: string): Promise<T> {
  if (response.ok) return await response.json() as T

  const apiError = await readApiError(response)
  if (response.status === 429) {
    const retryAfter = apiError?.retryAfterSeconds
      ?? Number(response.headers.get('Retry-After'))
    throw new Error(
      `Hypixel's request limit has been reached. Try again in ${formatRetryDelay(retryAfter)}.`,
    )
  }
  throw new Error(apiError?.message ?? fallbackMessage)
}

function formatRetryDelay(seconds: number) {
  if (!Number.isFinite(seconds) || seconds <= 0) return 'about a minute'
  if (seconds < 60) return `${Math.ceil(seconds)} seconds`

  const minutes = Math.ceil(seconds / 60)
  return `${minutes} ${minutes === 1 ? 'minute' : 'minutes'}`
}

function formatGameMode(gameMode: string) {
  return gameMode.charAt(0).toUpperCase() + gameMode.slice(1)
}

function formatNumber(value: number) {
  return new Intl.NumberFormat().format(Math.round(value))
}

export default App
