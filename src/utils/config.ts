/**
 * Application Configuration
 * 
 * This module provides centralized access to environment variables.
 * All environment variables are prefixed with VITE_ to be exposed to client-side code.
 * 
 * Usage:
 *   import { config } from '@/utils/config';
 *   const apiUrl = config.API_BASE_URL;
 *   const wsUrl = config.WS_URL;
 */

interface AppConfig {
  API_BASE_URL: string;
  WS_URL: string;
  isDevelopment: boolean;
  isProduction: boolean;
}

/**
 * Validates that all required environment variables are set
 */
function validateConfig(): void {
  const required = ['VITE_API_BASE_URL', 'VITE_WS_URL'];
  const missing = required.filter(key => !import.meta.env[key]);

  if (missing.length > 0) {
    console.error(
      `Missing required environment variables: ${missing.join(', ')}. ` +
      `Please check your .env.local or .env.production file.`
    );
  }
}

/**
 * Application configuration object
 * Provides type-safe access to environment variables
 */
export const config: AppConfig = {
  API_BASE_URL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api',
  WS_URL: import.meta.env.VITE_WS_URL || 'ws://localhost:8080/ws/game',
  isDevelopment: import.meta.env.DEV,
  isProduction: import.meta.env.PROD,
};

// Validate configuration on module load
validateConfig();

export default config;
