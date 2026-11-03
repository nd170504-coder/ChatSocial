import { useEffect } from 'react'
import { BrowserRouter } from 'react-router'
import { AppRouter } from './app/router'
import { authStore } from './features/auth/session'

function App() {
  useEffect(() => { void authStore.initialize() }, [])
  return <BrowserRouter><AppRouter /></BrowserRouter>
}

export default App
