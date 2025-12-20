/**
 * Main App Component
 * Handles routing and global layout
 */

import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import { AuthProvider } from './hooks/useAuth';
import Navigation from './components/Navigation';
import Home from './pages/Home';
import Login from './pages/Login';
import Products from './pages/Products';
import Results from './pages/Results';
import Profile from './pages/Profile';
import './App.css';

function App() {
  return (
    <Router>
      <AuthProvider>
        <div className="app">
          <Navigation />
          <main className="main-content">
            <Routes>
              <Route path="/" element={<Home />} />
              <Route path="/connexion" element={<Login />} />
              <Route path="/produits" element={<Products />} />
              <Route path="/resultats/:productId" element={<Results />} />
              <Route path="/profil" element={<Profile />} />
            </Routes>
          </main>
          <footer className="app-footer">
            <p>© 2024 Comparateur de Prix - DOM & Métropole</p>
          </footer>
        </div>
      </AuthProvider>
    </Router>
  );
}

export default App;
