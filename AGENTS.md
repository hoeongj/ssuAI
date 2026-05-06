# AGENTS.md

## Project Name
ssuAI

## Project Summary
ssuAI is an AI-powered school assistant for Soongsil University students.

The project aims to integrate school-related information such as u-SAINT, LMS, cafeteria menus, library book availability, and library seat status into a backend API, MCP server, chatbot, web dashboard, and later a mobile app.

## Portfolio Goal
This is primarily a portfolio project.

The project should demonstrate:
- practical Spring Boot backend development
- clean layered architecture
- external school service integration
- secure handling of sensitive student data
- MCP-based tool integration
- AI chatbot integration
- testing and maintainable code
- Docker-based local development
- Git-based development workflow

Optimize for:
- code quality
- architecture clarity
- interview explainability
- small and reviewable changes

Do not optimize only for fast implementation.

## Developer Context
The main developer is a 3rd-year Computer Science student at Soongsil University.

The developer has experience with basic Spring Boot CRUD projects and is learning:
- production-style backend architecture
- security
- testing
- external service integration
- deployment
- AI/MCP integration

Therefore:
- Prefer clear and maintainable code.
- Avoid unnecessary complexity.
- Work in small, reviewable steps.
- Explain non-obvious design choices briefly.

## AI Collaboration Workflow

This project uses two AI coding assistants with separate roles.

Claude Code is used for:
- architecture design
- feature planning
- security review
- code review
- risk analysis
- breaking large tasks into smaller tasks

Codex CLI is used for:
- implementing small scoped tasks
- editing files
- writing tests
- fixing compile errors
- applying review feedback
- making minimal code changes

## Hand-off Convention (Claude → Codex)

The developer uses Claude Code as architect/reviewer and Codex CLI as implementer. Per-task instructions from Claude live in **one rolling file**:

- Path: `.codex/current-task.md` (project root, gitignored — transient).
- Claude writes the next task into this file. The previous content is overwritten.
- The developer's message to Codex is always the same one-liner:

  > Read `.codex/current-task.md` and execute it. Reply using the Required Output Format below.

- For full feature specs (e.g., a new domain slice), Claude writes the spec to `docs/tasks/<NN>-<name>.md` (committed), and `.codex/current-task.md` becomes a short pointer:

  ```
  Implement docs/tasks/02-meal-mock-api.md.
  Reply using the Required Output Format in AGENTS.md.
  ```

- For small patches (one-off fixes, follow-up edits), Claude writes the full instructions inline in `.codex/current-task.md` — no separate `docs/tasks/` entry needed.

Codex MUST read `.codex/current-task.md` at the start of every session triggered by the one-liner above, before touching any code.

## Working Rules for Codex

When working on this repository:

1. Read this AGENTS.md before starting. If the developer's message references `.codex/current-task.md`, read that file too.
2. Work on one small feature at a time.
3. Prefer small diffs.
4. Do not implement the whole project at once.
5. Do not modify unrelated files.
6. Do not add new production dependencies unless there is a clear reason.
7. Do not change documentation files unless explicitly asked.
8. If mock data is used, clearly label it as mock data.
9. Do not store secrets, passwords, cookies, tokens, or session values in the repository.
10. After code changes, follow the **Required Output Format** below. This is mandatory for every task that touches files.

## Required Output Format

Every response that creates, modifies, or deletes files MUST end with these two sections, in this exact order:

### 1. Work Summary
A short description (1–3 sentences) of what was done and why. Mention any non-obvious decisions. Do NOT paste full diffs here.

### 2. File List
List every file touched, grouped by action. Use absolute or repo-relative paths. Omit a group if it is empty.

```
Created:
- backend/src/main/java/com/ssuai/...

Modified:
- backend/build.gradle
- backend/src/main/resources/application.yml

Deleted:
- backend/src/main/java/com/ssuai/global/web/HelloController.java
```

Rules for the File List:
- List EVERY file touched, including config files, tests, and resources.
- Do not summarize as "and 3 other test files" — list them all.
- If no files were touched (e.g., a question-only response), write `No files changed.` instead of the list.

This format is mandatory even for one-file edits. The reviewer (Claude / the developer) relies on this list to know which files to read without scanning the full diff.

## Recommended Tech Stack

### Backend
- Java 21
- Spring Boot
- Spring Web
- Spring Validation
- Spring Security
- Spring Data JPA
- Spring Boot Actuator
- Gradle

Use a stable Spring Boot version that has strong ecosystem support.
Do not upgrade to a major new Spring Boot version only because it is newer.

### Database and Cache
- PostgreSQL for main relational data
- Redis for caching, temporary session state, rate limiting, and notification-related state
- H2 only for early local tests or prototypes

### AI and MCP
- Spring AI
- Spring AI MCP support
- MCP Java SDK later if direct MCP protocol control is needed

MCP tools should start as read-only tools.

### Crawling and External Integration
- Jsoup for simple HTML parsing
- Playwright for login-based, JavaScript-heavy, or browser-interaction flows

All external school service logic must be isolated in Connector classes.

