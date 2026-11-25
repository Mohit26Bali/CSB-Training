package shop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import shop.OrderService.OutOfStockException;
import shop.OrderService.PaymentDeclinedException;
import shop.OrderService.Receipt;
import shop.Ports.EmailService;
import shop.Ports.InventoryClient;
import shop.Ports.PaymentGateway;
import shop.Ports.PaymentResult;

@ExtendWith(MockitoExtension.class)                        // creates the mocks, injects them, and checks for unused stubs
class OrderServiceTest {

    static final Clock FIXED = Clock.fixed(Instant.parse("2026-11-25T10:00:00Z"), ZoneOffset.UTC);

    @Mock PaymentGateway payments;
    @Mock InventoryClient inventory;
    @Mock EmailService email;
    @Captor ArgumentCaptor<String> bodyCaptor;

    OrderService service;
    Cart cart;

    @BeforeEach
    void setUp() {
        service = new OrderService(payments, inventory, email, FIXED);   // constructor injection makes this one line
        cart = new Cart();
        cart.add("PEN", 1000, 2);
        cart.add("BOOK", 25000, 1);                                       // total 27000
    }

    // ---------- the happy path: stubbing and verifying ----------
    @Test
    void placesAnOrderChargesTheCustomerAndSendsAnEmail() {
        when(inventory.reserve(anyString(), anyInt())).thenReturn(true);
        when(payments.charge("cust-1", 27000)).thenReturn(new PaymentResult(true, "PAY-77"));

        Receipt receipt = service.placeOrder("cust-1", "asha@example.com", cart, null);

        assertThat(receipt.amount()).isEqualTo(27000);
        assertThat(receipt.paymentReference()).isEqualTo("PAY-77");
        assertThat(receipt.placedAt()).isEqualTo(Instant.parse("2026-11-25T10:00:00Z"));   // from the fixed clock
        verify(payments).charge("cust-1", 27000);                         // exactly once is the default
        verify(email).send(eq("asha@example.com"), anyString(), anyString());
        verify(inventory, never()).release(anyString(), anyInt());        // nothing was given back
    }

    @Test
    void theEmailBodyIsCapturedAndInspected() {
        when(inventory.reserve(anyString(), anyInt())).thenReturn(true);
        when(payments.charge(anyString(), anyInt())).thenReturn(new PaymentResult(true, "PAY-1"));

        service.placeOrder("cust-1", "asha@example.com", cart, "SAVE10");

        verify(email).send(eq("asha@example.com"), anyString(), bodyCaptor.capture());
        assertThat(bodyCaptor.getValue()).contains("24300");              // 27000 minus 10%
    }

    // ---------- failure paths ----------
    @Test
    void anItemOutOfStockReleasesWhatWasReservedAndNeverCharges() {
        when(inventory.reserve("PEN", 2)).thenReturn(true);
        when(inventory.reserve("BOOK", 1)).thenReturn(false);

        assertThatThrownBy(() -> service.placeOrder("cust-1", "asha@example.com", cart, null))
            .isInstanceOf(OutOfStockException.class)
            .hasMessage("out of stock: BOOK");

        verify(inventory).release("PEN", 2);                              // the first reservation was undone
        verify(inventory, never()).release(eq("BOOK"), anyInt());
        verifyNoInteractions(payments, email);                            // no money taken, no email sent
    }

    @Test
    void aDeclinedPaymentGivesTheStockBack() {
        when(inventory.reserve(anyString(), anyInt())).thenReturn(true);
        when(payments.charge(anyString(), anyInt())).thenReturn(new PaymentResult(false, null));

        assertThatThrownBy(() -> service.placeOrder("cust-1", "asha@example.com", cart, null))
            .isInstanceOf(PaymentDeclinedException.class);

        verify(inventory).release("PEN", 2);
        verify(inventory).release("BOOK", 1);
        verifyNoInteractions(email);
    }

    @Test
    void aGatewayThatThrowsStopsTheOrder() {
        when(inventory.reserve(anyString(), anyInt())).thenReturn(true);
        when(payments.charge(anyString(), anyInt())).thenThrow(new IllegalStateException("gateway timeout"));

        assertThatThrownBy(() -> service.placeOrder("cust-1", "asha@example.com", cart, null))
            .isInstanceOf(IllegalStateException.class).hasMessage("gateway timeout");
        verifyNoInteractions(email);
    }

    @Test
    void anEmptyCartIsRejectedBeforeAnythingIsCalled() {
        assertThatThrownBy(() -> service.placeOrder("cust-1", "a@b.c", new Cart(), null))
            .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(payments, inventory, email);
    }

