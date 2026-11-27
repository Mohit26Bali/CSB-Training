"""python tools/collections/warranty.py warranty/docs/postman/Warranty.postman_collection.json"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from postman_lib import folder, login_tests, req, status, write_collection  # noqa: E402

PRE = """
const iso = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
const monthsAgo = (m) => { const t = new Date(); t.setMonth(t.getMonth() - m); return iso(t); };
const id = String(Math.floor(Math.random() * 900000 + 100000));
pm.collectionVariables.set("runId", id);
pm.collectionVariables.set("newUser", "postman." + id);
pm.collectionVariables.set("bought2m", monthsAgo(2));
pm.collectionVariables.set("bought30m", monthsAgo(30));
pm.collectionVariables.set("tooOld", monthsAgo(11 * 12));
const tomorrow = new Date(); tomorrow.setDate(tomorrow.getDate() + 1);
pm.collectionVariables.set("tomorrow", iso(tomorrow));
"""


def product(serial, bought="{{bought2m}}", months=12, name="Galaxy S23", category="MOBILE"):
    return {"name": name, "brand": "Samsung", "category": category, "serialNumber": serial, "purchaseDate": bought, "warrantyMonths": months}


def claim(product_id="{{productAId}}", text="The screen flickers and goes black", priority="HIGH"):
    return {"productId": product_id, "issueDescription": text, "priority": priority}


items = [
    folder("1. Auth", [
        req("Login as service agent", "POST", "/api/auth/login", body={"username": "agent", "password": "agent123"},
            tests=login_tests("agentToken"), pre=PRE),
        req("Login as second agent", "POST", "/api/auth/login", body={"username": "sunil.agarwal", "password": "agent123"},
            tests=login_tests("agent2Token")),
        req("Register a fresh customer for this run", "POST", "/api/auth/register",
            body={"username": "{{newUser}}", "password": "password123", "fullname": "Postman Customer", "role": "SERVICE_AGENT"}, tests=f"""
{status(201)}
pm.test("Always a customer", () => pm.expect(pm.response.json().role).to.eql("CUSTOMER"));
pm.collectionVariables.set("customerToken", pm.response.json().token);
"""),
        req("Login as a seeded customer", "POST", "/api/auth/login", body={"username": "diya.patel", "password": "customer123"},
            tests=login_tests("customer2Token")),
        req("Wrong password is 401", "POST", "/api/auth/login", body={"username": "agent", "password": "wrong"}, tests=status(401)),
    ]),
    folder("2. Register products", [
        req("Categories", "GET", "/api/products/categories", token="customerToken", tests=f"""
{status(200)}
pm.test("Labelled categories", () => pm.expect(pm.response.json()[0]).to.have.keys("value", "label"));
"""),
        req("Register a product under warranty (A)", "POST", "/api/products", token="customerToken", body=product("PM{{runId}}-a"), tests=f"""
{status(201)}
const p = pm.response.json();
pm.test("Serial is upper-cased, warranty is active", () => {{
  pm.expect(p.serialNumber).to.eql("PM" + pm.collectionVariables.get("runId") + "-A");
  pm.expect(p.warrantyActive).to.be.true;
  pm.expect(p.daysLeft).to.be.above(200);
  pm.expect(p.hasActiveClaim).to.be.false;
}});
pm.collectionVariables.set("productAId", p.id);
"""),
        req("Register an expired product (B)", "POST", "/api/products", token="customerToken",
            body=product("PM{{runId}}-B", bought="{{bought30m}}", name="Old phone"), tests=f"""
{status(201)}
pm.test("Registered but not covered", () => {{ const p = pm.response.json(); pm.expect(p.warrantyActive).to.be.false; pm.expect(p.daysLeft).to.be.below(0); }});
pm.collectionVariables.set("productBId", pm.response.json().id);
"""),
        req("Same serial again (different case) is rejected", "POST", "/api/products", token="customerToken", body=product("pm{{runId}}-a"), tests=f"""
{status(400)}
pm.test("Serial field error", () => pm.expect(pm.response.json().fieldErrors.serialNumber).to.include("already registered"));
"""),
        req("Another customer cannot reuse the serial", "POST", "/api/products", token="customer2Token", body=product("PM{{runId}}-A"), tests=f"""
{status(400)}
pm.test("Serial field error", () => pm.expect(pm.response.json().fieldErrors).to.have.property("serialNumber"));
"""),
        req("Future purchase date is rejected", "POST", "/api/products", token="customerToken", body=product("PM{{runId}}-F", bought="{{tomorrow}}"), tests=f"""
{status(400)}
pm.test("Date field error", () => pm.expect(pm.response.json().fieldErrors.purchaseDate).to.include("future"));
"""),
        req("Purchase more than 10 years ago is rejected", "POST", "/api/products", token="customerToken", body=product("PM{{runId}}-O", bought="{{tooOld}}"), tests=status(400)),
        req("Unknown category is rejected", "POST", "/api/products", token="customerToken", body=product("PM{{runId}}-C", category="YACHT"), tests=f"""
{status(400)}
pm.test("Category field error", () => pm.expect(pm.response.json().fieldErrors).to.have.property("category"));
"""),
        req("Missing fields list every problem", "POST", "/api/products", token="customerToken",
            body={"name": "", "brand": "", "category": "", "serialNumber": "x", "warrantyMonths": 99}, tests=f"""
{status(400)}
pm.test("All bad fields are listed", () => pm.expect(pm.response.json().fieldErrors).to.include.keys("name", "brand", "category", "serialNumber", "purchaseDate", "warrantyMonths"));
"""),
    ]),
    folder("3. Customer views products", [
        req("My products", "GET", "/api/products/my", token="customerToken", tests=f"""
{status(200)}
pm.test("Exactly my two", () => pm.expect(pm.response.json().length).to.eql(2));
"""),
        req("Search by serial", "GET", "/api/products/my?search=PM{{runId}}-a", token="customerToken", tests=f"""
{status(200)}
pm.test("Finds product A", () => {{ const l = pm.response.json(); pm.expect(l.length).to.eql(1); pm.expect(l[0].id).to.eql(pm.collectionVariables.get("productAId")); }});
"""),
        req("Another customer does not see my products", "GET", "/api/products/my?search=PM{{runId}}", token="customer2Token", tests=f"""
{status(200)}
pm.test("None", () => pm.expect(pm.response.json().length).to.eql(0));
"""),
    ]),
    folder("4. File claims", [
        req("Expired warranty cannot generate a claim", "POST", "/api/claims", token="customerToken", body=claim("{{productBId}}"), tests=f"""
{status(400)}
pm.test("Says the warranty expired", () => pm.expect(pm.response.json().message).to.include("expired"));
"""),
        req("Claim on product A (high priority)", "POST", "/api/claims", token="customerToken", body=claim(), tests=f"""
{status(201)}
const c = pm.response.json();
pm.test("Pending, high priority", () => {{ pm.expect(c.status).to.eql("PENDING"); pm.expect(c.priority).to.eql("HIGH"); }});
pm.collectionVariables.set("claimId", c.id);
"""),
        req("A second active claim for the same product is a conflict", "POST", "/api/claims", token="customerToken", body=claim(text="Again"), tests=f"""
{status(409)}
pm.test("Explains the open claim", () => pm.expect(pm.response.json().message).to.include("open claim"));
"""),
        req("Someone else's product is 404", "POST", "/api/claims", token="customer2Token", body=claim(text="Not mine"), tests=status(404)),
        req("Missing fields are reported", "POST", "/api/claims", token="customerToken", body={}, tests=f"""
{status(400)}
pm.test("Field errors", () => pm.expect(pm.response.json().fieldErrors).to.include.keys("productId", "issueDescription"));
"""),
        req("Invalid priority is rejected", "POST", "/api/claims", token="customerToken", body=claim(priority="URGENT"), tests=f"""
{status(400)}
pm.test("Priority field error", () => pm.expect(pm.response.json().fieldErrors).to.have.property("priority"));
"""),
        req("The product now shows an open claim", "GET", "/api/products/my?search=PM{{runId}}-a", token="customerToken", tests=f"""
{status(200)}
pm.test("hasActiveClaim", () => pm.expect(pm.response.json()[0].hasActiveClaim).to.be.true);
"""),
    ]),
    folder("5. Agent handles the claim", [
        req("Pending queue includes it", "GET", "/api/agent/claims?status=PENDING&search=PM{{runId}}", token="agentToken", tests=f"""
{status(200)}
pm.test("Found with customer details", () => {{ const l = pm.response.json(); pm.expect(l.length).to.eql(1); pm.expect(l[0].customerUsername).to.eql(pm.collectionVariables.get("newUser")); }});
"""),
        req("Summary has all five statuses", "GET", "/api/agent/summary", token="agentToken", tests=f"""
{status(200)}
pm.test("Five rows", () => pm.expect(pm.response.json().length).to.eql(5));
"""),
        req("Start repair before approval is a conflict", "PUT", "/api/agent/claims/{{claimId}}/start-repair", token="agentToken", tests=status(409)),
        req("Complete before repair is a conflict", "PUT", "/api/agent/claims/{{claimId}}/complete", token="agentToken", tests=status(409)),
        req("Reject without a reason is 400", "PUT", "/api/agent/claims/{{claimId}}/reject", token="agentToken", body={"reason": ""}, tests=f"""
{status(400)}
pm.test("Reason field error", () => pm.expect(pm.response.json().fieldErrors).to.have.property("reason"));
"""),
        req("Approve", "PUT", "/api/agent/claims/{{claimId}}/approve", token="agentToken", tests=f"""
{status(200)}
pm.test("Approved and recorded", () => {{ const c = pm.response.json(); pm.expect(c.status).to.eql("APPROVED"); pm.expect(c.decidedBy).to.eql("agent"); }});
"""),
        req("Approving again is a conflict", "PUT", "/api/agent/claims/{{claimId}}/approve", token="agent2Token", tests=status(409)),
        req("An approved claim cannot be rejected", "PUT", "/api/agent/claims/{{claimId}}/reject", token="agent2Token", body={"reason": "Too late"}, tests=status(409)),
        req("Start repair", "PUT", "/api/agent/claims/{{claimId}}/start-repair", token="agentToken", tests=f"""
{status(200)}
pm.test("In repair", () => {{ const c = pm.response.json(); pm.expect(c.status).to.eql("IN_REPAIR"); pm.expect(c.repairStartedAt).to.be.a("string"); }});
"""),
        req("A claim in repair still blocks a new one", "POST", "/api/claims", token="customerToken", body=claim(text="Another"), tests=status(409)),
        req("Complete with a note", "PUT", "/api/agent/claims/{{claimId}}/complete", token="agentToken", body={"resolutionNote": "Replaced the display panel"}, tests=f"""
{status(200)}
pm.test("Completed with the note", () => {{ const c = pm.response.json(); pm.expect(c.status).to.eql("COMPLETED"); pm.expect(c.resolutionNote).to.eql("Replaced the display panel"); }});
"""),
        req("Starting repair again is a conflict", "PUT", "/api/agent/claims/{{claimId}}/start-repair", token="agentToken", tests=status(409)),
        req("Unknown claim is 404", "PUT", "/api/agent/claims/999999/approve", token="agentToken", tests=status(404)),
    ]),
    folder("6. Customer sees the outcome", [
        req("Completed claim shows the resolution", "GET", "/api/claims/my?status=COMPLETED", token="customerToken", tests=f"""
{status(200)}
pm.test("Resolution visible", () => pm.expect(pm.response.json()[0].resolutionNote).to.include("display panel"));
"""),
        req("A finished claim no longer blocks a new one", "POST", "/api/claims", token="customerToken", body=claim(text="Now the speaker crackles", priority="LOW"), tests=f"""
{status(201)}
pm.collectionVariables.set("claim2Id", pm.response.json().id);
"""),
        req("Agent rejects it with a reason", "PUT", "/api/agent/claims/{{claim2Id}}/reject", token="agentToken", body={"reason": "Physical damage is not covered"}, tests=f"""
{status(200)}
pm.test("Rejected with the reason", () => {{ const c = pm.response.json(); pm.expect(c.status).to.eql("REJECTED"); pm.expect(c.rejectionReason).to.include("not covered"); }});
"""),
        req("Customer reads the rejection reason", "GET", "/api/claims/my?status=REJECTED", token="customerToken", tests=f"""
{status(200)}
pm.test("Reason visible", () => pm.expect(pm.response.json()[0].rejectionReason).to.include("not covered"));
"""),
        req("Another customer sees none of these claims", "GET", "/api/claims/my", token="customer2Token", tests=f"""
{status(200)}
pm.test("Not mine", () => pm.expect(pm.response.json().some(c => c.id == pm.collectionVariables.get("claimId"))).to.be.false);
"""),
    ]),
    folder("7. Security checks", [
        req("No token is 401", "GET", "/api/products/my", tests=status(401)),
        req("Customer cannot open the queue", "GET", "/api/agent/claims", token="customerToken", tests=status(403)),
        req("Customer cannot approve", "PUT", "/api/agent/claims/{{claim2Id}}/approve", token="customerToken", tests=status(403)),
        req("Customer cannot see the summary", "GET", "/api/agent/summary", token="customerToken", tests=status(403)),
        req("Agent cannot register products", "POST", "/api/products", token="agentToken", body=product("PM{{runId}}-X"), tests=status(403)),
        req("Agent cannot file claims", "POST", "/api/claims", token="agentToken", body=claim(), tests=status(403)),
        req("Agent has no personal claims list", "GET", "/api/claims/my", token="agentToken", tests=status(403)),
    ]),
]

write_collection(
    sys.argv[1], "Warranty Claims API",
    "Walks the warranty workflow: registering products, warranty expiry, filing claims, duplicate-claim protection, agent decisions through repair, rejection reasons and security.\n"
    "Start the backend first (http://localhost:8107). Repeatable: it registers a fresh customer and uses unique serial numbers.",
    items, {"baseUrl": "http://localhost:8107", **{k: "" for k in [
        "agentToken", "agent2Token", "customerToken", "customer2Token", "productAId", "productBId", "claimId", "claim2Id",
        "runId", "newUser", "bought2m", "bought30m", "tooOld", "tomorrow"]}})
