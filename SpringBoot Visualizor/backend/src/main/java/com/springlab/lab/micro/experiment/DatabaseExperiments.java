package com.springlab.lab.micro.experiment;

import com.springlab.lab.micro.contract.InventoryContract;
import com.springlab.lab.micro.contract.InventoryContract.DeleteStock;
import com.springlab.lab.micro.contract.InventoryContract.Level;
import com.springlab.lab.micro.contract.InventoryContract.Reserve;
import com.springlab.lab.micro.contract.InventoryContract.Restock;
import com.springlab.lab.micro.contract.OrderContract;
import com.springlab.lab.micro.contract.OrderContract.PlaceOrder;
import com.springlab.lab.micro.contract.ProductContract;
import com.springlab.lab.micro.contract.ProductContract.ChangePrice;
import com.springlab.lab.micro.contract.ProductContract.DeleteProduct;
import com.springlab.lab.micro.contract.ProductContract.GetProduct;
import com.springlab.lab.micro.core.CallResult;
import com.springlab.lab.micro.core.ServiceBus;
import com.springlab.lab.micro.core.ServiceName;
import com.springlab.lab.micro.run.BoundaryProbe;
import com.springlab.lab.micro.run.BoundaryProbe.ProbeResult;
import com.springlab.lab.micro.run.BoundaryProbe.SharedQuery;
import com.springlab.lab.micro.run.ExperimentMeta;
import com.springlab.lab.micro.run.MicroExperiment;
import com.springlab.lab.micro.run.MicroRecorder;
import com.springlab.lab.micro.run.RunContext;
import com.springlab.lab.trace.CodeRefIds;
import com.springlab.lab.trace.LifecycleTrace.Evidence;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.springlab.lab.micro.run.MicroRecorder.ev;
import static com.springlab.lab.micro.run.MicroRecorder.nodes;

/**
 * Topic 5: database per service. Every service owns its tables; all the tables here live in ONE MySQL schema on one server, so the
 * isolation is LOGICAL: enforced by the lab code (a source-level boundary test and a runtime guard), not by separate database servers.
 */
public final class DatabaseExperiments {

    static final String LOGICAL = "LOGICAL isolation: separate tables in one MySQL schema, enforced by lab code. Separate physical databases would be enforced by the servers.";

    private DatabaseExperiments() {
    }

    // ------------------------------------------------------------------------------------------------------------------------

    @Component
    public static class OwnershipAndContracts implements MicroExperiment {

        @Override
        public ExperimentMeta meta() {
            return new ExperimentMeta("dbs-ownership-contracts", "database-per-service", "Each service owns its data; others ask through contracts",
                    "If the Order service writes orders and Inventory manages stock on its own, who knows what, and how does Order learn the stock level?",
                    "Each service reads and writes only its own tables. Inventory can change its stock without Order knowing. Order learns the stock level only by "
                            + "asking Inventory through its contract.",
                    "services", List.of());
        }

