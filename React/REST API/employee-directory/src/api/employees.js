export const API_URL = 'https://dummyjson.com/users'

// Calls the REST API and returns the users array from the JSON response
export async function fetchEmployees() {
  const response = await fetch(API_URL)
  if (!response.ok) {
    throw new Error(`The server responded with ${response.status} ${response.statusText}`.trim())
  }
  const data = await response.json()
  return data.users ?? []
}