### Frontend
- Next.js
- React
- TypeScript
- Tailwind CSS
- shadcn/ui
- TanStack Query

### Mobile App
- Expo React Native
- TypeScript
- Expo Notifications

Mobile development should start after the backend and web dashboard become stable.

### Infrastructure
- Docker
- Docker Compose
- GitHub Actions
- Nginx later if needed
- AWS EC2, Lightsail, or another simple VPS later

### Testing
- JUnit 5
- Spring Boot Test
- Testcontainers later for PostgreSQL and Redis integration tests
- Fixture-based tests for external school service parsing
- Do not call real u-SAINT or LMS in automated tests

## Architecture Principles

Use a layered architecture:

Controller -> Service -> Repository / Connector

### Controller
Responsibilities:
- handle HTTP requests and responses
- validate request DTOs
- call service methods
- return response DTOs

Avoid:
- business logic
- crawling logic
- direct database access

### Service
Responsibilities:
- application logic
- transaction boundaries
- coordination between repositories and connectors
- caching decisions

Avoid:
- raw browser automation
- direct HTML parsing when it belongs in a connector

### Repository
Responsibilities:
- database access only
- Spring Data JPA usage

### Connector
Responsibilities:
- external school service access
- crawling
- parsing
- mapping external data into internal DTOs

Examples:
- UsaintConnector
- LmsConnector
- CafeteriaConnector
- LibraryConnector

### DTO
Use DTOs for API input and output.
Do not expose JPA entities directly through API responses.

## Backend Package Structure Guideline

Prefer this package structure:

com.ssuai
- global
    - config
    - exception
    - security
    - response
- domain
    - meal
        - controller
        - service
        - dto
        - connector
    - library
        - controller
        - service
        - dto
        - connector
    - user
        - controller
        - service
        - dto
        - entity
        - repository
    - chat
        - controller
        - service
        - dto
    - mcp
        - tool
        - config

Do not create too many abstractions too early.

## MVP Development Order

Build the project in this order:

1. Project documentation
2. Spring Boot backend setup
3. Public cafeteria menu API with mock data
4. Public cafeteria menu API with a real connector
5. Library book search API with mock data
6. Library book search API with a real connector
7. Library seat status API
8. Basic web dashboard
9. Read-only MCP tools for public data
10. Basic chatbot using MCP tools
11. User account system
12. LMS integration
13. u-SAINT integration
14. Notification system
15. Carefully controlled action tools such as library seat reservation

Do not start with:
- u-SAINT login
- LMS login
- graduation requirement automation
- library seat auto-reservation
- mobile app
- complex agent automation

## Security Rules

This project may eventually handle sensitive student data.

Strict rules:
- Never store school account passwords in plain text.
- Never commit secrets.
- Never log passwords, cookies, tokens, session IDs, or personal academic data.
- Use environment variables for secrets.
- Use encryption if credentials or long-lived tokens are ever stored.
- Personal academic data must be treated as sensitive private data.
- Any action feature must require explicit final user confirmation.

Sensitive data examples:
- grades
- course history
- graduation requirements
- LMS assignments
- LMS grades
- u-SAINT student information

## MCP Tool Rules

MCP tools should be:
- small
- focused
- predictable
- structured
- easy to test
- safe by default

Good read-only tools:
- get_today_meal
- search_library_book
- get_library_seat_status
- get_current_courses
- get_lms_assignments
- get_graduation_progress

Risky action tools:
- reserve_library_seat
- change_student_information
- submit_assignment
- register_course

Risky tools must have:
- explicit user confirmation
- dry-run mode
- audit logs
- failure handling
- clear final result reporting

## Coding Style

- Use meaningful class and method names.
- Prefer constructor injection.
- Use Lombok carefully.
- Avoid large methods.
- Avoid business logic in controllers.
- Avoid static utility classes unless clearly justified.
- Prefer explicit DTOs over Map-based responses.
- Keep exception handling consistent.
- Add comments only when they explain why, not what.

## Git Workflow

Before asking an AI agent to modify files:
- Run git status.
- Commit stable work if needed.

After changes:
- Review git diff.
- Run tests.
- Commit small meaningful changes.

Suggested commit message types:
- chore: setup, configuration, tooling
- docs: documentation
- feat: feature
- fix: bug fix
- refactor: internal structure improvement
- test: tests
- security: security-related improvement

## Verification Commands

From the backend directory on Windows CMD:
- gradlew.bat test
- gradlew.bat bootRun

From the backend directory on macOS/Linux/WSL:
- ./gradlew test
- ./gradlew bootRun

Docker commands will be added later:
- docker compose up -d
- docker compose down

## Current Project Phase

Current phase:
- repository setup
- AI-assisted development workflow setup
- product and architecture documentation

Do not jump into implementation until:
- docs/product.md is drafted
- docs/architecture.md is drafted

## First Recommended Feature

The first implementation feature should be:

Public cafeteria menu API

Reasons:
- no login required
- low privacy risk
- good connector pattern practice
- useful for chatbot tool calling later
- easy to demonstrate in a portfolio

Start with mock data first.
Then replace it with a real connector.