    // ---------- verifying ORDER and the absence of other calls ----------
    @Test
    void thingsHappenInTheRightOrder() {
        when(inventory.reserve(anyString(), anyInt())).thenReturn(true);
        when(payments.charge(anyString(), anyInt())).thenReturn(new PaymentResult(true, "P"));

        service.placeOrder("cust-1", "a@b.c", cart, null);

        InOrder order = inOrder(inventory, payments, email);
        order.verify(inventory).reserve("PEN", 2);
        order.verify(inventory).reserve("BOOK", 1);
        order.verify(payments).charge("cust-1", 27000);
        order.verify(email).send(eq("a@b.c"), anyString(), anyString());
        verifyNoMoreInteractions(payments, email);
    }

    @Test
    void aSecondCallIsCountedWithTimes() {
        when(inventory.reserve(anyString(), anyInt())).thenReturn(true);
        when(payments.charge(anyString(), anyInt())).thenReturn(new PaymentResult(true, "P"));

        service.placeOrder("c1", "a@b.c", cart, null);
        service.placeOrder("c2", "a@b.c", cart, null);

        verify(payments, times(2)).charge(anyString(), eq(27000));
        verify(email, times(2)).send(anyString(), anyString(), anyString());
    }

    // ---------- BDD style: given / when / then ----------
    @Test
    void sameThingInBddStyle() {
        given(inventory.reserve(anyString(), anyInt())).willReturn(true);
        given(payments.charge(anyString(), anyInt())).willReturn(new PaymentResult(true, "PAY-9"));

        Receipt receipt = service.placeOrder("c1", "a@b.c", cart, null);

        then(payments).should().charge("c1", 27000);
        assertThat(receipt.paymentReference()).isEqualTo("PAY-9");
    }

    // ---------- static methods: the random order id ----------
    @Test
    void staticMethodsCanBeMockedInsideATryBlock() {
        UUID fixed = UUID.fromString("00000000-0000-0000-0000-000000000042");
        when(inventory.reserve(anyString(), anyInt())).thenReturn(true);
        when(payments.charge(anyString(), anyInt())).thenReturn(new PaymentResult(true, "P"));

        try (MockedStatic<UUID> uuid = mockStatic(UUID.class)) {          // only active inside this block, on this thread
            uuid.when(UUID::randomUUID).thenReturn(fixed);
            Receipt receipt = service.placeOrder("c1", "a@b.c", cart, null);
            assertThat(receipt.orderId()).isEqualTo("00000000-0000-0000-0000-000000000042");
        }
        assertThat(UUID.randomUUID()).isNotEqualTo(fixed);                // the real method is back
    }

    // ---------- void methods, answers, spies, consecutive calls: a standalone tour ----------
    @Test
    void voidMethodsUseDoThrowAndDoNothing() {
        EmailService mail = mock(EmailService.class);
        doThrow(new IllegalStateException("smtp down")).when(mail).send(eq("bad@x"), anyString(), anyString());
        doNothing().when(mail).send(eq("good@x"), anyString(), anyString());

        mail.send("good@x", "s", "b");
        assertThatThrownBy(() -> mail.send("bad@x", "s", "b")).hasMessage("smtp down");
    }

    @Test
    void consecutiveCallsAndAnswers() {
        InventoryClient stock = mock(InventoryClient.class);
        when(stock.reserve("X", 1)).thenReturn(true, true, false);       // true, true, then false forever
        assertThat(new boolean[] {stock.reserve("X", 1), stock.reserve("X", 1), stock.reserve("X", 1), stock.reserve("X", 1)})
            .containsExactly(true, true, false, false);

        PaymentGateway gateway = mock(PaymentGateway.class);
        when(gateway.charge(anyString(), anyInt())).thenAnswer(call -> {  // compute the result from the arguments
            int amount = call.getArgument(1);
            return new PaymentResult(amount < 50_000, "REF-" + amount);
        });
        assertThat(gateway.charge("c", 1000).approved()).isTrue();
        assertThat(gateway.charge("c", 90_000).approved()).isFalse();
    }

    @Test
    void aSpyIsARealObjectWhoseCallsAreRecorded() {
        Cart real = new Cart();
        Cart spied = spy(real);
        spied.add("A", 100, 3);
        assertThat(spied.total()).isEqualTo(300);                         // real behaviour
        verify(spied).add("A", 100, 3);                                   // and recorded
        org.mockito.Mockito.doReturn(999).when(spied).total();            // one method overridden
        assertThat(spied.total()).isEqualTo(999);
    }

    @Test
    void anUnstubbedMockReturnsDefaults() {
        PaymentGateway gateway = mock(PaymentGateway.class);
        InventoryClient stock = mock(InventoryClient.class);
        assertThat(gateway.charge("c", 1)).isNull();                      // objects: null
        assertThat(stock.reserve("X", 1)).isFalse();                      // boolean: false
    }
}
