"""python tools/collections/food.py food-delivery/docs/postman/Food.postman_collection.json"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from postman_lib import folder, login_tests, req, status, write_collection  # noqa: E402

PRE = """
const id = String(Math.floor(Math.random() * 900000 + 100000));
pm.collectionVariables.set("runId", id);
pm.collectionVariables.set("newUser", "postman." + id);
pm.collectionVariables.set("dishName", "Postman Special " + id);
pm.collectionVariables.set("dishNameLower", "postman special " + id);
"""

ADDRESS = "Flat 302, Green Park Apartments, Baner, Pune"


def order(*lines, address=ADDRESS, phone="9876543210", notes=None):
    return {"items": [{"menuItemId": i, "quantity": q} for i, q in lines], "address": address, "phone": phone, "notes": notes}


def dish(name="{{dishName}}", price=180, category="MAINS", active=True, vegetarian=True):
    return {"name": name, "description": "Created by the Postman run", "category": category, "price": price, "vegetarian": vegetarian, "active": active}


items = [
    folder("1. Auth", [
        req("Login as the restaurant", "POST", "/api/auth/login", body={"username": "restaurant", "password": "admin123"},
            tests=login_tests("adminToken"), pre=PRE),
        req("Register a fresh customer for this run", "POST", "/api/auth/register",
            body={"username": "{{newUser}}", "password": "password123", "fullname": "Postman Customer", "role": "RESTAURANT_ADMIN"}, tests=f"""
{status(201)}
pm.test("Always a customer", () => pm.expect(pm.response.json().role).to.eql("CUSTOMER"));
pm.collectionVariables.set("customerToken", pm.response.json().token);
"""),
        req("Login as another customer", "POST", "/api/auth/login", body={"username": "diya.patel", "password": "customer123"},
            tests=login_tests("otherToken")),
        req("Wrong password is 401", "POST", "/api/auth/login", body={"username": "restaurant", "password": "wrong"}, tests=status(401)),
    ]),
    folder("2. The menu", [
        req("Customers see only dishes that are on", "GET", "/api/menu", token="customerToken", tests=f"""
{status(200)}
const list = pm.response.json();
pm.test("Many dishes, all active, none sold out", () => {{ pm.expect(list.length).to.be.at.least(20); pm.expect(list.every(m => m.active)).to.be.true; pm.expect(list.some(m => m.name === "Mutton Rogan Josh")).to.be.false; }});
pm.collectionVariables.set("tikkaId", list.find(m => m.name === "Paneer Tikka").id);
pm.collectionVariables.set("naanId", list.find(m => m.name === "Butter Naan").id);
pm.collectionVariables.set("chaiId", list.find(m => m.name === "Masala Chai").id);
"""),
        req("Filter by category", "GET", "/api/menu?category=DESSERTS", token="customerToken", tests=f"""
{status(200)}
pm.test("Only desserts", () => {{ const l = pm.response.json(); pm.expect(l.length).to.be.at.least(3); pm.expect(l.every(m => m.category === "DESSERTS")).to.be.true; }});
"""),
        req("Vegetarian only", "GET", "/api/menu?vegetarian=true", token="customerToken", tests=f"""
{status(200)}
pm.test("No non-veg dish", () => pm.expect(pm.response.json().every(m => m.vegetarian)).to.be.true);
"""),
        req("Search by name", "GET", "/api/menu?search=biryani", token="customerToken", tests=f"""
{status(200)}
pm.test("Both biryanis", () => pm.expect(pm.response.json().length).to.eql(2));
"""),
        req("An unknown category is rejected", "GET", "/api/menu?category=PIZZA", token="customerToken", tests=status(400)),
        req("Categories", "GET", "/api/menu/categories", token="customerToken", tests=f"""
{status(200)}
pm.test("In menu order", () => pm.expect(pm.response.json()).to.eql(["STARTERS", "MAINS", "BREADS_AND_RICE", "DESSERTS", "DRINKS"]));
"""),
        req("No token is 401", "GET", "/api/menu", tests=status(401)),
    ]),
    folder("3. The restaurant manages the menu", [
        req("The restaurant sees sold-out dishes too", "GET", "/api/admin/menu?search=rogan", token="adminToken", tests=f"""
{status(200)}
pm.test("Rogan Josh is off", () => {{ pm.expect(pm.response.json().length).to.eql(1); pm.expect(pm.response.json()[0].active).to.be.false; }});
pm.collectionVariables.set("soldOutId", pm.response.json()[0].id);
"""),
        req("Empty dish form lists every problem", "POST", "/api/admin/menu", token="adminToken", body={}, tests=f"""
{status(400)}
pm.test("All bad fields", () => pm.expect(pm.response.json().fieldErrors).to.include.keys("name", "category", "price"));
"""),
        req("A tiny price is rejected", "POST", "/api/admin/menu", token="adminToken", body=dish(price=5), tests=f"""
{status(400)}
pm.test("Price error", () => pm.expect(pm.response.json().fieldErrors.price).to.include("at least 10"));
"""),
        req("A duplicate name is rejected in any case", "POST", "/api/admin/menu", token="adminToken", body=dish("paneer TIKKA"), tests=f"""
{status(400)}
pm.test("Name error", () => pm.expect(pm.response.json().fieldErrors.name).to.include("already a dish"));
"""),
        req("Customers cannot add dishes", "POST", "/api/admin/menu", token="customerToken", body=dish("Nope"), tests=status(403)),
        req("Add a dish", "POST", "/api/admin/menu", token="adminToken", body=dish(), tests=f"""
{status(201)}
const m = pm.response.json();
pm.test("On the menu with a picture key", () => {{ pm.expect(m.active).to.be.true; pm.expect(m.imageKey).to.match(/^postman-special-/); }});
pm.collectionVariables.set("newDishId", m.id);
"""),
        req("Customers can order it", "GET", "/api/menu?search={{runId}}", token="customerToken", tests=f"""
{status(200)}
pm.test("Listed", () => pm.expect(pm.response.json().length).to.eql(1));
"""),
        req("Mark it sold out at a new price", "PUT", "/api/admin/menu/{{newDishId}}", token="adminToken", body=dish(price=200, active=False), tests=f"""
{status(200)}
pm.test("Off, 200", () => {{ pm.expect(pm.response.json().active).to.be.false; pm.expect(pm.response.json().price).to.eql(200); }});
"""),
        req("Customers no longer see it", "GET", "/api/menu?search={{runId}}", token="customerToken", tests=f"""
{status(200)}
pm.test("Hidden", () => pm.expect(pm.response.json().length).to.eql(0));
"""),
    ]),
    folder("4. Order validation", [
        req("Empty order lists the problems", "POST", "/api/orders", token="customerToken", body={}, tests=f"""
{status(400)}
pm.test("Items, address and phone", () => pm.expect(pm.response.json().fieldErrors).to.include.keys("items", "address", "phone"));
"""),
        req("A quantity of zero is rejected", "POST", "/api/orders", token="customerToken", body=order(("{{tikkaId}}", 0)), tests=f"""
{status(400)}
pm.test("Quantity error", () => pm.expect(JSON.stringify(pm.response.json().fieldErrors)).to.include("at least 1"));
"""),
        req("A bad phone and a short address are rejected", "POST", "/api/orders", token="customerToken", body=order(("{{tikkaId}}", 1), address="short", phone="12345"), tests=f"""
{status(400)}
pm.test("Both explained", () => {{ const e = pm.response.json().fieldErrors; pm.expect(e.address).to.include("10 to 200"); pm.expect(e.phone).to.include("10 digit"); }});
"""),
        req("The same dish twice is rejected", "POST", "/api/orders", token="customerToken", body=order(("{{tikkaId}}", 1), ("{{tikkaId}}", 2)), tests=f"""
{status(400)}
pm.test("Items error", () => pm.expect(pm.response.json().fieldErrors.items).to.include("twice"));
"""),
        req("A sold-out dish cannot be ordered", "POST", "/api/orders", token="customerToken", body=order(("{{tikkaId}}", 1), ("{{soldOutId}}", 1)), tests=f"""
{status(400)}
pm.test("Names the dish", () => pm.expect(pm.response.json().fieldErrors.items).to.include("Mutton Rogan Josh"));
"""),
        req("An unknown dish is rejected", "POST", "/api/orders", token="customerToken", body=order((999999, 1)), tests=status(400)),
        req("Below the minimum order is rejected", "POST", "/api/orders", token="customerToken", body=order(("{{chaiId}}", 1)), tests=f"""
{status(400)}
pm.test("Minimum mentioned", () => pm.expect(pm.response.json().message).to.include("minimum order"));
"""),
        req("The restaurant cannot order", "POST", "/api/orders", token="adminToken", body=order(("{{tikkaId}}", 2)), tests=status(403)),
    ]),
    folder("5. Placing orders", [
        req("Place a big order (free delivery)", "POST", "/api/orders", token="customerToken", body=order(("{{tikkaId}}", 2), ("{{naanId}}", 2), notes="Less spicy please"), tests=f"""
{status(201)}
const o = pm.response.json();
pm.test("Totals come from the menu: 680 + 34 tax, no delivery fee", () => {{ pm.expect(o.subtotal).to.eql(680); pm.expect(o.tax).to.eql(34); pm.expect(o.deliveryFee).to.eql(0); pm.expect(o.total).to.eql(714); }});
pm.test("Placed, with lines and the next step", () => {{ pm.expect(o.status).to.eql("PLACED"); pm.expect(o.nextStatus).to.eql("ACCEPTED"); pm.expect(o.lines.length).to.eql(2); pm.expect(o.itemCount).to.eql(4); pm.expect(o.number).to.match(/^FD-\\d+$/); pm.expect(o.canCancel).to.be.true; }});
pm.collectionVariables.set("order1", o.id);
"""),
        req("The same order again is a double click", "POST", "/api/orders", token="customerToken", body=order(("{{naanId}}", 2), ("{{tikkaId}}", 2), notes="Less spicy please"), tests=f"""
{status(409)}
pm.test("Points at the first order", () => pm.expect(pm.response.json().message).to.include("just placed"));
"""),
        req("A small order pays the delivery fee", "POST", "/api/orders", token="customerToken", body=order(("{{tikkaId}}", 1)), tests=f"""
{status(201)}
const o = pm.response.json();
pm.test("280 + 14 tax + 40 delivery = 334", () => {{ pm.expect(o.subtotal).to.eql(280); pm.expect(o.tax).to.eql(14); pm.expect(o.deliveryFee).to.eql(40); pm.expect(o.total).to.eql(334); }});
pm.collectionVariables.set("order2", o.id);
"""),
        req("My active orders, newest first", "GET", "/api/orders/my?scope=active", token="customerToken", tests=f"""
{status(200)}
pm.test("Two, newest first", () => {{ const l = pm.response.json(); pm.expect(l.length).to.eql(2); pm.expect(l[0].id).to.be.above(l[1].id); }});
"""),
        req("A bad scope is rejected", "GET", "/api/orders/my?scope=later", token="customerToken", tests=status(400)),
        req("Another customer sees none of them", "GET", "/api/orders/my?scope=active", token="otherToken", tests=f"""
{status(200)}
pm.test("Not theirs", () => pm.expect(pm.response.json().some(o => o.id == pm.collectionVariables.get("order1"))).to.be.false);
"""),
        req("The restaurant sees the new orders first", "GET", "/api/admin/orders?search={{newUser}}", token="adminToken", tests=f"""
{status(200)}
pm.test("Both orders, with the customer's phone and address", () => {{ const l = pm.response.json(); pm.expect(l.length).to.eql(2); pm.expect(l[0].phone).to.eql("9876543210"); pm.expect(l[0].address).to.include("Baner"); }});
"""),
    ]),
    folder("6. The kitchen", [
        req("Customers cannot change the status", "PUT", "/api/admin/orders/{{order1}}/status", token="customerToken", body={"status": "ACCEPTED"}, tests=status(403)),
        req("A missing status is rejected", "PUT", "/api/admin/orders/{{order1}}/status", token="adminToken", body={}, tests=status(400)),
        req("Skipping a step is refused", "PUT", "/api/admin/orders/{{order1}}/status", token="adminToken", body={"status": "READY"}, tests=f"""
{status(409)}
pm.test("Says the next step", () => pm.expect(pm.response.json().message).to.include("can only move to ACCEPTED"));
"""),
        req("Accept", "PUT", "/api/admin/orders/{{order1}}/status", token="adminToken", body={"status": "ACCEPTED"}, tests=f"""
{status(200)}
pm.test("Accepted by the restaurant, no longer cancellable", () => {{ const o = pm.response.json(); pm.expect(o.status).to.eql("ACCEPTED"); pm.expect(o.acceptedBy).to.eql("restaurant"); pm.expect(o.canCancel).to.be.false; pm.expect(o.nextStatus).to.eql("PREPARING"); }});
"""),
        req("Going back is refused", "PUT", "/api/admin/orders/{{order1}}/status", token="adminToken", body={"status": "PLACED"}, tests=status(409)),
        req("Preparing", "PUT", "/api/admin/orders/{{order1}}/status", token="adminToken", body={"status": "PREPARING"}, tests=status(200)),
        req("The customer sees it being prepared", "GET", "/api/orders/{{order1}}", token="customerToken", tests=f"""
{status(200)}
pm.test("Preparing with times", () => {{ const o = pm.response.json(); pm.expect(o.status).to.eql("PREPARING"); pm.expect(o.acceptedAt).to.be.a("string"); pm.expect(o.preparingAt).to.be.a("string"); }});
"""),
        req("Another customer cannot open the order", "GET", "/api/orders/{{order1}}", token="otherToken", tests=status(403)),
        req("Ready", "PUT", "/api/admin/orders/{{order1}}/status", token="adminToken", body={"status": "READY"}, tests=status(200)),
        req("A ready order cannot be rejected", "PUT", "/api/admin/orders/{{order1}}/cancel", token="adminToken", body={"reason": "Too late"}, tests=status(409)),
        req("Delivered", "PUT", "/api/admin/orders/{{order1}}/status", token="adminToken", body={"status": "DELIVERED"}, tests=f"""
{status(200)}
pm.test("Delivered, nothing further", () => {{ pm.expect(pm.response.json().status).to.eql("DELIVERED"); pm.expect(pm.response.json().nextStatus).to.be.null; pm.expect(pm.response.json().deliveredAt).to.be.a("string"); }});
"""),
        req("A delivered order cannot move on", "PUT", "/api/admin/orders/{{order1}}/status", token="adminToken", body={"status": "DELIVERED"}, tests=status(409)),
        req("It shows in the delivered list", "GET", "/api/admin/orders?status=DELIVERED&search={{newUser}}", token="adminToken", tests=f"""
{status(200)}
pm.test("Found", () => pm.expect(pm.response.json().length).to.eql(1));
"""),
        req("It is now a past order for the customer", "GET", "/api/orders/my?scope=past", token="customerToken", tests=f"""
{status(200)}
pm.test("Delivered one is past", () => pm.expect(pm.response.json().some(o => o.id == pm.collectionVariables.get("order1"))).to.be.true);
"""),
    ]),
    folder("7. Cancelling", [
        req("Another customer cannot cancel it", "DELETE", "/api/orders/{{order2}}", token="otherToken", tests=status(403)),
        req("The customer cancels before acceptance", "DELETE", "/api/orders/{{order2}}?reason=Changed%20my%20mind", token="customerToken", tests=f"""
{status(200)}
pm.test("Cancelled by the customer", () => {{ const o = pm.response.json(); pm.expect(o.status).to.eql("CANCELLED"); pm.expect(o.cancelReason).to.eql("Changed my mind"); pm.expect(o.cancelledBy).to.eql(pm.collectionVariables.get("newUser")); }});
"""),
        req("Cancelling twice is a conflict", "DELETE", "/api/orders/{{order2}}", token="customerToken", tests=status(409)),
        req("Place a third order", "POST", "/api/orders", token="customerToken", body=order(("{{naanId}}", 4), ("{{chaiId}}", 2)), tests=f"""
{status(201)}
pm.test("340 subtotal", () => pm.expect(pm.response.json().subtotal).to.eql(340));
pm.collectionVariables.set("order3", pm.response.json().id);
"""),
        req("The restaurant accepts it", "PUT", "/api/admin/orders/{{order3}}/status", token="adminToken", body={"status": "ACCEPTED"}, tests=status(200)),
        req("The customer can no longer cancel", "DELETE", "/api/orders/{{order3}}", token="customerToken", tests=f"""
{status(409)}
pm.test("Already accepted", () => pm.expect(pm.response.json().message).to.include("already accepted"));
"""),
        req("The restaurant must give a reason to reject", "PUT", "/api/admin/orders/{{order3}}/cancel", token="adminToken", body={}, tests=f"""
{status(400)}
pm.test("Reason error", () => pm.expect(pm.response.json().fieldErrors).to.have.property("reason"));
"""),
        req("The restaurant rejects it", "PUT", "/api/admin/orders/{{order3}}/cancel", token="adminToken", body={"reason": "Tandoor is down"}, tests=f"""
{status(200)}
pm.test("Cancelled by the restaurant", () => {{ const o = pm.response.json(); pm.expect(o.status).to.eql("CANCELLED"); pm.expect(o.cancelledBy).to.eql("restaurant"); }});
"""),
        req("The customer sees the reason", "GET", "/api/orders/{{order3}}", token="customerToken", tests=f"""
{status(200)}
pm.test("Reason shown", () => pm.expect(pm.response.json().cancelReason).to.eql("Tandoor is down"));
"""),
        req("Unknown order is 404", "GET", "/api/orders/999999", token="customerToken", tests=status(404)),
    ]),
    folder("8. Sales and security", [
        req("Sales dashboard", "GET", "/api/admin/dashboard", token="adminToken", tests=f"""
{status(200)}
pm.test("Today, week, best sellers and status counts", () => {{
  const d = pm.response.json();
  pm.expect(d).to.include.keys("ordersToday", "revenueToday", "openOrders", "averageOrder", "deliveredThisWeek", "revenueThisWeek", "byStatus", "bestSellers", "lastSevenDays");
  pm.expect(d.lastSevenDays.length).to.eql(7);
  pm.expect(d.byStatus.length).to.eql(6);
  pm.expect(d.bestSellers.length).to.be.at.most(5);
  pm.expect(d.revenueToday).to.be.at.least(714);
}});
"""),
        req("No token is 401", "GET", "/api/orders/my", tests=status(401)),
        req("Customer cannot see the order board", "GET", "/api/admin/orders", token="customerToken", tests=status(403)),
        req("Customer cannot see sales", "GET", "/api/admin/dashboard", token="customerToken", tests=status(403)),
        req("The restaurant has no personal orders", "GET", "/api/orders/my", token="adminToken", tests=status(403)),
    ]),
]

write_collection(
    sys.argv[1], "Food Delivery API",
    "Walks the ordering workflow: the menu with filters, menu management, order validation, totals with tax and delivery fee, the double-click guard, "
    "the kitchen's step-by-step status changes, cancellation rules, the sales dashboard and security.\n"
    "Start the backend first (http://localhost:8101). Repeatable: it registers a fresh customer and a uniquely named dish each run.",
    items, {"baseUrl": "http://localhost:8101", **{k: "" for k in [
        "adminToken", "customerToken", "otherToken", "runId", "newUser", "dishName", "dishNameLower", "tikkaId", "naanId", "chaiId", "soldOutId",
        "newDishId", "order1", "order2", "order3"]}})
