"""Small helpers for generating a Postman v2.1 collection with test scripts. Used by tools/collections/*.py."""

import json


def req(name, method, path, token=None, body=None, tests=None, pre=None, headers=None):
    header = [{"key": "Content-Type", "value": "application/json"}] if body is not None else []
    header += [{"key": k, "value": v} for k, v in (headers or {}).items()]
    r = {
        "method": method,
        "header": header,
        "url": {"raw": f"{{{{baseUrl}}}}{path}", "host": ["{{baseUrl}}"],
                "path": [p for p in path.split("?")[0].split("/") if p]},
    }
    if "?" in path:
        r["url"]["query"] = [{"key": kv.split("=")[0], "value": kv.split("=", 1)[1]} for kv in path.split("?")[1].split("&")]
    r["auth"] = ({"type": "bearer", "bearer": [{"key": "token", "value": "{{%s}}" % token, "type": "string"}]}
                 if token else {"type": "noauth"})
    if body is not None:
        r["body"] = {"mode": "raw", "raw": json.dumps(body, indent=2), "options": {"raw": {"language": "json"}}}
    item = {"name": name, "request": r, "event": []}
    if tests:
        item["event"].append({"listen": "test", "script": {"type": "text/javascript", "exec": tests.strip("\n").split("\n")}})
    if pre:
        item["event"].append({"listen": "prerequest", "script": {"type": "text/javascript", "exec": pre.strip("\n").split("\n")}})
    if not item["event"]:
        del item["event"]
    return item


def status(code):
    return f'pm.test("Status is {code}", () => pm.response.to.have.status({code}));'


def login_tests(var):
    return f"""
{status(200)}
const body = pm.response.json();
pm.test("Returns a bearer token", () => pm.expect(body.token).to.be.a("string").and.not.empty);
pm.collectionVariables.set("{var}", body.token);
"""


def folder(name, items):
    return {"name": name, "item": items}


def write_collection(out, name, description, items, variables):
    collection = {
        "info": {"name": name, "description": description,
                 "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"},
        "item": items,
        "variable": [{"key": k, "value": v} for k, v in variables.items()],
    }
    with open(out, "w", encoding="utf-8") as f:
        json.dump(collection, f, indent=2)
    print("wrote", out, sum(len(f["item"]) for f in items), "requests")
