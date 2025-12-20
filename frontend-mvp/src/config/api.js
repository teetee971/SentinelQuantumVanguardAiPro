/**
 * API Configuration
 * Manages API base URL from environment variables
 */

export const API_CONFIG = {
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:3000/api',
  timeout: 10000,
};

export default API_CONFIG;