        @Override
        public void run(RunContext ctx) throws Exception {
            ServiceBus bus = ctx.bus();
            MicroRecorder rec = ctx.recorder();
            long[] ref = new long[2];

            rec.live("seed", "Fixtures: a customer and a product with 10 units", nodes("client", "user", "product", "inventory"), "client", "user",
                    "Created through the services that own them.", null, () -> {
                        bus.startRequest();
                        ref[0] = Lab.seedUser(bus, "100.00");
                        ref[1] = Lab.seedProduct(bus, "SKU-1", "4.50", 10);
                        return List.of(ev("Fixtures", "created"));
                    });

            rec.live("order-writes", "The Order service writes an order", nodes("client", "order", "order-db", "inventory", "inventory-db"), "client", "order",
                    "Order stores the order in ITS table and asks Inventory, through the contract, to reserve stock in Inventory's table.",
                    CodeRefIds.MICRO_ORDER_PLACE, () -> {
                        bus.startRequest();
                        CallResult<?> r = bus.call(null, OrderContract.PLACE_ORDER, PlaceOrder.simple(ref[0], ref[1], 2), Lab.SETUP);
                        return List.of(ev("Caller sees", Lab.outcome(r)), Lab.ordersEv(ctx, "PLACED"), Lab.stockEv(ctx, ref[1]));
                    });

            long[] ordersBefore = new long[1];
            rec.live("inventory-alone", "Inventory changes its stock by itself", nodes("client", "inventory", "inventory-db"), "client", "inventory",
                    "A delivery arrives and Inventory adds stock. It writes only its own table and tells nobody: the Order service data is untouched.",
                    CodeRefIds.MICRO_INVENTORY_RESERVE, () -> {
                        ordersBefore[0] = Lab.orders(ctx, "PLACED");
                        bus.startRequest();
                        CallResult<?> r = bus.call(null, InventoryContract.RESTOCK, new Restock(ref[1], 5), Lab.SETUP);
                        return List.of(ev("Caller sees", Lab.outcome(r)), Lab.stockEv(ctx, ref[1]),
                                ev("Orders PLACED unchanged " + Lab.INSPECTOR_NOTE, Lab.orders(ctx, "PLACED") == ordersBefore[0]));
                    });

            rec.live("ask-by-contract", "Order asks Inventory for the stock level", nodes("order", "inventory", "inventory-db"), "order", "inventory",
                    "Order cannot read the stock table, so it uses the contract. The answer is what Inventory chooses to tell it.", CodeRefIds.MICRO_BUS_CALL, () -> {
                        bus.startRequest();
                        CallResult<InventoryContract.StockLevel> r = bus.call(ServiceName.ORDER, InventoryContract.STOCK_LEVEL, new Level(ref[1]), Lab.SETUP);
                        return List.of(ev("Caller sees", Lab.outcome(r)), ev("Stock level answered by Inventory", r.value().available()));
                    });

            Map<String, Set<String>> touched = ctx.recorder().tablesTouchedByService();
            List<Evidence> evidence = new ArrayList<>();
            touched.forEach((service, tables) -> evidence.add(ev("Tables touched by the " + service + " service", String.join(", ", tables))));
            evidence.add(ev("Services that issued SQL", touched.size()));
            evidence.add(ev("Physical databases they used", "1 (every service connection goes to the same DataSource: one MySQL schema)"));
            rec.infer("tables-per-service", "Whose table is whose, and where do they live?", nodes("order", "inventory", "user", "product"),
                    "Calculated from the SQL each service handed to the JDBC driver. The ownership is real; the physical separation is not.", evidence);

            rec.concept("logical-vs-physical", "Logical isolation is not physical isolation", nodes("order-db", "inventory-db"),
                    LOGICAL + " With separate databases each service would have its own credentials and server, a failure or slowdown of one database would not "
                            + "reach the others, and a cross-service join would be impossible rather than merely forbidden. The lab keeps one schema so it can run "
                            + "everywhere without extra infrastructure.");
        }
    }

    // ------------------------------------------------------------------------------------------------------------------------

    @Component
    public static class ForbiddenAccess implements MicroExperiment {

        private final BoundaryProbe probe;

        ForbiddenAccess(BoundaryProbe probe) {
            this.probe = probe;
        }

        @Override
        public ExperimentMeta meta() {
            return new ExperimentMeta("dbs-forbidden-access", "database-per-service", "What happens when a service reaches into another service's tables",
                    "What if the Order service tries to read the stock table directly instead of asking Inventory?",
                    "The lab refuses the statement before it reaches MySQL. Reading its own table works, and asking Inventory through the contract works. "
                            + "With separate physical databases, the database servers themselves would refuse.",
                    "services", List.of());
        }

