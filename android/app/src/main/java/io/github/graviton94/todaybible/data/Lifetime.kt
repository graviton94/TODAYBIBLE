package io.github.graviton94.todaybible.data

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

/**
 * 여는 길 두 가지 (Google Play): 월 구독 (`monthly`) · 평생권 (한 번 결제 `lifetime`).
 * 구독 중인 사람에게는 더 싼 평생권 (`lifetime_member`) 을 보여 줘요. 가격은 모두 Play Console 에서 정하고 앱은 Play 가 준 글을 그대로 써요.
 * 결제 정보는 앱이 받지 않음 (Play 가 처리). 산 기록은 기기에 남겨 두어 Play 에 닿지 않을 때도 열려 있게.
 */
class Lifetime(context: Context) {
    companion object { const val ID = "lifetime"; const val MEMBER = "lifetime_member"; const val MONTHLY = "monthly" }
    private val prefs = context.getSharedPreferences("today", Context.MODE_PRIVATE)

    var owned by mutableStateOf(prefs.getBoolean("lifetime", false))
        private set
    var price by mutableStateOf<String?>(null)
        private set
    /** 월 구독 중 (Play 가 돌려준 지금 살아 있는 구독). */
    var subscribed by mutableStateOf(prefs.getBoolean("subscribed", false))
        private set
    var monthlyPrice by mutableStateOf<String?>(null)
        private set
    var memberPrice by mutableStateOf<String?>(null)
        private set
    /** 결제 · 되찾기 결과 한 줄 (화면이 토스트로 보여 주고 지워요). */
    enum class Note { BOUGHT, SUBSCRIBED, RESTORED, NOTHING, PENDING, FAILED }
    var note by mutableStateOf<Note?>(null)
    private fun note(n: Note) { note = n }
    /** 잠긴 것이 모두 열려 있음 (평생권이거나 구독 중). */
    val unlocked: Boolean get() = owned || subscribed
    val ready: Boolean get() = details != null || monthly != null
    val canSubscribe: Boolean get() = monthly != null
    /** 지금 보여 줄 평생권 가격: 구독 중이면 할인 가격. */
    val lifetimePrice: String? get() = if (subscribed && memberPrice != null) memberPrice else price

    private var details: ProductDetails? = null
    private var member: ProductDetails? = null
    private var monthly: ProductDetails? = null
    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .setListener { r, list ->
            when (r.responseCode) {
                BillingClient.BillingResponseCode.OK -> list?.forEach { p -> if (p.purchaseState == Purchase.PurchaseState.PENDING) note(Note.PENDING) else { grant(p); note(if (MONTHLY in p.products) Note.SUBSCRIBED else Note.BOUGHT) } }
                BillingClient.BillingResponseCode.USER_CANCELED -> {}   // 스스로 닫았으면 말없이
                BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> restore(asked = true)
                else -> note(Note.FAILED)
            }
        }
        .build()

    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private var tries = 0
    private var connecting = false

