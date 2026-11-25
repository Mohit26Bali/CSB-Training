package shop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.exceptions.misusing.InvalidUseOfMatchersException;
import org.mockito.exceptions.verification.TooManyActualInvocations;
import shop.Ports.InventoryClient;
import shop.Ports.PaymentGateway;
import shop.Ports.PaymentResult;

/** The puzzles from the notes, each with the answer asserted. */
class PuzzlesTest {

    @Test
    void puzzle1_aMockReturnsWhatWasStubbedAndDefaultsOtherwise() {
        @SuppressWarnings("unchecked")
        List<String> list = mock(List.class);
        when(list.get(0)).thenReturn("a");
        assertThat(list.get(0)).isEqualTo("a");
        assertThat(list.get(1)).isNull();
        assertThat(list.size()).isZero();
    }

    @Test
    void puzzle2_matchersCannotBeMixedWithRawValues() {
        InventoryClient stock = mock(InventoryClient.class);
        assertThatThrownBy(() -> when(stock.reserve(anyString(), 2)).thenReturn(true))
            .isInstanceOf(InvalidUseOfMatchersException.class);
        when(stock.reserve(anyString(), eq(2))).thenReturn(true);          // fixed: all matchers
        assertThat(stock.reserve("any", 2)).isTrue();
    }

    @Test
    void puzzle3_stubbingASpyWithWhenCallsTheRealMethod() {
        List<String> spied = spy(new ArrayList<String>());
        assertThatThrownBy(() -> when(spied.get(0)).thenReturn("x")).isInstanceOf(IndexOutOfBoundsException.class);
        doReturn("x").when(spied).get(0);                                  // fixed: the real get(0) is not called
        assertThat(spied.get(0)).isEqualTo("x");
    }

    @Test
    void puzzle4_verifyDefaultsToExactlyOnce() {
        PaymentGateway gateway = mock(PaymentGateway.class);
        gateway.charge("c", 100);
        gateway.charge("c", 100);
        assertThatThrownBy(() -> verify(gateway).charge("c", 100)).isInstanceOf(TooManyActualInvocations.class);
    }

    int counter;                                                            // an instance field: reset for every test

    @Test void puzzle5_firstTest()  { counter++; assertEquals(1, counter); }
    @Test void puzzle5_secondTest() { counter++; assertEquals(1, counter); }

    @Test
    void puzzle6_assertEqualsTakesExpectedFirst() {
        AssertionError error = org.junit.jupiter.api.Assertions.assertThrows(AssertionError.class, () -> assertEquals(5, 3 + 3));
        assertThat(error.getMessage()).isEqualTo("expected: <5> but was: <6>");
    }

    @Test
    void puzzle7_finalTypesCanBeMockedWithMockito5() {
        PaymentResult result = mock(PaymentResult.class);                   // a record is final; Mockito 5 mocks it anyway
        assertThat(result.approved()).isFalse();
        assertThat(result.reference()).isNull();
    }

    @Test
    void puzzle8_aStubbedMockIgnoresTheRealImplementationEntirely() {
        Cart cart = mock(Cart.class);
        when(cart.total()).thenReturn(5);
        cart.add("A", 100, 3);                                              // does nothing on a mock
        assertThat(cart.total()).isEqualTo(5);
        assertThat(cart.items()).isEmpty();                                 // a mocked List-returning method gives an empty list
        assertThat(cart.isEmpty()).isFalse();                               // boolean default
        assertThat(cart.totalWithDiscount("SAVE10")).isZero();              // int default
        verify(cart).add("A", 100, 3);
    }
}
