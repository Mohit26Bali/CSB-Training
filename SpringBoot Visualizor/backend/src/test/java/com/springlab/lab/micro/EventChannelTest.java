package com.springlab.lab.micro;

import com.springlab.lab.micro.core.Bulkheads;
import com.springlab.lab.micro.core.CallOptions;
import com.springlab.lab.micro.core.CallRecord;
import com.springlab.lab.micro.core.CallResult;
import com.springlab.lab.micro.core.EventChannel;
import com.springlab.lab.micro.core.EventMessage;
import com.springlab.lab.micro.core.HandlerRegistry;
import com.springlab.lab.micro.core.Operation;
import com.springlab.lab.micro.core.Outcome;
import com.springlab.lab.micro.core.ServiceBehaviours;
import com.springlab.lab.micro.core.ServiceBehaviours.Behaviour;
import com.springlab.lab.micro.core.ServiceBus;
import com.springlab.lab.micro.core.ServiceModule;
import com.springlab.lab.micro.core.ServiceName;
import com.springlab.lab.micro.core.ServiceResult;
import com.springlab.lab.micro.core.SimClock;
import com.springlab.lab.micro.core.TraceBook;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The SIMULATED event channel and per-instance behaviour, without a database. */
class EventChannelTest {

    static final Operation<EventMessage, String> CONSUME = new Operation<>(ServiceName.NOTIFICATION, "consume");
    static final Operation<String, String> PING = new Operation<>(ServiceName.INVENTORY, "ping");

    final List<String> consumed = new ArrayList<>();
    final ServiceBehaviours behaviours = new ServiceBehaviours();
    final TraceBook traces = new TraceBook();
    ServiceBus bus;
    EventChannel channel;

    void setUp() {
        ServiceModule notification = new ServiceModule() {
            public ServiceName service() {
                return ServiceName.NOTIFICATION;
            }

            public void register(HandlerRegistry.Registrar r) {
                r.on(CONSUME, (ctx, m) -> {
                    consumed.add(m.messageId());
                    return ServiceResult.ok("ok");
                });
            }
        };
        ServiceModule inventory = new ServiceModule() {
            public ServiceName service() {
                return ServiceName.INVENTORY;
            }

            public void register(HandlerRegistry.Registrar r) {
                r.on(PING, (ctx, q) -> ServiceResult.ok("pong"));
            }
        };
        bus = new ServiceBus(new HandlerRegistry(List.of(notification, inventory)), new SimClock(), behaviours,
                new Bulkheads(Bulkheads.Policy.perService(2)), traces, true);
        bus.startRequest();
        channel = new EventChannel(bus);
        channel.subscribe("orders", "notification", CONSUME);
    }

    @Test
    void publishingDoesNotInvokeTheConsumerUntilDeliveryIsRequested() {
        setUp();
        EventMessage m = channel.publish("orders", "OrderPlaced", 7);
        assertThat(m.messageId()).isEqualTo("msg-1");
        assertThat(consumed).isEmpty();
        assertThat(channel.pending("notification")).isEqualTo(1);
        assertThat(traces.callCount()).as("the sender made no call").isZero();
    }

    @Test
    void deliveryIsRecordedAsAnEventCallFromTheBrokerNotAsASynchronousCall() {
        setUp();
        channel.publish("orders", "OrderPlaced", 7);
        List<EventChannel.Attempt> attempts = channel.deliverPending("notification", CallOptions.defaults());
        assertThat(attempts).hasSize(1);
        assertThat(attempts.get(0).outcome()).isEqualTo(Outcome.SUCCESS);
        CallRecord call = traces.calls().get(0);
        assertThat(call.kind()).isEqualTo("EVENT");
        assertThat(call.from()).isEqualTo("broker");
        assertThat(call.describe()).startsWith("[event] broker");
        assertThat(channel.pending("notification")).isZero();
    }

