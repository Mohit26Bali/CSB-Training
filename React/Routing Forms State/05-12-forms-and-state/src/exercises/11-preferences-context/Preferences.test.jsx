import { render, renderHook, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import PreferencesApp from './PreferencesApp.jsx'
import { DEFAULT_PREFERENCES, STORAGE_KEY, loadPreferences, usePreferences } from './UserPreferencesContext.jsx'

beforeEach(() => localStorage.clear())

describe('UserPreferencesContext', () => {
  it('starts with the defaults', () => {
    render(<PreferencesApp />)
    expect(screen.getByLabelText('Current theme')).toHaveTextContent('Theme: light')
    expect(screen.getByText('Welcome back, Asha')).toBeInTheDocument()
  })

  it('Settings updates the shared preferences, and Header, Dashboard and Profile all follow', async () => {
    const user = userEvent.setup()
    render(<PreferencesApp />)
    await user.selectOptions(screen.getByLabelText('Theme'), 'dark')
    await user.selectOptions(screen.getByLabelText('Language'), 'hi')
    await user.click(screen.getByLabelText('Email notifications'))

    expect(screen.getByLabelText('Current theme')).toHaveTextContent('Theme: dark')          // Header
    expect(screen.getByText('फिर से स्वागत है, Asha')).toBeInTheDocument()                      // Header greeting
    expect(screen.getByText('Showing the dark theme.')).toBeInTheDocument()                  // Dashboard
    expect(screen.getByText('Disabled')).toBeInTheDocument()                                  // Profile
    expect(screen.getByTestId('themed-area')).toHaveClass('prefs--dark')                      // colours change
  })

  it('saves to localStorage and restores on the next start', async () => {
    const user = userEvent.setup()
    const { unmount } = render(<PreferencesApp />)
    await user.selectOptions(screen.getByLabelText('Language'), 'ta')
    expect(JSON.parse(localStorage.getItem(STORAGE_KEY))).toEqual({ theme: 'light', language: 'ta', notifications: true })

    unmount()
    render(<PreferencesApp />)                                                                 // "restart"
    expect(screen.getByLabelText('Language')).toHaveValue('ta')
  })

  it('ignores broken or invalid saved data (invalid input)', () => {
    localStorage.setItem(STORAGE_KEY, '{not json')
    expect(loadPreferences()).toEqual(DEFAULT_PREFERENCES)
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ theme: 'purple', language: 'fr', notifications: 'yes' }))
    expect(loadPreferences()).toEqual(DEFAULT_PREFERENCES)
  })

  it('usePreferences outside the provider fails with a clear message', () => {
    expect(() => renderHook(() => usePreferences())).toThrow('usePreferences must be used inside <UserPreferencesProvider>')
  })
})
