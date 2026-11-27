"""Builds a consistent README.md for a hackathon project from a dict. Used by tools/readmes/*.py."""


def table(headers, rows):
    out = ["| " + " | ".join(headers) + " |", "|" + "|".join("---" for _ in headers) + "|"]
    out += ["| " + " | ".join(str(c) for c in row) + " |" for row in rows]
    return "\n".join(out)


def build(d):
    be, fe = d["backend_port"], d["frontend_port"]
    parts = [f"# {d['title']}\n\n{d['summary']}\n"]

    parts.append(f"""## Quick start

You need Java 17 (`JAVA_HOME` must point at it), Node 20+, and MySQL 8 on `localhost:3306`.
The database `{d['db']}` is created automatically (user `root`, password `mohit123` by default).

```bash
# terminal 1: backend  (http://localhost:{be})
cd backend
./mvnw spring-boot:run          # PowerShell: .\\mvnw spring-boot:run

# terminal 2: front end  (http://localhost:{fe})
cd frontend
npm install                     # first time only
npm run dev
```

Open <http://localhost:{fe}>. The sun/moon button switches between light and dark mode.
On first start the backend seeds demo users and sample data.

### Demo logins

{table(['Username', 'Password', 'Role'], d['logins'])}
""")

    parts.append("## Features\n\n" + "\n".join(f"- {f}" for f in d["features"]) + "\n")
    parts.append("## Business rules\n\n" + table(["Rule", "Result"], d["rules"]) + "\n")
    if d.get("concurrency"):
        parts.append(f"**Concurrency:** {d['concurrency']}\n")
    if d.get("notes"):
        parts.append("\n".join(d["notes"]) + "\n")

    parts.append(f"""## API

All endpoints except login and register need `Authorization: Bearer <token>`.

{table(['Method', 'URL', 'Role', 'Purpose'], [('POST', '/api/auth/login', 'public', 'Log in, returns a token'),
                                              ('POST', '/api/auth/register', 'public', 'Create an account (always the lowest role)'),
                                              ('GET', '/api/auth/me', 'any', 'Current user')] + d['api'])}

Errors share one shape, and validation errors list each bad field so the UI can mark the right input:

```json
{{ "status": 400, "error": "Bad Request", "message": "...", "fieldErrors": {{ "field": "..." }}, "timestamp": "..." }}
```
""")

    pkg = d["package"]
    parts.append(f"""## Architecture

```
React ({fe}) / Postman  ->  Controller  ->  Service  ->  Repository  ->  MySQL
```

```
backend/src/main/java/com/hackathon/{pkg}
├── config        SecurityConfig (JWT filter chain, CORS), DataInitializer (demo data)
├── security      JwtService, JwtAuthenticationFilter, JSON 401/403 handlers
├── controller    {d['controllers']}
├── service       {d['services']}
├── repo          {d['repos']}
├── entity        {d['entities']}
├── dto           requests and responses, ErrorResponse
└── exception     GlobalExceptionHandler and the business exceptions
frontend/src      api (typed client) · auth (session, route guard) · components · pages · config.ts (branding, nav, demo accounts)
```

Controllers handle HTTP only, services hold the business rules, repositories only query.
Data processing in the services uses Java Streams. Tables are created by Hibernate: {d['tables']}
""")

    parts.append(f"""## Tests

```bash
cd backend
./mvnw test        # {d['tests']}
```

API walkthrough with the Postman CLI (backend must be running; the run is repeatable):

```bash
postman collection run docs/postman/{d['collection']}
```

It runs {d['postman']}. You can also import the file into the Postman app.

## Configuration

Environment variables override the defaults in `application.properties`:
`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` (**set your own, 32+ characters, outside local use**), `CORS_ORIGINS`.

## Images

The login-page photos in `frontend/public/hero`{d.get('images_extra', '')} come from [Pexels](https://www.pexels.com) (free licence)
and were downloaded with `../tools/fetch_images.py` using `images.json` (needs `PEXELS_API_KEY`).

## Known limitations

""" + "\n".join(f"- {x}" for x in d["limits"] + [
        "Login throttling is in memory, so it resets on restart.",
        "The JWT is kept in `sessionStorage`; a production system would use an httpOnly cookie.",
    ]) + "\n")
    return "\n".join(parts)
