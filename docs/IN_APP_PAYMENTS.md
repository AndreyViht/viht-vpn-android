# Subscription checkout

Android opens subscription tariffs from Home, Profile, and the expired-subscription screen. The renewal button is available at any remaining duration.

`cabinet-api` exposes authenticated `app-tariffs`, `app-checkout` and `app-payment-status` actions. Prices, recipient wallet and payer identity come from the server. Checkout uses the same YooMoney wallet, `pay_<telegram_id>_<days>_<reference>` labels, signed webhook and atomic credit ledger as the existing website. OAuth accounts use their existing internal payer identifier; Telegram sign-in is not required.

A UUID is saved before checkout creation so retries reuse the reference. Pending checkout is scoped to the account and survives restarting the UI. A fresh reference is generated after successful payment. A changed server price requires another explicit tap before opening the payment form.

The provider form runs in a separate, non-exported `PaymentActivity`, without the Viht JavaScript bridge or owner session token. The initial address must be the HTTPS YooMoney checkout endpoint. Provider return navigation closes that activity; it never credits a subscription. Only a completed entry in the existing server ledger triggers success and profile refresh. Bank confirmation may open a banking application.

Verification includes shared-interface tests, browser scenarios for duplicate clicks, interrupted checkout, server-confirmed success, failed requests, repeated renewal and narrow screens, plus the Android CI build/lint/unit tests. No real money transfer is performed by these tests.

This implements the direct APK payment flow. Google Play publication and region-dependent billing policy require a separate release configuration before store distribution. Extra slots also use the isolated checkout: quantity 1–20, server-priced at 300 RUB per slot, existing slot_* webhook labels, account-scoped pending references, and automatic limit refresh after confirmed payment.
