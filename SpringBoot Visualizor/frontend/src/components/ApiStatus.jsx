import { useEffect, useState } from 'react'
import client from '../api/client'

export default function ApiStatus() {
  const [status, setStatus] = useState('checking')

  useEffect(() => {
    client
      .get('/actuator/health')
      .then((res) => setStatus(res.data.status))
      .catch(() => setStatus('UNREACHABLE'))
  }, [])

  return (
    <p className="text-xs text-muted">
      Backend:{' '}
      <span className={status === 'UP' ? 'font-semibold text-success' : 'font-semibold text-error'}>
        {status}
      </span>
    </p>
  )
}
