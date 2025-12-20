/**
 * Login/Register Page (Connexion)
 * User authentication page
 */

import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import './Login.css';

export default function Login() {
  const [isLogin, setIsLogin] = useState(true);
  const [formData, setFormData] = useState({
    email: '',
    password: '',
    name: '',
    region: 'metropole',
  });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  
  const { login, register } = useAuth();
  const navigate = useNavigate();

  const handleChange = (e) => {
    setFormData({
      ...formData,
      [e.target.name]: e.target.value,
    });
    setError('');
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      if (isLogin) {
        await login(formData.email, formData.password);
      } else {
        await register(formData);
      }
      navigate('/');
    } catch (err) {
      setError(err.message || 'Une erreur est survenue');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-page">
      <div className="login-container">
        <div className="login-header">
          <h1>{isLogin ? 'Connexion' : 'Créer un compte'}</h1>
          <p>
            {isLogin
              ? 'Connectez-vous pour comparer les prix'
              : 'Créez un compte pour commencer'}
          </p>
        </div>

        {error && <div className="error-message">{error}</div>}

        <form onSubmit={handleSubmit} className="login-form">
          {!isLogin && (
            <div className="form-group">
              <label htmlFor="name">Nom</label>
              <input
                type="text"
                id="name"
                name="name"
                value={formData.name}
                onChange={handleChange}
                required={!isLogin}
                placeholder="Votre nom"
              />
            </div>
          )}

          <div className="form-group">
            <label htmlFor="email">Email</label>
            <input
              type="email"
              id="email"
              name="email"
              value={formData.email}
              onChange={handleChange}
              required
              placeholder="votre@email.com"
            />
          </div>

          <div className="form-group">
            <label htmlFor="password">Mot de passe</label>
            <input
              type="password"
              id="password"
              name="password"
              value={formData.password}
              onChange={handleChange}
              required
              placeholder="••••••••"
              minLength="6"
            />
          </div>

          {!isLogin && (
            <div className="form-group">
              <label htmlFor="region">Région</label>
              <select
                id="region"
                name="region"
                value={formData.region}
                onChange={handleChange}
                required
              >
                <option value="metropole">Métropole</option>
                <option value="guadeloupe">Guadeloupe</option>
                <option value="martinique">Martinique</option>
                <option value="guyane">Guyane</option>
                <option value="reunion">Réunion</option>
                <option value="mayotte">Mayotte</option>
              </select>
            </div>
          )}

          <button type="submit" className="submit-button" disabled={loading}>
            {loading
              ? 'Chargement...'
              : isLogin
              ? 'Se connecter'
              : "S'inscrire"}
          </button>
        </form>

        <div className="toggle-form">
          <p>
            {isLogin
              ? "Vous n'avez pas de compte ?"
              : 'Vous avez déjà un compte ?'}
          </p>
          <button
            type="button"
            className="toggle-button"
            onClick={() => {
              setIsLogin(!isLogin);
              setError('');
            }}
          >
            {isLogin ? 'Créer un compte' : 'Se connecter'}
          </button>
        </div>

        <div className="back-link">
          <Link to="/">← Retour à l'accueil</Link>
        </div>
      </div>
    </div>
  );
}
