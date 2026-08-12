package ir.sabou.inventory.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class InputParsersTest {
    @Test
    fun persianMoneyInput_isNormalizedToRial() {
        assertEquals(12_345_678L, parseMoneyRial("۱۲٬۳۴۵٬۶۷۸").value)
    }

    @Test
    fun decimalQuantity_hasSixDigitPrecision() {
        assertEquals(1_250_000L, parseQuantity("۱٫۲۵").value)
    }

    @Test
    fun quantityWithMoreThanSixDecimals_isRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            parseQuantity("1.0000001")
        }
    }
}
