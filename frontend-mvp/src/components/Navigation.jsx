/**
 * Navigation Component
 * Main navigation bar
 */

import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import './Navigation.css';

export default function Navigation() {
  const { user } = useAuth();
  const navigate = useNavigate();

  return (
    <nav className="navigation">
      <div className="nav-container">
        <Link to="/" className="nav-logo">
          <span className="logo-icon">💰</span>
          <span className="logo-text">Comparateur Prix</span>
        </Link>

        <div className="nav-links">
          <Link to="/" className="nav-link">
            Accueil
          </Link>
          <Link to="/produits" className="nav-link">
            Produits
          </Link>
          {user ? (
            <>
              <Link to="/profil" className="nav-link">
                Mon Profil
              </Link>
              <div className="user-badge">
                {user.name || user.email}
              </div>
            </>
          ) : (
            <Link to="/connexion" className="nav-link nav-login">
              Connexion
            </Link>
          )}
        </div>
      </div>
    </nav>
  );
}
