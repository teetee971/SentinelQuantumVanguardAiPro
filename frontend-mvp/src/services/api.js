/**
 * API Service Layer
 * Handles all API communication with the backend
 */

import { API_CONFIG } from '../config/api';

class APIService {
  constructor() {
    this.baseURL = API_CONFIG.baseURL;
  }

  /**
   * Generic fetch wrapper with error handling
   */
  async request(endpoint, options = {}) {
    const url = `${this.baseURL}${endpoint}`;
    const config = {
      headers: {
        'Content-Type': 'application/json',
        ...options.headers,
      },
      ...options,
    };

    // Add authentication token if available
    const token = localStorage.getItem('auth_token');
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`;
    }

    try {
      const response = await fetch(url, config);
      
      if (!response.ok) {
        const error = await response.json().catch(() => ({}));
        throw new Error(error.message || `HTTP ${response.status}: ${response.statusText}`);
      }

      return await response.json();
    } catch (error) {
      console.error('API Request Error:', error);
      throw error;
    }
  }

  // Authentication
  async login(email, password) {
    return this.request('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    });
  }

  async register(userData) {
    return this.request('/auth/register', {
      method: 'POST',
      body: JSON.stringify(userData),
    });
  }

  async logout() {
    localStorage.removeItem('auth_token');
    localStorage.removeItem('user_data');
  }

  // Products
  async searchProducts(query, region = null) {
    const params = new URLSearchParams({ q: query });
    if (region) params.append('region', region);
    return this.request(`/products/search?${params}`);
  }

  async getProduct(productId) {
    return this.request(`/products/${productId}`);
  }

  async getProductPrices(productId, region = null) {
    const params = region ? `?region=${region}` : '';
    return this.request(`/products/${productId}/prices${params}`);
  }

  // Price comparison
  async comparePrices(productIds, region = null) {
    const params = new URLSearchParams();
    productIds.forEach(id => params.append('products[]', id));
    if (region) params.append('region', region);
    return this.request(`/prices/compare?${params}`);
  }

  // User profile
  async getUserProfile() {
    return this.request('/user/profile');
  }

  async updateUserProfile(data) {
    return this.request('/user/profile', {
      method: 'PUT',
      body: JSON.stringify(data),
    });
  }

  async updateUserRegion(region) {
    return this.request('/user/region', {
      method: 'PUT',
      body: JSON.stringify({ region }),
    });
  }

  // Retailers/Stores
  async getRetailers(region = null) {
    const params = region ? `?region=${region}` : '';
    return this.request(`/retailers${params}`);
  }
}

export const apiService = new APIService();
export default apiService;
