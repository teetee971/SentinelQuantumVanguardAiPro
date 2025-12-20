/**
 * Profile Page (Profil)
 * User profile and settings
 */

import { useState, useEffect } from 'react';
import { useAuth } from '../hooks/useAuth';
import { useNavigate } from 'react-router-dom';
import apiService from '../services/api';
import './Profile.css';

export default function Profile() {
  const { user, logout, updateUser } = useAuth();
  const navigate = useNavigate();
  
  const [formData, setFormData] = useState({
    name: user?.name || '',
    email: user?.email || '',
    region: user?.region || 'metropole',
  });
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState({ type: '', text: '' });

  useEffect(() => {
    if (!user) {
      navigate('/connexion');
    }
  }, [user, navigate]);

  const handleChange = (e) => {
    setFormData({
      ...formData,
      [e.target.name]: e.target.value,
    });
    setMessage({ type: '', text: '' });
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setMessage({ type: '', text: '' });

    try {
      const updatedUser = await apiService.updateUserProfile(formData);
      updateUser(updatedUser);
      setMessage({ type: 'success', text: 'Profil mis à jour avec succès' });
    } catch (err) {
      setMessage({
        type: 'error',
        text: err.message || 'Erreur lors de la mise à jour',
      });
    } finally {
      setLoading(false);
    }
  };

  const handleLogout = async () => {
    await logout();
    navigate('/');
  };

  if (!user) {
    return null;
  }

  const regionNames = {
    metropole: 'Métropole',
    guadeloupe: 'Guadeloupe',
    martinique: 'Martinique',
    guyane: 'Guyane',
    reunion: 'Réunion',
    mayotte: 'Mayotte',
  };

  return (
    <div className="profile-page">
      <div className="profile-container">
        <div className="profile-header">
          <h1>Mon Profil</h1>
          <p>Gérez vos informations personnelles</p>
        </div>

        {message.text && (
          <div className={`message ${message.type}`}>{message.text}</div>
        )}

        <form onSubmit={handleSubmit} className="profile-form">
          <div className="form-section">
            <h2>Informations personnelles</h2>

            <div className="form-group">
              <label htmlFor="name">Nom</label>
              <input
                type="text"
                id="name"
                name="name"
                value={formData.name}
                onChange={handleChange}
                required
              />
            </div>

            <div className="form-group">
              <label htmlFor="email">Email</label>
              <input
                type="email"
                id="email"
                name="email"
                value={formData.email}
                onChange={handleChange}
                required
              />
            </div>
          </div>

          <div className="form-section">
            <h2>Préférences</h2>

            <div className="form-group">
              <label htmlFor="region">Région principale</label>
              <select
                id="region"
                name="region"
                value={formData.region}
                onChange={handleChange}
                required
              >
                {Object.entries(regionNames).map(([key, name]) => (
                  <option key={key} value={key}>
                    {name}
                  </option>
                ))}
              </select>
              <small>
                Les prix seront affichés en priorité pour cette région
              </small>
            </div>
          </div>

          <div className="form-actions">
            <button type="submit" className="save-button" disabled={loading}>
              {loading ? 'Enregistrement...' : 'Enregistrer les modifications'}
            </button>
          </div>
        </form>

        <div className="danger-zone">
          <h2>Zone dangereuse</h2>
          <button onClick={handleLogout} className="logout-button">
            Se déconnecter
          </button>
        </div>
      </div>
    </div>
  );
}