        @Override
        public void run(RunContext ctx) throws Exception {
            ServiceBus bus = ctx.bus();
            MicroRecorder rec = ctx.recorder();
            long[] ref = new long[2];

            rec.live("seed", "Fixtures: a customer and a product with 5 units", nodes("client", "user", "product", "inventory"), "client", "user",
                    "Created through the services that own them.", null, () -> {
                        bus.startRequest();
                        ref[0] = Lab.seedUser(bus, "100.00");
                        ref[1] = Lab.seedProduct(bus, "SKU-1", "4.50", 5);
                        return List.of(ev("Fixtures", "created"));
                    });

            rec.live("own-table", "The Order service reads its OWN table", nodes("order", "order-db"), "order", "order-db",
                    "Allowed: this is data the Order service owns.", CodeRefIds.MICRO_OWNERSHIP_GUARD, () -> {
                        ProbeResult r = probe.guardedRead(ServiceName.ORDER, "micro_lab_orders");
                        return List.of(ev("Result", r.detail()), ev("Succeeded", r.succeeded()));
                    });

            rec.live("foreign-table", "The Order service reads Inventory's stock table", nodes("order", "inventory-db"), "order", "inventory-db",
                    "Refused. The ownership guard stops the statement before it is sent to MySQL, so MySQL never sees it. The lab does this because here "
                            + "nothing in the database would: all tables are in one schema.", CodeRefIds.MICRO_OWNERSHIP_GUARD, () -> {
                        ProbeResult r = probe.guardedRead(ServiceName.ORDER, "micro_lab_stock");
                        return List.of(ev("Result", r.detail()), ev("Succeeded", r.succeeded()), ev("Statement sent to MySQL", false));
                    });

            rec.live("through-contract", "The Order service asks Inventory instead", nodes("order", "inventory", "inventory-db"), "order", "inventory",
                    "The permitted way. Inventory runs its own query on its own table and answers through its contract.", CodeRefIds.MICRO_BUS_CALL, () -> {
                        bus.startRequest();
                        CallResult<InventoryContract.StockLevel> r = bus.call(ServiceName.ORDER, InventoryContract.STOCK_LEVEL, new Level(ref[1]), Lab.SETUP);
                        return List.of(ev("Caller sees", Lab.outcome(r)), ev("Stock level answered by Inventory", r.value().available()));
                    });

            rec.concept("physical-enforcement", "Where the real enforcement would be", nodes("order-db", "inventory-db"),
                    "Real separation is done by the platform: each service gets its own database user (or its own database server) that simply has no "
                            + "access to the other tables. The guard in this lab imitates that refusal in code. It is a teaching device, not a security boundary.");
        }
    }

    // ------------------------------------------------------------------------------------------------------------------------

    @Component
    public static class ForeignKeys implements MicroExperiment {

        @Override
        public ExperimentMeta meta() {
            return new ExperimentMeta("dbs-foreign-keys", "database-per-service", "Foreign keys stop at the service boundary",
                    "Why do services refer to each other by plain id, and what is the cost?",
                    "Inside one service MySQL enforces foreign keys. Across services there is none, so a referenced row can disappear and the reference dangles. "
                            + "Data that is copied (a price snapshot) goes stale on purpose.",
                    "services", List.of());
        }

