# CSB Training

Everything built during the CSB training: daily exercises and larger projects, in Java (Spring Boot), React, SQL and plain web technologies.

The folders are being added one at a time, each after a review (security, tests, a README that explains how to run it). A folder that is not in the list below has not been added yet.

| Folder | What it is | Tech |
| --- | --- | --- |
| [`Notes`](Notes) | **Study notes**: 26 detailed guides on Java (fundamentals to concurrency, the JVM, design patterns), SQL, JPA, REST, Spring Boot 3 and 4, Spring Security with JWT and microservices, with runnable examples | Markdown; Spring Boot 4 and SQL example projects; a link and code-block checker (Node) |
| [`Bootstrap`](Bootstrap) | **PeopleHub**: a responsive employee management dashboard (search, filters, sorting, paging, add / edit / delete, dark mode) | HTML, CSS, Bootstrap 5.3, JavaScript; Node tests, Playwright |

## Working with the repo

- Each project has its own README with the exact steps to run it and to run its tests.
- Secrets never go in the repo. Where a project needs a key or password, it ships an `.env.example` or `*.example` file; copy it, fill in your own values, and keep the copy out of git (the root `.gitignore` already covers the usual names).
