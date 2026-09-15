import { act, cleanup, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { StrictMode } from 'react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App.tsx'

type TestProfileProgress = {
  profileId: string
  currencies: {
    coinPurse: number | null
    bankBalance: number | null
    motesPurse: number | null
  }
  equipment: Array<{
    category: string
    itemId: string
    name: string
  }>
  skills: Array<{
    name: string
    level: number
    totalExperience: number
    experienceIntoLevel: number
    experienceForNextLevel: number | null
  }>
  collections: Array<{
    itemId: string
    name: string
    totalAmount: number
    tier: number
    amountIntoTier: number
    amountForNextTier: number | null
  }>
}

const player = {
  uuid: 'player-uuid',
  displayName: 'ExamplePlayer',
  firstLogin: 1_000,
  lastLogin: 2_000,
}

const profiles = [{
  profileId: 'apple-profile',
  name: 'Apple',
  selected: true,
  gameMode: null,
}]

const profileProgress: TestProfileProgress = {
  profileId: 'apple-profile',
  currencies: {
    coinPurse: 123,
    bankBalance: 456,
    motesPurse: null,
  },
  equipment: [{
    category: 'Armor',
    itemId: 'TEST_HELMET',
    name: 'Test Helmet',
  }],
  skills: [{
    name: 'Mining',
    level: 10,
    totalExperience: 5_000,
    experienceIntoLevel: 200,
    experienceForNextLevel: 1_000,
  }],
  collections: [{
    itemId: 'COBBLESTONE',
    name: 'Cobblestone',
    totalAmount: 2_500,
    tier: 4,
    amountIntoTier: 500,
    amountForNextTier: 2_500,
  }],
}

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
})

