# Use case 1: Food Delivery Order Management System

Source: `cases/All_Cases.docx`

**Business Problem:** Customers need to place food orders from a restaurant menu, while restaurant staff need to accept and update orders through a simple workflow.

**Users / Roles:** CUSTOMER, RESTAURANT_ADMIN

**Core Features:**

- Customer logs in and views available menu items.
- Customer adds items and quantities to a cart.
- Customer places an order.
- Customer views order history and current status.
- Restaurant admin views new orders and updates status.
- Order status progresses through PLACED, ACCEPTED, PREPARING, READY and DELIVERED.

**Business Rules / Validation:** Only active menu items can be ordered; quantity must be positive; an order must contain at least one item; only restaurant admins can change order status.

**Optional Bonus:** Category filtering, order total calculation, simple sales dashboard.

**7-Hour Scope Guidance:** Implement authentication, the main transaction workflow, JPA persistence, role-based authorization, REST APIs and a working React UI first. Bonus features should only be attempted after the core end-to-end flow is complete.

