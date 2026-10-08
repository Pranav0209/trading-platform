# Auth Service

Welcome to the **Auth Service** repository! This document serves as a comprehensive guide for new developers to understand the service's architecture, technologies, and workflows.

## 📖 Overview

The Auth Service is a critical microservice in the trading platform built using **NestJS**. It is strictly responsible for managing user identity, security, authentication, and authorization. 

This service manages credentials, issues JSON Web Tokens (JWT) for secure session management, enforces rate limits for logins, and handles OTP verification via email. It uses a **PostgreSQL** database managed by **TypeORM** to store user state, login attempts, and refresh tokens.

## 🛠️ Tech Stack

- **Framework:** [NestJS](https://nestjs.com/) (Node.js)
- **Language:** TypeScript
- **Database:** PostgreSQL
- **ORM:** TypeORM
- **Authentication:** JWT (Access & Refresh Tokens), `bcryptjs` for password hashing
- **Email/OTP:** `@emailjs/nodejs`
- **Testing:** Jest (Unit), Supertest (E2E)

## 📁 Architecture & Module Breakdown

The application is structured into feature modules under `src/`:

- **`auth/`**: The core of this service.
  - **Controllers/Services**: Handles `/login`, `/register`, `/refresh`, `/verify-otp`, and email updates.
  - **Login Throttling**: Implements max attempt checks and cooldown periods using `login-throttle.service` and `login-attempt.entity` to prevent brute-force attacks.
  - **Guards**: `jwt.guard.ts` secures endpoints requiring an active session.
- **`users/`**: Manages the `User` entity, database operations, and user profiles.
- **`preferences/`**: Manages specific user configurations and settings (`preferences.controller`).
- **`core/`**: 
  - **EmailJS**: Contains `emailjs.service.ts` for sending out OTPs and welcome emails.
  - **Filters**: Global exception filters (`exception.filter.ts`) to standardise error responses across the API.
- **`refresh-tokens/`**: Dedicated entity and management for long-lived JWT refresh tokens.

## ⚙️ Environment Variables

Copy the provided `.env.example` to `.env` in the root of `auth-service/`.

| Variable | Description | Example |
|---|---|---|
| `DATABASE_URL` | PostgreSQL connection string | `postgresql://user:pass@localhost:5432/db` |
| `JWT_SECRET` | Secret key for JWT signing | `super-secret-key` |
| `JWT_TOKEN_EXPIRY` | Short-lived Access Token expiry | `15m` |
| `JWT_REFRESH_TOKEN_EXPIRY_DAYS`| Long-lived Refresh Token expiry| `7` |
| `ACCESS_TOKEN_EXPIRES_IN_SECONDS`| Expiry in seconds for frontend | `900` |
| `PORT` | Port the API runs on | `3000` |
| `FRONTEND_URL` | Base URL for the frontend | `http://localhost:4200` |
| `LOGIN_MAX_ATTEMPTS` | Threshold before account lockout | `5` |
| `LOGIN_COOLDOWN_SECONDS` | Lockout duration in seconds | `900` |
| `FAUXNANCE_API_KEY` | API Key for external Fauxnance APIs | `<key>` |
| `FAUXNANCE_BASE_URL` | URL for external Fauxnance APIs | `https://api.fauxnance.com` |
| `NODE_ENV` | Running environment | `development` |

## 🚀 Getting Started

Follow these steps to get the service running locally:

### 1. Prerequisites
- Node.js (v18+ recommended)
- PostgreSQL database running locally or via Docker
- `npm` installed

### 2. Installation
```bash
cd services/auth-service
npm install
```

### 3. Configuration
Ensure your PostgreSQL server is running and the database specified in your `DATABASE_URL` exists. TypeORM will automatically sync the schema in development mode (if configured).

### 4. Running the Application
```bash
# Development mode (with live reload)
npm run start:dev

# Production build
npm run build
npm run start:prod
```

## 🧪 Testing

We use Jest for both unit and end-to-end testing.
- **Unit Tests**: `npm run test` (Tests individual services and controllers)
- **E2E Tests**: `npm run test:e2e` (Tests the full request lifecycle via Supertest)
- **Coverage**: `npm run test:cov` (Ensure high coverage before pushing code)

## 🤝 Development Workflow for New Devs

1. **Keep it Modular**: If you are adding a new feature (e.g., Two-Factor Auth via SMS), create a new module rather than stuffing logic into `auth.service.ts`.
2. **DTOs**: Always validate incoming requests using class-validator in DTOs (e.g., `login.dto.ts`).
3. **Database Changes**: Make changes to the `*.entity.ts` files. TypeORM will handle the rest during dev, but consider migrations for production.
4. **Security First**: Never log sensitive data like passwords, OTPs, or JWT secrets. 
