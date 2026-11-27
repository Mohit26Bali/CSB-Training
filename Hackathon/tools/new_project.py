"""
Creates a new hackathon project from the shared starter kit (the generic parts of expense-management):
JWT login, role-based security, error handling, the React shell, dark mode and the animated login page.
You then write the domain (entities, services, controllers, pages) on top of it.

Usage (from the Hackathon folder):
    python tools/new_project.py leave-portal --title "Leave Portal" --pkg leave --cls Leave --nn 12 ^
        --db leave_portal --roles EMPLOYEE,MANAGER ^
        --rules "/api/manager/**=MANAGER;/api/leave/**=EMPLOYEE,MANAGER"

--nn    two digits used for the ports: backend 81NN, front end 51NN
--rules ';'-separated "ant-pattern=ROLE[,ROLE]" or "ant-pattern=authenticated", checked in order
Then run `npm install` in <slug>/frontend.
"""

import argparse
import os
import re
import shutil
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
TEMPLATE = os.path.join(ROOT, "expense-management")
OLD_PKG = "com.hackathon.expense"

BACKEND_KIT = [
    "security/JwtService", "security/JwtAuthenticationFilter", "security/JsonErrorWriter",
    "security/RestAuthenticationEntryPoint", "security/RestAccessDeniedHandler",
    "config/SecurityConfig", "controller/AuthController",
    "service/AuthService", "service/LoginAttemptService", "service/CustomUserDetailsService",
    "dto/LoginRequest", "dto/RegisterRequest", "dto/AuthResponse", "dto/UserResponse", "dto/ErrorResponse",
    "dto/MessageResponse", "entity/User", "repo/UserRepository",
    "exception/DuplicateUsernameException", "exception/TooManyAttemptsException",
    "exception/ResourceNotFoundException", "exception/InvalidStatusException",
    "exception/FieldValidationException", "exception/ForbiddenActionException",
    "exception/ConflictException", "exception/BusinessRuleException", "exception/GlobalExceptionHandler",
]