    @Test
    void anUnavailableConsumerLeavesTheEventPendingAndItIsDeliveredLater() {
        setUp();
        channel.publish("orders", "OrderPlaced", 7);
        behaviours.set(ServiceName.NOTIFICATION, Behaviour.down());
        List<EventChannel.Attempt> failed = channel.deliverPending("notification", CallOptions.defaults());
        assertThat(failed.get(0).outcome()).isEqualTo(Outcome.UNAVAILABLE);
        assertThat(channel.pending("notification")).as("not acknowledged, so still pending").isEqualTo(1);
        assertThat(consumed).isEmpty();

        behaviours.reset(ServiceName.NOTIFICATION);
        assertThat(channel.deliverPending("notification", CallOptions.defaults()).get(0).outcome()).isEqualTo(Outcome.SUCCESS);
        assertThat(consumed).containsExactly("msg-1");
        assertThat(channel.pending("notification")).isZero();
    }

    @Test
    void anAcknowledgedEventIsNotDeliveredAgainByDeliverPending() {
        setUp();
        channel.publish("orders", "OrderPlaced", 7);
        channel.deliverPending("notification", CallOptions.defaults());
        assertThat(channel.deliverPending("notification", CallOptions.defaults())).isEmpty();
        assertThat(consumed).hasSize(1);
    }

    @Test
    void redeliverSimulatesAtLeastOnceDuplicates() {
        setUp();
        EventMessage m = channel.publish("orders", "OrderPlaced", 7);
        channel.deliverPending("notification", CallOptions.defaults());
        EventChannel.Attempt again = channel.redeliver("notification", m.messageId(), CallOptions.defaults());
        assertThat(again.duplicate()).isTrue();
        assertThat(consumed).containsExactly("msg-1", "msg-1");
    }

    @Test
    void redeliveringAnUnknownMessageIsAProgrammingError() {
        setUp();
        assertThatThrownBy(() -> channel.redeliver("notification", "msg-99", CallOptions.defaults())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void eventsOnOtherTopicsAreNotDelivered() {
        setUp();
        channel.publish("payments", "PaymentTaken", 7);
        assertThat(channel.pending("notification")).isZero();
        assertThat(channel.deliverPending("notification", CallOptions.defaults())).isEmpty();
    }

    // ---- per-instance behaviour

    @Test
    void aBehaviourSetForOneInstanceAffectsOnlyCallsAimedAtThatInstance() {
        setUp();
        behaviours.setInstance(ServiceName.INVENTORY, "inventory-2", Behaviour.down());
        CallResult<String> dead = bus.call(null, PING, "x", CallOptions.defaults().onInstance("inventory-2"));
        CallResult<String> alive = bus.call(null, PING, "x", CallOptions.defaults().onInstance("inventory-1"));
        CallResult<String> unaimed = bus.call(null, PING, "x");
        assertThat(dead.outcome()).isEqualTo(Outcome.UNAVAILABLE);
        assertThat(alive.outcome()).isEqualTo(Outcome.SUCCESS);
        assertThat(unaimed.outcome()).isEqualTo(Outcome.SUCCESS);
        assertThat(traces.calls().get(0).instance()).isEqualTo("inventory-2");
        assertThat(traces.calls().get(0).describe()).contains("inventory#inventory-2");
        assertThat(traces.spans().get(0).instance()).isEqualTo("inventory-2");
    }

    @Test
    void anInstanceBehaviourOverridesTheServiceLevelBehaviourForThatInstance() {
        setUp();
        behaviours.set(ServiceName.INVENTORY, Behaviour.down());
        behaviours.setInstance(ServiceName.INVENTORY, "inventory-1", Behaviour.up());
        assertThat(bus.call(null, PING, "x", CallOptions.defaults().onInstance("inventory-1")).outcome()).isEqualTo(Outcome.SUCCESS);
        assertThat(bus.call(null, PING, "x").outcome()).isEqualTo(Outcome.UNAVAILABLE);
    }
}
