# Environment Variables Setup

This document explains how to configure environment variables for the rApp Tycoon Frontend.

## Overview

The application uses environment variables to configure the API base URL and WebSocket URL for different environments (development, production). All environment variables are prefixed with `VITE_` to be exposed to client-side code by Vite.

## Files

### `.env.local` (Development)
Used when running the development server (`npm run dev`). This file is **not** committed to Git.

```
VITE_API_BASE_URL=http://localhost:8080/api
VITE_WS_URL=ws://localhost:8080/ws/game
```

### `.env.production` (Production)
Used when building for production (`npm run build`). This file **can** be committed to Git as it contains no secrets.

```
VITE_API_BASE_URL=https://api.rappstycoon.com/api
VITE_WS_URL=wss://api.rappstycoon.com/ws/game
```

### `.env.example` (Template)
A template file showing the required environment variables. Use this as a reference when setting up new environments.

## Setup Instructions

### For Development

1. Copy `.env.example` to `.env.local`:
   ```bash
   cp .env.example .env.local
   ```

2. Update `.env.local` with your local development server URLs:
   ```
   VITE_API_BASE_URL=http://localhost:8080/api
   VITE_WS_URL=ws://localhost:8080/ws/game
   ```

3. Start the development server:
   ```bash
   npm run dev
   ```

### For Production

1. Update `.env.production` with your production server URLs:
   ```
   VITE_API_BASE_URL=https://api.rappstycoon.com/api
   VITE_WS_URL=wss://api.rappstycoon.com/ws/game
   ```

2. Build for production:
   ```bash
   npm run build
   ```

## Accessing Environment Variables in Code

Use the centralized configuration module to access environment variables:

```typescript
import { config } from '@/utils/config';

// Access API base URL
const apiUrl = config.API_BASE_URL;

// Access WebSocket URL
const wsUrl = config.WS_URL;

// Check environment
if (config.isDevelopment) {
  console.log('Running in development mode');
}

if (config.isProduction) {
  console.log('Running in production mode');
}
```

Alternatively, access environment variables directly using Vite's `import.meta.env`:

```typescript
const apiUrl = import.meta.env.VITE_API_BASE_URL;
const wsUrl = import.meta.env.VITE_WS_URL;
```

## Environment Variable Reference

| Variable | Description | Example |
|----------|-------------|---------|
| `VITE_API_BASE_URL` | Base URL for REST API calls | `http://localhost:8080/api` |
| `VITE_WS_URL` | WebSocket URL for real-time updates | `ws://localhost:8080/ws/game` |

## Security Notes

- **Never commit `.env.local`** to Git. It's already in `.gitignore`.
- **Never commit `.env.production.local`** to Git. It's already in `.gitignore`.
- `.env.production` can be committed as it contains no secrets (only public URLs).
- For sensitive production URLs, use environment variables in your deployment pipeline instead.

## Vite Configuration

Vite automatically exposes environment variables prefixed with `VITE_` to client-side code. No additional configuration is needed in `vite.config.js`.

For more information, see [Vite Environment Variables Documentation](https://vitejs.dev/guide/env-and-modes.html).