def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("slug")
    ap.add_argument("--title", required=True)
    ap.add_argument("--pkg", required=True, help="java package suffix, e.g. leave")
    ap.add_argument("--cls", required=True, help="class prefix, e.g. Leave -> LeaveApplication")
    ap.add_argument("--nn", required=True)
    ap.add_argument("--db", required=True)
    ap.add_argument("--roles", required=True)
    ap.add_argument("--rules", required=True)
    a = ap.parse_args()

    dest = os.path.join(ROOT, a.slug)
    if os.path.exists(dest):
        sys.exit(f"{a.slug} already exists; refusing to overwrite it.")
    roles = a.roles.split(",")
    new_pkg = f"com.hackathon.{a.pkg}"
    be_port, fe_port = f"81{a.nn}", f"51{a.nn}"
    src_java = os.path.join(TEMPLATE, "backend/src/main/java", *OLD_PKG.split("."))
    dst_java = os.path.join(dest, "backend/src/main/java", *new_pkg.split("."))

    def java(text):
        return text.replace(OLD_PKG, new_pkg)

    # ---- backend ----
    os.makedirs(os.path.join(dest, "backend"), exist_ok=True)
    for name in ["mvnw", "mvnw.cmd", ".gitignore"]:
        shutil.copy(os.path.join(TEMPLATE, "backend", name), os.path.join(dest, "backend", name))
    shutil.copytree(os.path.join(TEMPLATE, "backend/.mvn"), os.path.join(dest, "backend/.mvn"))

    pom = read(os.path.join(TEMPLATE, "backend/pom.xml"))
    pom = pom.replace("<artifactId>expense-management</artifactId>", f"<artifactId>{a.slug}</artifactId>")
    pom = pom.replace("<name>expense-management</name>", f"<name>{a.slug}</name>")
    pom = re.sub(r"<description>.*?</description>", f"<description>{a.title}</description>", pom)
    write(os.path.join(dest, "backend/pom.xml"), pom)

    props = read(os.path.join(TEMPLATE, "backend/src/main/resources/application.properties"))
    props = (props.replace("spring.application.name=expense-management", f"spring.application.name={a.slug}")
             .replace("server.port=8111", f"server.port={be_port}")
             .replace("expense_management", a.db)
             .replace("http://localhost:5111", f"http://localhost:{fe_port}")
             .replace("Seed demo users and expenses", "Seed demo users and sample data"))
    write(os.path.join(dest, "backend/src/main/resources/application.properties"), props)

    for rel in BACKEND_KIT:
        text = java(read(os.path.join(src_java, rel + ".java")))
        text = text.replace("Role.EMPLOYEE", f"Role.{roles[0]}")  # self-registration always creates the first (lowest) role
        if rel == "config/SecurityConfig":
            lines = []
            for rule in a.rules.split(";"):
                pattern, who = rule.split("=")
                if who == "authenticated":
                    lines.append(f'                    .requestMatchers("{pattern}").authenticated()')
                else:
                    rs = ", ".join(f'"{r}"' for r in who.split(","))
                    method = "hasRole" if "," not in who else "hasAnyRole"
                    lines.append(f'                    .requestMatchers("{pattern}").{method}({rs})')
            text = re.sub(r"( *// RULES-BEGIN.*?\n).*?( *// RULES-END\n)",
                          lambda m: m.group(1) + "\n".join(lines) + "\n" + m.group(2), text, flags=re.S)
        write(os.path.join(dst_java, rel + ".java"), text)

    write(os.path.join(dst_java, "entity/Role.java"),
          f"package {new_pkg}.entity;\n\npublic enum Role {{\n    " + ",\n    ".join(roles) + "\n}\n")
    write(os.path.join(dst_java, f"{a.cls}Application.java"), f"""package {new_pkg};

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class {a.cls}Application {{

    public static void main(String[] args) {{
        SpringApplication.run({a.cls}Application.class, args);
    }}
}}
""")
    write(os.path.join(dest, "backend/src/test/resources/application-test.properties"),
          read(os.path.join(TEMPLATE, "backend/src/test/resources/application-test.properties")))
    write(os.path.join(dest, f"backend/src/test/java/{'/'.join(new_pkg.split('.'))}/{a.cls}ApplicationTests.java"), f"""package {new_pkg};

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class {a.cls}ApplicationTests {{

    @Test
    void contextLoads() {{
    }}
}}
""")

    # ---- frontend ----
    fe_src, fe_dst = os.path.join(TEMPLATE, "frontend"), os.path.join(dest, "frontend")
    skip_dirs = {"node_modules", "dist"}
    skip_paths = {os.path.join("src", "pages", "employee"), os.path.join("src", "pages", "manager"),
                  os.path.join("public", "hero"), os.path.join("public", "banner")}
    for folder, dirs, files in os.walk(fe_src):
        rel_dir = os.path.relpath(folder, fe_src)
        dirs[:] = [d for d in dirs if d not in skip_dirs and os.path.normpath(os.path.join(rel_dir, d)) not in skip_paths]
        for f in files:
            rel = os.path.normpath(os.path.join(rel_dir, f))
            if rel in {os.path.join("src", "App.tsx"), os.path.join("src", "api", "endpoints.ts"),
                       os.path.join("src", "api", "types.ts"), os.path.join("src", "config.ts")}:
                continue  # domain files: written below as starting points
            target = os.path.join(fe_dst, rel)
            os.makedirs(os.path.dirname(target), exist_ok=True)
            if f.endswith((".ts", ".tsx", ".json", ".html", ".css", ".svg", ".md")):
                text = read(os.path.join(folder, f))
                text = (text.replace("expense.session", f"{a.slug}.session").replace("expense:session-expired", f"{a.slug}:session-expired")
                        .replace("expense.theme", f"{a.slug}.theme").replace("Expense Manager", a.title)
                        .replace("expense-management-frontend", f"{a.slug}-frontend")
                        .replace("5111", fe_port).replace("8111", be_port))
                write(target, text)
            else:
                shutil.copy(os.path.join(folder, f), target)

    types = f"""export type Role = {' | '.join(repr(r) for r in roles)}

export interface AuthResponse {{
  token: string
  expiresIn: number
  username: string
  fullname: string
  role: Role
}}

export interface RegisterInput {{
  username: string
  password: string
  fullname: string
}}
""".replace('"', "'")
    write(os.path.join(fe_dst, "src/api/types.ts"), types)
    write(os.path.join(fe_dst, "src/api/endpoints.ts"), """import { http } from './client'
import type { AuthResponse, RegisterInput } from './types'

export const authApi = {
  login: (username: string, password: string) =>
    http.post<AuthResponse>('/auth/login', { username, password }).then((r) => r.data),
  register: (input: RegisterInput) => http.post<AuthResponse>('/auth/register', input).then((r) => r.data),
}
""")
    print(f"Created {a.slug}: backend :{be_port}, frontend :{fe_port}, database {a.db}")
    print("Next: write src/config.ts, src/App.tsx, the domain code, and run `npm install` in frontend.")
    print("Then give it its own look: add an entry to LOOKS in tools/apply_look.py (palette, fonts, login layout, nav style, banners)")
    print("and run `python tools/apply_look.py <slug>`. Until then it looks like expense-management.")


if __name__ == "__main__":
    main()
