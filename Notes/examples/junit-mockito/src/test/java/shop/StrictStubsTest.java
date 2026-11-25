package shop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.MockitoSession;
import org.mockito.Mockito;
import org.mockito.exceptions.misusing.PotentialStubbingProblem;
import org.mockito.exceptions.misusing.UnnecessaryStubbingException;
import org.mockito.quality.Strictness;
import shop.Ports.InventoryClient;

/** Shows what "strict stubs" (the default with MockitoExtension) catches, using a hand-made session. */
class StrictStubsTest {

    @Test
    void aStubThatIsNeverUsedIsReportedAsAMistake() {
        MockitoSession session = Mockito.mockitoSession().strictness(Strictness.STRICT_STUBS).startMocking();
        InventoryClient stock = mock(InventoryClient.class);
        when(stock.reserve("X", 1)).thenReturn(true);                    // stubbed, but the code under test never calls it

        assertThatThrownBy(session::finishMocking).isInstanceOf(UnnecessaryStubbingException.class);
    }

    @Test
    void callingAStubbedMethodWithOtherArgumentsFromTheCodeUnderTestIsReported() {
        MockitoSession session = Mockito.mockitoSession().strictness(Strictness.STRICT_STUBS).startMocking();
        InventoryClient stock = mock(InventoryClient.class);
        when(stock.reserve("PENN", 2)).thenReturn(true);                 // a typo: the service will ask for "PEN"

        Cart cart = new Cart();
        cart.add("PEN", 1000, 2);
        OrderService service = new OrderService(mock(Ports.PaymentGateway.class), stock, mock(Ports.EmailService.class), java.time.Clock.systemUTC());

        // The mismatch is reported when the call comes from the code under test (a different source file than the stub).
        assertThatThrownBy(() -> service.placeOrder("c1", "a@b.c", cart, null)).isInstanceOf(PotentialStubbingProblem.class);
        session.setStrictness(Strictness.LENIENT);
        session.finishMocking();
    }

    @Test
    void lenientStubsAreAllowedToGoUnused() {
        MockitoSession session = Mockito.mockitoSession().strictness(Strictness.STRICT_STUBS).startMocking();
        InventoryClient stock = mock(InventoryClient.class);
        lenient().when(stock.reserve("X", 1)).thenReturn(true);           // opt out for this one stub

        session.finishMocking();                                         // no exception
        assertThat(stock.reserve("X", 1)).isTrue();
    }
}
