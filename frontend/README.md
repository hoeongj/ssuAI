# ssuAI Frontend

Next.js dashboard for the local ssuAI MVP.

## Prerequisites

- Node.js 20
- pnpm 9
- Backend running on `http://localhost:8080`

## Setup

```powershell
Copy-Item .env.example .env.local
pnpm install
```

`NEXT_PUBLIC_SSUAI_API_BASE` defaults to `http://localhost:8080`.

## Commands

```powershell
pnpm dev
pnpm lint
pnpm typecheck
pnpm test
pnpm build
```

From the repository root:

```powershell
pnpm --dir frontend dev
pnpm --dir frontend lint
pnpm --dir frontend typecheck
pnpm --dir frontend test
pnpm --dir frontend build
```