        @Override
        public void run(RunContext ctx) throws Exception {
            ServiceBus bus = ctx.bus();
            MicroRecorder rec = ctx.recorder();
            long[] ref = new long[3];
            String[] copied = new String[2];

            rec.live("seed", "Fixtures: a customer and two products", nodes("client", "user", "product", "inventory"), "client", "user",
                    "Created through the services that own them.", null, () -> {
                        bus.startRequest();
                        ref[0] = Lab.seedUser(bus, "100.00");
                        ref[1] = Lab.seedProduct(bus, "SKU-1", "4.50", 5);
                        ref[2] = Lab.seedProduct(bus, "SKU-2", "4.50", 5);
                        return List.of(ev("Fixtures", "created"));
                    });

            rec.live("inside-one-service", "Inside Inventory, MySQL enforces a foreign key", nodes("client", "inventory", "inventory-db"), "client", "inventory",
                    "A reservation points at its stock row with a real foreign key. Deleting the stock row while a reservation exists is refused by MySQL.",
                    CodeRefIds.MICRO_INVENTORY_RESERVE, () -> {
                        bus.startRequest();
                        bus.call(null, InventoryContract.RESERVE, new Reserve(900, ref[1], 1), Lab.SETUP);
                        CallResult<?> r = bus.call(null, InventoryContract.DELETE_STOCK, new DeleteStock(ref[1]), Lab.SETUP);
                        return List.of(ev("Caller sees", Lab.outcome(r)), Lab.stockEv(ctx, ref[1]),
                                ev("Stock row still exists " + Lab.INSPECTOR_NOTE, Lab.stock(ctx, ref[1]) >= 0));
                    });

            rec.live("across-services", "Across services nothing is enforced", nodes("client", "order", "order-db", "product", "product-db"), "client", "product",
                    "An order refers to product P by id. The Product service deletes P: nothing refuses it, because the order lives in another service's table. "
                            + "The order now points at a product that does not exist.", CodeRefIds.MICRO_ORDER_PLACE, () -> {
                        bus.startRequest();
                        bus.call(null, OrderContract.PLACE_ORDER, PlaceOrder.simple(ref[0], ref[2], 1), Lab.SETUP);
                        CallResult<?> deleted = bus.call(null, ProductContract.DELETE_PRODUCT, new DeleteProduct(ref[2]), Lab.SETUP);
                        CallResult<?> lookup = bus.call(ServiceName.ORDER, ProductContract.GET_PRODUCT, new GetProduct(ref[2]), Lab.SETUP);
                        return List.of(ev("Product deleted", Lab.outcome(deleted)), ev("Order for the deleted product still exists " + Lab.INSPECTOR_NOTE,
                                        Lab.ordersOf(ctx, ref[2], "PLACED") == 1),
                                ev("Order asks Product for it now", Lab.outcome(lookup)),
                                ev("Stock rows of the deleted product " + Lab.INSPECTOR_NOTE, ctx.inspectLong("select count(*) from micro_lab_stock where run_key = ? and product_ref = ?", ref[2])));
                    });

            rec.live("copied-data", "A copy made on purpose goes stale", nodes("client", "order", "order-db", "product", "product-db"), "client", "product",
                    "The Order service stores the unit price it was quoted. When the Product service changes the price, the order keeps the old one. That is "
                            + "usually what you want for an order, and it shows what duplication means: the copy is only as fresh as the last time it was taken.",
                    CodeRefIds.MICRO_ORDER_PLACE, () -> {
                        bus.startRequest();
                        bus.call(null, OrderContract.PLACE_ORDER, PlaceOrder.simple(ref[0], ref[1], 1), Lab.SETUP);
                        copied[0] = ctx.inspectString("select unit_price from micro_lab_orders where run_key = ? and product_ref = ?", ref[1]);
                        bus.call(null, ProductContract.CHANGE_PRICE, new ChangePrice(ref[1], new BigDecimal("6.00")), Lab.SETUP);
                        CallResult<ProductContract.ProductView> now = bus.call(ServiceName.ORDER, ProductContract.GET_PRODUCT, new GetProduct(ref[1]), Lab.SETUP);
                        copied[1] = now.value().price().toPlainString();
                        return List.of(ev("Price copied into the order " + Lab.INSPECTOR_NOTE, copied[0]), ev("Price now in the Product service", copied[1]));
                    });

            rec.infer("cost", "What the missing foreign key costs", nodes("order", "product"),
                    "Calculated from the results above; nothing here was observed directly.",
                    List.of(ev("Dangling reference possible across services", true), ev("Dangling reference possible inside one service", false),
                            ev("Copy differs from its source", !copied[0].equals(copied[1]))));

            rec.concept("living-without-fks", "How systems cope", nodes("order", "product"),
                    "Services refer to each other by id, treat NOT_FOUND as a normal answer, keep the copies they need (a price, a name) and update them from "
                            + "events when freshness matters. A cross-service foreign key would require both tables in one database, which removes the independence "
                            + "that separate services were meant to give.");
        }
    }

    // ------------------------------------------------------------------------------------------------------------------------

    @Component
    public static class SharedCoupling implements MicroExperiment {

        private final BoundaryProbe probe;

        SharedCoupling(BoundaryProbe probe) {
            this.probe = probe;
        }

