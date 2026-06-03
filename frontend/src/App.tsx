import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { AppLayout } from './components/layout/AppLayout';
import { LobbyPage } from './pages/LobbyPage';
import { GamePage } from './pages/GamePage';
import { ResultsPage } from './pages/ResultsPage';
import { TutorialPage } from './pages/TutorialPage';
import { GameProvider } from './context/GameContext';
import { ErrorBoundary } from './components/ui';

function App() {
  return (
    <ErrorBoundary>
      <GameProvider>
        <BrowserRouter>
          <Routes>
            <Route element={<AppLayout />}>
              <Route path="/" element={<LobbyPage />} />
              <Route path="/tutorial" element={<TutorialPage />} />
              <Route path="/game" element={<GamePage />} />
              <Route path="/results" element={<ResultsPage />} />
            </Route>
          </Routes>
        </BrowserRouter>
      </GameProvider>
    </ErrorBoundary>
  );
}

export default App;