describe('player lookup flow', () => {
  it('refreshes the current profile when the same username is submitted again', async () => {
    const fetchMock = installProfileFlow(profileProgress)
    const router = renderApp('/players/ExamplePlayer/profiles/apple-profile')
    await screen.findByText('123 coins')

    await userEvent.setup().click(screen.getByRole('button', { name: 'Find player' }))

    await screen.findByText('123 coins')
    expect(requestedUrls(fetchMock).filter((url) => url.endsWith('/progress'))).toHaveLength(2)
    expect(router.state.location.pathname).toBe('/players/ExamplePlayer/profiles/apple-profile')
  })

  it('retries a rate-limited profile request on a same-username submission', async () => {
    let progressRequests = 0
    installFetch(async (url) => {
      if (url.startsWith('/api/minecraft/players/')) {
        return jsonResponse({ uuid: player.uuid, username: player.displayName })
      }
      if (url === '/api/players/player-uuid') return jsonResponse(player)
      if (url.endsWith('/profiles')) return jsonResponse(profiles)
      if (url.endsWith('/progress')) {
        progressRequests++
        return progressRequests === 1
          ? jsonResponse({ retryAfterSeconds: 1 }, 429)
          : jsonResponse(profileProgress)
      }
      throw new Error(`Unexpected request: ${url}`)
    })
    renderApp('/players/ExamplePlayer/profiles/apple-profile')
    await screen.findByText(/Hypixel's request limit has been reached/)

    await userEvent.setup().click(screen.getByRole('button', { name: 'Find player' }))

    expect(await screen.findByText('123 coins')).toBeInTheDocument()
    expect(progressRequests).toBe(2)
    expect(screen.queryByText(/Hypixel's request limit has been reached/)).not.toBeInTheDocument()
  })

  it('loads a direct profile URL under StrictMode effect replay', async () => {
    installProfileFlow(profileProgress)
    renderApp('/players/ExamplePlayer/profiles/apple-profile', true)
    expect(await screen.findByText('123 coins')).toBeInTheDocument()
    expect(screen.queryByText('Searching...')).not.toBeInTheDocument()
  })

  it('ignores an old profile response even when fetch completes after being aborted', async () => {
    let finishOldRequest: (response: Response) => void = () => { throw new Error('No pending request') }
    let oldRequestStarted = false
    installFetch(async (url) => {
      if (url.startsWith('/api/minecraft/players/')) {
        return jsonResponse({ uuid: player.uuid, username: player.displayName })
      }
      if (url === '/api/players/player-uuid') return jsonResponse(player)
      if (url.endsWith('/profiles')) {
        return jsonResponse([...profiles, { ...profiles[0], profileId: 'pear-profile', name: 'Pear', selected: false }])
      }
      if (url.endsWith('/apple-profile/progress')) {
        oldRequestStarted = true
        return new Promise<Response>((resolve) => { finishOldRequest = resolve })
      }
      if (url.endsWith('/pear-profile/progress')) {
        return jsonResponse({ ...profileProgress, profileId: 'pear-profile', currencies: { ...profileProgress.currencies, coinPurse: 999 } })
      }
      throw new Error(`Unexpected request: ${url}`)
    })
    renderApp('/players/ExamplePlayer/profiles/apple-profile')
    await waitFor(() => expect(oldRequestStarted).toBe(true))
    await userEvent.setup().click(screen.getByRole('link', { name: /Pear.*Profile/ }))
    await screen.findByText('999 coins')

    await act(async () => { finishOldRequest(await jsonResponse(profileProgress)) })

    expect(screen.getByText('999 coins')).toBeInTheDocument()
    expect(screen.queryByText('123 coins')).not.toBeInTheDocument()
  })

  it('selects the default profile when returning to the same player without a profile ID', async () => {
    installProfileFlow(profileProgress)
    const router = renderApp('/players/ExamplePlayer/profiles/apple-profile')
    await screen.findByText('123 coins')

    await act(async () => { await router.navigate('/players/ExamplePlayer') })

    await waitFor(() => expect(router.state.location.pathname)
      .toBe('/players/ExamplePlayer/profiles/apple-profile'))
    expect(await screen.findByText('123 coins')).toBeInTheDocument()
  })

  it('respects a profile route chosen while the player lookup is still pending', async () => {
    let finishProfiles: (response: Response) => void = () => { throw new Error('No pending request') }
    let profilesRequested = false
    installFetch(async (url) => {
      if (url.startsWith('/api/minecraft/players/')) {
        return jsonResponse({ uuid: player.uuid, username: player.displayName })
      }
      if (url === '/api/players/player-uuid') return jsonResponse(player)
      if (url.endsWith('/profiles')) {
        profilesRequested = true
        return new Promise<Response>((resolve) => { finishProfiles = resolve })
      }
      if (url.endsWith('/pear-profile/progress')) return jsonResponse({ ...profileProgress, profileId: 'pear-profile' })
      throw new Error(`Unexpected request: ${url}`)
    })
    const router = renderApp('/players/ExamplePlayer')
    await waitFor(() => expect(profilesRequested).toBe(true))
    await act(async () => { await router.navigate('/players/ExamplePlayer/profiles/pear-profile') })
    await act(async () => {
      finishProfiles(await jsonResponse([...profiles, { ...profiles[0], profileId: 'pear-profile', name: 'Pear', selected: false }]))
    })

    expect(await screen.findByText('123 coins')).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/players/ExamplePlayer/profiles/pear-profile')
  })

  it('loads a player and navigates to the selected profile', async () => {
    const fetchMock = installProfileFlow(profileProgress)
    const router = renderApp('/players/ExamplePlayer')

    expect(await screen.findByRole('heading', { name: 'ExamplePlayer' }))
      .toBeInTheDocument()
    expect(await screen.findByText('123 coins')).toBeInTheDocument()
    expect(screen.getByText('Test Helmet')).toBeInTheDocument()
    expect(router.state.location.pathname)
      .toBe('/players/ExamplePlayer/profiles/apple-profile')
    expect(requestedUrls(fetchMock)).toEqual([
      '/api/minecraft/players/ExamplePlayer',
      '/api/players/player-uuid',
      '/api/players/player-uuid/profiles',
      '/api/players/player-uuid/profiles/apple-profile/progress',
    ])
  })

  it('shows an unavailable state for a direct private profile route', async () => {
    installProfileFlow({
      ...profileProgress,
      currencies: { coinPurse: null, bankBalance: null, motesPurse: null },
      equipment: [],
      skills: [],
      collections: [],
    })
    renderApp('/players/ExamplePlayer/profiles/apple-profile')

    expect(await screen.findByText(
      'Detailed profile data is unavailable. The player may have disabled SkyBlock API access.',
    )).toBeInTheDocument()
    expect(screen.getAllByText('Unavailable')).toHaveLength(3)
  })

  it('clears the previous player when the next lookup fails', async () => {
    installFetch(async (url) => {
      if (url === '/api/minecraft/players/MissingPlayer') {
        return jsonResponse({ message: 'Not found' }, 404)
      }
      if (url === '/api/minecraft/players/ExamplePlayer') {
        return jsonResponse({ uuid: 'player-uuid', username: 'ExamplePlayer' })
      }
      if (url === '/api/players/player-uuid') return jsonResponse(player)
      if (url === '/api/players/player-uuid/profiles') return jsonResponse([])
      throw new Error(`Unexpected request: ${url}`)
    })
    renderApp('/players/ExamplePlayer')
    expect(await screen.findByRole('heading', { name: 'ExamplePlayer' }))
      .toBeInTheDocument()

    const user = userEvent.setup()
    const username = screen.getByRole('textbox', { name: 'Minecraft username' })
    await user.clear(username)
    await user.type(username, 'MissingPlayer')
    await user.click(screen.getByRole('button', { name: 'Find player' }))

    expect(await screen.findByText('Could not find that Minecraft username'))
      .toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'ExamplePlayer' }))
      .not.toBeInTheDocument()
  })

  it('aborts an older lookup when the route changes', async () => {
    let slowSignal: AbortSignal | null = null
    installFetch(async (url, init) => {
      if (url === '/api/minecraft/players/SlowPlayer') {
        return await new Promise<Response>((_resolve, reject) => {
          slowSignal = init?.signal ?? null
          if (!slowSignal) {
            reject(new Error('Expected an abort signal'))
            return
          }
          slowSignal.addEventListener(
            'abort',
            () => reject(new DOMException('Aborted', 'AbortError')),
            { once: true },
          )
        })
      }
      if (url === '/api/minecraft/players/FastPlayer') {
        return jsonResponse({ uuid: 'fast-uuid', username: 'FastPlayer' })
      }
      if (url === '/api/players/fast-uuid') {
        return jsonResponse({ ...player, uuid: 'fast-uuid', displayName: 'FastPlayer' })
      }
      if (url === '/api/players/fast-uuid/profiles') return jsonResponse([])
      throw new Error(`Unexpected request: ${url}`)
    })
    const router = renderApp('/players/SlowPlayer')
    await waitFor(() => expect(slowSignal).not.toBeNull())

    await act(async () => {
      await router.navigate('/players/FastPlayer')
    })

    expect(await screen.findByRole('heading', { name: 'FastPlayer' }))
      .toBeInTheDocument()
    expect(isAborted(slowSignal)).toBe(true)
    expect(screen.queryByRole('heading', { name: 'SlowPlayer' }))
      .not.toBeInTheDocument()
  })

  it('shows the retry delay from a rate-limited response', async () => {
    installFetch(async (url) => {
      if (url === '/api/minecraft/players/ExamplePlayer') {
        return jsonResponse({ uuid: 'player-uuid', username: 'ExamplePlayer' })
      }
      if (url === '/api/players/player-uuid') {
        return jsonResponse(
          {
            message: "Hypixel's request limit has been reached.",
            retryAfterSeconds: 17,
          },
          429,
          { 'Retry-After': '17' },
        )
      }
      throw new Error(`Unexpected request: ${url}`)
    })
    renderApp('/players/ExamplePlayer')

    expect(await screen.findByText(
      "Hypixel's request limit has been reached. Try again in 17 seconds.",
    )).toBeInTheDocument()
  })
})