        @Override
        public ExperimentMeta meta() {
            return new ExperimentMeta("dbs-shared-coupling", "database-per-service", "The shared-database counter-example",
                    "What if the Order service reads Inventory's table directly, and Inventory later changes its schema?",
                    "Direct access works until the owner changes the table. A shared table makes the schema a hidden public API: the change breaks the reader. "
                            + "A contract hides the schema, so the same change breaks nobody.",
                    "services", List.of());
        }

        @Override
        public void run(RunContext ctx) throws Exception {
            ServiceBus bus = ctx.bus();
            MicroRecorder rec = ctx.recorder();
            long[] ref = new long[2];
            boolean[] directBroke = new boolean[1];
            boolean[] contractBroke = new boolean[1];

            rec.live("seed", "Fixtures: a customer and a product with 5 units", nodes("client", "user", "product", "inventory"), "client", "user",
                    "Created through the services that own them.", null, () -> {
                        bus.startRequest();
                        ref[0] = Lab.seedUser(bus, "100.00");
                        ref[1] = Lab.seedProduct(bus, "SKU-1", "4.50", 5);
                        return List.of(ev("Fixtures", "created"));
                    });

            rec.live("direct-read", "Order reads the stock table directly (shared database)", nodes("order", "inventory-db"), "order", "inventory-db",
                    "A connection that belongs to no service, so nothing refuses it. While Order and Inventory agree on the column name, it works.",
                    CodeRefIds.MICRO_OWNERSHIP_GUARD, () -> {
                        ProbeResult r = probe.sharedDatabaseRead(SharedQuery.CURRENT_COLUMN, ref[1]);
                        return List.of(ev("Result", r.detail()), ev("Succeeded", r.succeeded()));
                    });

            rec.live("schema-changed", "Inventory changes its column; Order's query still uses the old name", nodes("order", "inventory-db"), "order", "inventory-db",
                    "The lab does not alter the schema. It runs the query as the Order team wrote it before Inventory renamed the column (the old name was 'qty'). "
                            + "MySQL reports a real error, which is exactly what the Order team would get on deployment day.",
                    CodeRefIds.MICRO_OWNERSHIP_GUARD, () -> {
                        ProbeResult r = probe.sharedDatabaseRead(SharedQuery.OLD_COLUMN, ref[1]);
                        directBroke[0] = !r.succeeded();
                        return List.of(ev("Result", r.detail()), ev("Succeeded", r.succeeded()),
                                ev("Simulation", "the query uses the pre-rename column name; the table was not altered"));
                    });

            rec.live("through-contract", "The same question through Inventory's contract", nodes("order", "inventory", "inventory-db"), "order", "inventory",
                    "Inventory answers from whatever its schema looks like today. The contract did not change, so the caller is unaffected.", CodeRefIds.MICRO_BUS_CALL, () -> {
                        bus.startRequest();
                        CallResult<InventoryContract.StockLevel> r = bus.call(ServiceName.ORDER, InventoryContract.STOCK_LEVEL, new Level(ref[1]), Lab.SETUP);
                        contractBroke[0] = !r.isSuccess();
                        return List.of(ev("Caller sees", Lab.outcome(r)), ev("Stock level answered by Inventory", r.value().available()));
                    });

            rec.infer("coupling", "What the sharing cost", nodes("order", "inventory"),
                    "Calculated from the two results in the steps above.",
                    List.of(ev("Direct access broke when the schema changed", directBroke[0]), ev("Contract access broke when the schema changed", contractBroke[0]),
                            ev("Teams that had to coordinate the change (direct access)", "2: Inventory and every reader of its table"),
                            ev("Teams that had to coordinate the change (contract)", "1: Inventory")));

            rec.concept("other-costs", "More costs of a shared database", nodes("order-db", "inventory-db"),
                    "Beyond schema coupling, services that share tables also share locks and load (a slow report from one team can block another), cannot be "
                            + "deployed or migrated independently, and cannot choose different storage technologies. A shared database can be a pragmatic choice, "
                            + "but then the services are not independent in the way microservices promise.");
        }
    }
}
