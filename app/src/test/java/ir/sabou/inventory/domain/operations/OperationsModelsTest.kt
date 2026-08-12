package ir.sabou.inventory.domain.operations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OperationsModelsTest {
    @Test
    fun supplierDraft_trimsStoredText() {
        val valid = SupplierDraft(
            name = "  لبنیات نمونه  ",
            phone = " 07100000000 ",
            paymentTermsDays = 12,
        ).validated()

        assertEquals("لبنیات نمونه", valid.name)
        assertEquals("07100000000", valid.phone)
        assertEquals(12, valid.paymentTermsDays)
    }

    @Test
    fun negativeInventoryThreshold_isRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            InventoryItemDraft(
                name = "برنج ایرانی",
                category = "خشکبار",
                unit = "کیلو",
                alertEnabled = true,
                alertThresholdMicros = -1,
                supplierId = null,
            ).validated()
        }
    }

    @Test
    fun settlementReminder_onlyBecomesDueForOpenPurchase() {
        val purchase = PurchaseSummary(
            id = 1,
            invoiceNo = "P-1",
            supplierName = "نمونه",
            purchaseEpochDay = 1,
            dueEpochDay = 10,
            totalRial = 1_000,
            paidRial = 200,
            isPaid = false,
            paymentStatus = "PARTIAL",
            paymentMethod = null,
            reminderEnabled = true,
            reminderEpochDay = 8,
        )

        assertEquals(true, purchase.reminderIsDue(8))
        assertEquals(false, purchase.reminderIsDue(7))
        assertEquals(800, purchase.outstandingRial)
    }
}
