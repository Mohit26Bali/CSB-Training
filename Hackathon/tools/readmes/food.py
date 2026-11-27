"""python tools/readmes/food.py food-delivery/README.md"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from readme_lib import build  # noqa: E402

text = build({
    "title": "Food Delivery System",
    "summary": "Customers browse a restaurant menu with photos, fill a cart, place an order and follow it from placed to delivered; the restaurant works an order board, "
               "manages the menu and sees sales. Spring Boot REST API with JWT security, plus a React front end.",
    "backend_port": 8101, "frontend_port": 5101, "db": "food_delivery", "package": "food",
    "logins": [("`restaurant`", "`admin123`", "RESTAURANT_ADMIN"), ("`kitchen`", "`admin123`", "RESTAURANT_ADMIN"),
               ("`aarav.sharma` (two delivered orders, a cancelled one and one being prepared now)", "`customer123`", "CUSTOMER"),
               ("`diya.patel`, `rohan.mehta`, … (30 customers)", "`customer123`", "CUSTOMER")],
    "features": [
        "**Customer:** a 28-dish menu with a photo, a veg / non-veg mark and a price, filters (category, vegetarian only, search), a cart that is kept in the browser, "
        "a checkout with address, mobile number and a note, a live total with tax and delivery, and My orders with a five-step progress tracker and cancel while still placed.",
        "**Restaurant:** a live order board (new, accepted, preparing, ready) with one-click next step and reject-with-reason, delivered and cancelled lists, search by order number, customer or phone, "
        "menu management (add, edit, switch a dish off when it sells out) and a sales page (today, 7 days, best sellers, orders by status).",
        "Category filtering, order total calculation and a simple sales dashboard (the case's bonus features).",
    ],
    "rules": [
        ("Only active menu items can be ordered; the message names the dish that is sold out", "`400` field error on `items`"),
        ("Every quantity is between 1 and 20, the same dish cannot appear twice and an order needs at least one dish", "`400` field errors"),
        ("The address (10 to 200 characters) and a 10 digit mobile number are required; the minimum order is ₹100", "`400`"),
        ("Prices always come from the menu, never from the browser; totals = items + 5% tax + ₹40 delivery (free from ₹500)", "calculated by the server"),
        ("Orders move one step at a time: PLACED, ACCEPTED, PREPARING, READY, DELIVERED; only the restaurant changes status", "`403` / `409`"),
        ("A customer can cancel only their own order and only while it is PLACED; the restaurant can reject until it is READY and must give a reason", "`403` / `409` / `400`"),
        ("The same dishes to the same address within 30 seconds of the last order are refused as a double click", "`409`"),
    ],
    "concurrency": "placing an order locks the customer row (under `READ_COMMITTED`), so five simultaneous clicks on Place order make one order; every status change and cancellation locks the order row first, "
                   "so two staff members accepting the same order accept it once, and a customer cancelling while the kitchen accepts ends in exactly one consistent state. The tests check this with parallel threads.",
    "notes": [
        "Dish photos are looked up by name (`/dishes/paneer-tikka.jpg`); without one a warm tile with an emoji is shown.",
        "Order lines keep the dish name and price from the moment of ordering, so editing the menu later does not change past orders.",
        "Payment is pay-on-delivery; there is no online payment.",
    ],
    "api": [
        ("GET", "/api/menu?category=&vegetarian=&search=", "any signed-in user", "Active dishes"),
        ("GET", "/api/menu/categories", "any signed-in user", "Categories in menu order"),
        ("POST", "/api/orders", "CUSTOMER", "Place `{items: [{menuItemId, quantity}], address, phone, notes?}`"),
        ("GET", "/api/orders/my?scope=active|past", "CUSTOMER", "Own orders, newest first"),
        ("GET", "/api/orders/{id}", "CUSTOMER", "One own order"),
        ("DELETE", "/api/orders/{id}?reason=", "CUSTOMER", "Cancel while PLACED"),
        ("GET", "/api/admin/orders?status=&search=", "RESTAURANT_ADMIN", "The order board, open orders first"),
        ("PUT", "/api/admin/orders/{id}/status", "RESTAURANT_ADMIN", "Next step `{status}`"),
        ("PUT", "/api/admin/orders/{id}/cancel", "RESTAURANT_ADMIN", "Reject with a reason `{reason}`"),
        ("GET/POST/PUT", "/api/admin/menu", "RESTAURANT_ADMIN", "List all dishes, add one, edit one (including switching it off)"),
        ("GET", "/api/admin/dashboard", "RESTAURANT_ADMIN", "Orders and revenue, best sellers, status counts, last 7 days"),
    ],
    "controllers": "AuthController, CustomerController, AdminController",
    "services": "FoodService (menu, ordering, price rules, status flow, dashboard), AuthService, LoginAttemptService",
    "repos": "UserRepository (row lock), MenuItemRepository, FoodOrderRepository (row lock)",
    "entities": "User, MenuItem, FoodOrder, OrderItem, Role, MenuCategory, OrderStatus",
    "tables": "`users`, `menu_items` (unique name, category, price, vegetarian, active), `food_orders` (customer FK, status and the time of every step, totals, address, phone) and `order_items` (order and dish FKs, the name and price copied at order time, quantity).",
    "tests": "24 tests on in-memory H2 (no MySQL needed): menu filters and management, totals, price snapshots, validation, double-click guard, order scopes, cancellation rules, status flow, board, dashboard, roles, parallel orders and status changes",
    "collection": "Food.postman_collection.json",
    "postman": "64 requests (106 assertions). It registers a fresh customer and a uniquely named dish each run, so it can be run many times",
    "limits": [
        "No online payment, delivery tracking on a map, coupons or ratings.",
        "The order board and sales figures are computed in memory (Java Streams), which suits one restaurant but would need paging and SQL aggregates at scale.",
    ],
})
with open(sys.argv[1], "w", encoding="utf-8", newline="\n") as f:
    f.write(text)
print("wrote", sys.argv[1])