function renderApp(initialPath: string, strict = false) {
  const router = createMemoryRouter(
    [{ path: '*', element: <App /> }],
    { initialEntries: [initialPath] },
  )
  render(strict ? <StrictMode><RouterProvider router={router} /></StrictMode> : <RouterProvider router={router} />)
  return router
}

function installProfileFlow(progress: TestProfileProgress) {
  return installFetch(async (url) => {
    if (url === '/api/minecraft/players/ExamplePlayer') {
      return jsonResponse({ uuid: 'player-uuid', username: 'ExamplePlayer' })
    }
    if (url === '/api/players/player-uuid') return jsonResponse(player)
    if (url === '/api/players/player-uuid/profiles') return jsonResponse(profiles)
    if (url === '/api/players/player-uuid/profiles/apple-profile/progress') {
      return jsonResponse(progress)
    }
    throw new Error(`Unexpected request: ${url}`)
  })
}

function installFetch(
  handler: (url: string, init?: RequestInit) => Promise<Response>,
) {
  const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const url = typeof input === 'string' ? input : input.toString()
    return handler(url, init)
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function requestedUrls(fetchMock: ReturnType<typeof installFetch>) {
  return fetchMock.mock.calls.map(([input]) => (
    typeof input === 'string' ? input : input.toString()
  ))
}

function isAborted(signal: AbortSignal | null) {
  return signal?.aborted ?? false
}

function jsonResponse(
  body: unknown,
  status = 200,
  headers: HeadersInit = {},
) {
  return Promise.resolve(new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...headers },
  }))
}
