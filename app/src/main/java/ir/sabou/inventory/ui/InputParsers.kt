package ir.sabou.inventory.ui

import ir.sabou.inventory.core.MoneyRial
import ir.sabou.inventory.core.QuantityMicros
import java.math.BigDecimal
import java.math.RoundingMode

private val persianDigits = "۰۱۲۳۴۵۶۷۸۹"
private val arabicDigits = "٠١٢٣٤٥٦٧٨٩"

fun normalizeNumberInput(value: String): String = buildString(value.length) {
    value.forEach { character ->
        when {
            character in persianDigits -> append(persianDigits.indexOf(character))
            character in arabicDigits -> append(arabicDigits.indexOf(character))
            character == '٬' || character == ',' || character.isWhitespace() -> Unit
            character == '٫' -> append('.')
            else -> append(character)
        }
    }
}

fun parseMoneyRial(value: String): MoneyRial {
    val normalized = normalizeNumberInput(value)
    require(normalized.matches(Regex("""\d+"""))) {
        "مبلغ را به‌صورت عدد صحیح وارد کنید."
    }
    return MoneyRial.of(normalized.toLong())
}

fun parseQuantity(value: String): QuantityMicros {
    val normalized = normalizeNumberInput(value)
    require(normalized.matches(Regex("""\d+(\.\d{1,6})?"""))) {
        "مقدار کالا معتبر نیست."
    }
    val micros = BigDecimal(normalized)
        .movePointRight(6)
        .setScale(0, RoundingMode.UNNECESSARY)
        .longValueExact()
    return QuantityMicros.of(micros)
}

fun formatQuantity(micros: Long): String {
    val whole = micros / QuantityMicros.SCALE
    val fraction = (micros % QuantityMicros.SCALE).toString().padStart(6, '0').trimEnd('0')
    return if (fraction.isEmpty()) whole.toString() else "$whole.$fraction"
}