    fun connect() {
        if (client.isReady) { query(); return }
        if (connecting) return
        connecting = true
        runCatching {
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(r: BillingResult) {
                    connecting = false
                    if (r.responseCode == BillingClient.BillingResponseCode.OK) { tries = 0; query() } else retry()
                }
                // 끊긴 뒤 다음 호출은 자동 재연결이 맡아요. 가격을 아직 못 받았으면 다시 붙어 받아요.
                override fun onBillingServiceDisconnected() { connecting = false; if (!ready) retry() }
            })
        }.onFailure { connecting = false; retry() }
    }

    /** 연결이 안 되면 1 · 2 · 4 · 8 초 뒤 다시 (4번까지). 화면에 다시 들어오면 처음부터. */
    private fun retry() {
        if (tries >= 4) return
        main.postDelayed({ connect() }, 1000L shl tries++)
    }

    /** 앱으로 돌아올 때: 그 사이 해지 · 환불 · 보류 끝난 결제를 반영해요. */
    fun refresh() { if (debugged) return; if (client.isReady) restore() else { tries = 0; connect() } }

    private fun product(id: String, type: String) = QueryProductDetailsParams.Product.newBuilder().setProductId(id).setProductType(type).build()

    private fun query() {
        // 한 번 결제 상품과 구독은 따로 물어야 해요
        client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(listOf(
            product(ID, BillingClient.ProductType.INAPP), product(MEMBER, BillingClient.ProductType.INAPP))).build()) { r, result ->
            if (r.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
            details = result.productDetailsList.firstOrNull { it.productId == ID }
            member = result.productDetailsList.firstOrNull { it.productId == MEMBER }
            price = details?.oneTimePurchaseOfferDetails?.formattedPrice
            memberPrice = member?.oneTimePurchaseOfferDetails?.formattedPrice
        }
        client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(listOf(product(MONTHLY, BillingClient.ProductType.SUBS))).build()) { r, result ->
            if (r.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
            monthly = result.productDetailsList.firstOrNull { it.productId == MONTHLY }
            // 기본 요금 (무료 체험 같은 앞 단계가 있어도 마지막 단계가 매달 내는 값)
            monthlyPrice = monthly?.subscriptionOfferDetails?.firstOrNull()?.pricingPhases?.pricingPhaseList?.lastOrNull()?.formattedPrice
        }
        restore()
    }

    /** 이미 산 평생권 · 살아 있는 구독 다시 받기 (다른 폰 · 다시 설치 · 해지 뒤). */
    /** asked = 사용자가 '구매 복원' 을 눌렀을 때: 결과를 한 줄로 알려요. */
    fun restore(asked: Boolean = false) {
        if (debugged) return
        if (asked && !client.isReady) { note(Note.FAILED); connect(); return }
        val left = java.util.concurrent.atomic.AtomicInteger(2); val found = java.util.concurrent.atomic.AtomicBoolean(false)
        fun done() { if (left.decrementAndGet() == 0 && asked) note(if (found.get()) Note.RESTORED else Note.NOTHING) }
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { r, list ->
            if (r.responseCode == BillingClient.BillingResponseCode.OK) {
                list.forEach { p -> if ((ID in p.products || MEMBER in p.products) && p.purchaseState == Purchase.PurchaseState.PURCHASED) found.set(true); grant(p) }
                // 환불 · 취소된 평생권은 Play 가 돌려주지 않아요: 없으면 다시 잠금 (Play 에 닿았을 때만)
                if (list.none { (ID in it.products || MEMBER in it.products) && it.purchaseState == Purchase.PurchaseState.PURCHASED }) markOwned(false)
            }
            done()
        }
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()) { r, list ->
            if (r.responseCode == BillingClient.BillingResponseCode.OK) {
                // 해지 · 만료된 구독은 Play 가 돌려주지 않아요: 없으면 닫힘
                val live = list.filter { MONTHLY in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED }
                if (live.isNotEmpty()) found.set(true)
                markSubscribed(live.isNotEmpty()); live.forEach(::acknowledge)
            }
            done()
        }
    }

    /** 평생권 사기: 구독 중이면 할인 상품으로. */
    fun buy(activity: Activity): Boolean {
        if (!client.isReady) { tries = 0; connect() }
        val d = (if (subscribed) member else null) ?: details ?: return false
        val p = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build()
        return client.launchBillingFlow(activity, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(p)).build()).responseCode == BillingClient.BillingResponseCode.OK
    }

    /** 월 구독 시작. */
    fun subscribe(activity: Activity): Boolean {
        if (!client.isReady) { tries = 0; connect() }
        val d = monthly ?: return false
        val token = d.subscriptionOfferDetails?.firstOrNull()?.offerToken ?: return false
        val p = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).setOfferToken(token).build()
        return client.launchBillingFlow(activity, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(p)).build()).responseCode == BillingClient.BillingResponseCode.OK
    }

    private fun markOwned(on: Boolean) { owned = on; prefs.edit().putBoolean("lifetime", on).apply() }
    private fun markSubscribed(on: Boolean) { subscribed = on; prefs.edit().putBoolean("subscribed", on).apply() }
    private fun acknowledge(p: Purchase) {
        if (!p.isAcknowledged) client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()) {}
    }

    private fun grant(p: Purchase) {
        if (p.purchaseState != Purchase.PurchaseState.PURCHASED) return
        when {
            ID in p.products || MEMBER in p.products -> markOwned(true)
            MONTHLY in p.products -> markSubscribed(true)
            else -> return
        }
        acknowledge(p)
    }

    /** 캡처 · 시험용 (debug 빌드만). */
    fun debugSet(own: Boolean?, readyPrice: String?, monthPrice: String? = null, memberP: String? = null, sub: Boolean? = null) {
        debugged = own != null || sub != null
        own?.let { owned = it }
        readyPrice?.let { price = it; forceReady = true }
        monthPrice?.let { monthlyPrice = it }; memberP?.let { memberPrice = it }; sub?.let { subscribed = it }
    }
    var forceReady = false
        private set
    /** 캡처가 정한 구매 상태: Play 확인으로 덮지 않아요. */
    private var debugged = false

    fun close() = runCatching { client.endConnection() }
}
