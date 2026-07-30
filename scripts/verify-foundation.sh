#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

required_files=(
  "app/src/main/AndroidManifest.xml"
  "app/src/main/java/ir/sabou/inventory/core/MoneyRial.kt"
  "app/src/main/java/ir/sabou/inventory/core/QuantityMicros.kt"
  "app/src/main/java/ir/sabou/inventory/core/SignedLongMath.kt"
  "app/src/main/java/ir/sabou/inventory/data/db/AppDatabase.kt"
  "app/src/main/java/ir/sabou/inventory/data/repository/LocalPurchaseRepository.kt"
  "app/src/main/java/ir/sabou/inventory/data/repository/LocalOperationsRepository.kt"
  "app/src/main/java/ir/sabou/inventory/data/repository/LocalAccountingRepository.kt"
  "app/src/main/java/ir/sabou/inventory/data/security/DatabaseKeyProvider.kt"
  "app/src/main/java/ir/sabou/inventory/domain/accounting/AccountingModels.kt"
  "app/src/main/java/ir/sabou/inventory/ui/AccountingScreens.kt"
  "app/src/main/java/ir/sabou/inventory/ui/AccountingViewModel.kt"
  "app/src/main/java/ir/sabou/inventory/ui/OperationsScreens.kt"
  "app/src/main/java/ir/sabou/inventory/ui/PurchaseManagementDialogs.kt"
  "app/src/main/java/ir/sabou/inventory/ui/PersianDate.kt"
  "app/src/main/java/ir/sabou/inventory/ui/SabouApp.kt"
  "app/src/test/java/ir/sabou/inventory/core/MoneyRialTest.kt"
  "app/src/test/java/ir/sabou/inventory/core/SignedLongMathTest.kt"
  "app/src/test/java/ir/sabou/inventory/domain/accounting/AccountingModelsTest.kt"
  "app/src/test/java/ir/sabou/inventory/domain/purchase/PurchaseManagementTest.kt"
)

for relative in "${required_files[@]}"; do
  test -s "$ROOT/$relative"
done

if rg -n 'android.permission.INTERNET' "$ROOT/app/src/main/AndroidManifest.xml"; then
  echo "مجوز اینترنت نباید در نسخه آفلاین پایه وجود داشته باشد." >&2
  exit 1
fi

rg -q 'android:allowBackup="false"' "$ROOT/app/src/main/AndroidManifest.xml"
rg -q 'android:usesCleartextTraffic="false"' "$ROOT/app/src/main/AndroidManifest.xml"
rg -q 'applicationId = "ir.sabou.inventory"' "$ROOT/app/build.gradle.kts"
rg -q 'minSdk = 23' "$ROOT/app/build.gradle.kts"
rg -q 'SupportOpenHelperFactory' "$ROOT/app/src/main/java/ir/sabou/inventory/data/db/AppDatabase.kt"
rg -q 'database.withTransaction' "$ROOT/app/src/main/java/ir/sabou/inventory/data/repository/LocalPurchaseRepository.kt"
rg -q 'BalancedJournalDraft' "$ROOT/app/src/main/java/ir/sabou/inventory/data/repository/LocalPurchaseRepository.kt"
rg -Fq 'fun observeSearch(query: String)' "$ROOT/app/src/main/java/ir/sabou/inventory/data/db/Daos.kt"
rg -Fq 'fun observeLowStock()' "$ROOT/app/src/main/java/ir/sabou/inventory/data/db/Daos.kt"
rg -Fq 'PersianDatePickerDialog' "$ROOT/app/src/main/java/ir/sabou/inventory/ui/OperationsScreens.kt"
rg -Fq 'PurchasePaymentMethod.TRANSFER' "$ROOT/app/src/main/java/ir/sabou/inventory/ui/OperationsScreens.kt"
rg -Fq 'database.withTransaction' "$ROOT/app/src/main/java/ir/sabou/inventory/data/repository/LocalOperationsRepository.kt"
rg -Fq 'database.withTransaction' "$ROOT/app/src/main/java/ir/sabou/inventory/data/repository/LocalAccountingRepository.kt"
rg -Fq 'fun observeJournals(query: String)' "$ROOT/app/src/main/java/ir/sabou/inventory/data/db/Daos.kt"
rg -Fq 'fun observeJournalDetails(entryId: Long)' "$ROOT/app/src/main/java/ir/sabou/inventory/data/db/Daos.kt"
rg -Fq 'fun observeAccountBalances()' "$ROOT/app/src/main/java/ir/sabou/inventory/data/db/Daos.kt"
rg -Fq 'suspend fun reverseManual' "$ROOT/app/src/main/java/ir/sabou/inventory/domain/accounting/AccountingModels.kt"
rg -Fq 'AppScreen.ACCOUNTING' "$ROOT/app/src/main/java/ir/sabou/inventory/ui/SabouApp.kt"
rg -Fq 'suspend fun settle' "$ROOT/app/src/main/java/ir/sabou/inventory/domain/purchase/PurchaseModels.kt"
rg -Fq 'suspend fun reverse' "$ROOT/app/src/main/java/ir/sabou/inventory/domain/purchase/PurchaseModels.kt"
rg -Fq 'PURCHASE_SETTLEMENT' "$ROOT/app/src/main/java/ir/sabou/inventory/data/repository/LocalPurchaseRepository.kt"
rg -Fq 'PURCHASE_REVERSAL' "$ROOT/app/src/main/java/ir/sabou/inventory/data/repository/LocalPurchaseRepository.kt"
rg -Fq 'expectedPaidRial' "$ROOT/app/src/main/java/ir/sabou/inventory/data/db/Daos.kt"

if rg -n 'DELETE FROM (journal_entries|journal_lines)' "$ROOT/app/src/main"; then
  echo "حذف مستقیم اسناد مالی ممنوع است." >&2
  exit 1
fi

if rg -n '\b(Double|Float)\b' \
  "$ROOT/app/src/main/java/ir/sabou/inventory/core" \
  "$ROOT/app/src/main/java/ir/sabou/inventory/domain"; then
  echo "نوع اعشاری وارد هسته مالی شده است." >&2
  exit 1
fi

if rg -n 'Math\.(addExact|subtractExact|multiplyExact|negateExact)' \
  "$ROOT/app/src/main/java"; then
  echo "توابع عددی ناسازگار با Android API 23 وارد کد شده‌اند." >&2
  exit 1
fi

if rg -n 'fallbackToDestructiveMigration' "$ROOT/app/src/main"; then
  echo "مهاجرت تخریبی خودکار ممنوع است." >&2
  exit 1
fi

echo "Sabou v3 foundation checks passed."
