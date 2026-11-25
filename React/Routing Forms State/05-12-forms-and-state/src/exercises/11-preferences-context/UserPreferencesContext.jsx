import { createContext, useContext, useEffect, useState } from 'react'

export const STORAGE_KEY = 'employee-preferences'
export const DEFAULT_PREFERENCES = { theme: 'light', language: 'en', notifications: true }
export const THEMES = ['light', 'dark']
export const LANGUAGES = { en: 'English', hi: 'हिन्दी (Hindi)', ta: 'தமிழ் (Tamil)' }

const UserPreferencesContext = createContext(null)

/** Reads saved preferences; anything missing, invalid or unreadable falls back to the defaults. */
export function loadPreferences() {
  try {
    const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? 'null')
    if (!saved || typeof saved !== 'object') return DEFAULT_PREFERENCES
    return {
      theme: THEMES.includes(saved.theme) ? saved.theme : DEFAULT_PREFERENCES.theme,
      language: saved.language in LANGUAGES ? saved.language : DEFAULT_PREFERENCES.language,
      notifications: typeof saved.notifications === 'boolean' ? saved.notifications : DEFAULT_PREFERENCES.notifications,
    }
  } catch {
    return DEFAULT_PREFERENCES
  }
}

export function UserPreferencesProvider({ children }) {
  const [preferences, setPreferences] = useState(loadPreferences)     // restored once, at start-up

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(preferences))   // saved after every change
    } catch {
      // storage can be unavailable (private mode); the app still works for this session
    }
  }, [preferences])

  const updatePreference = (name, value) => setPreferences((current) => ({ ...current, [name]: value }))

  return (
    <UserPreferencesContext.Provider value={{ preferences, updatePreference }}>
      {children}
    </UserPreferencesContext.Provider>
  )
}

/** Any component below the provider can read and update the preferences, with no props passed down. */
export function usePreferences() {
  const context = useContext(UserPreferencesContext)
  if (!context) throw new Error('usePreferences must be used inside <UserPreferencesProvider>')
  return context
}
