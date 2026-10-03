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
 * 평생권: Google Play 한 번 결제 (소모되지 않는 상품 `lifetime`). 결제 정보는 앱이 받지 않음 (Play 가 처리).
 * 산 기록은 기기에 남겨 두어 Play 에 닿지 않을 때도 열려 있게. Play 에 상품이 없거나 닿지 않으면 ready = false.
 */
class Lifetime(context: Context) {
    companion object { const val ID = "lifetime" }
    private val prefs = context.getSharedPreferences("today", Context.MODE_PRIVATE)

    var owned by mutableStateOf(prefs.getBoolean("lifetime", false))
        private set
    var price by mutableStateOf<String?>(null)
        private set
    val ready: Boolean get() = details != null

    private var details: ProductDetails? = null
    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .setListener { r, list -> if (r.responseCode == BillingClient.BillingResponseCode.OK) list?.forEach(::grant) }
        .build()

    fun connect() {
        if (client.isReady) { query(); return }
        runCatching {
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(r: BillingResult) { if (r.responseCode == BillingClient.BillingResponseCode.OK) query() }
                override fun onBillingServiceDisconnected() {}
            })
        }
    }

    private fun query() {
        val q = QueryProductDetailsParams.newBuilder().setProductList(listOf(
            QueryProductDetailsParams.Product.newBuilder().setProductId(ID).setProductType(BillingClient.ProductType.INAPP).build()
        )).build()
        client.queryProductDetailsAsync(q) { r, result ->
            if (r.responseCode != BillingClient.BillingResponseCode.OK) return@queryProductDetailsAsync
            details = result.productDetailsList.firstOrNull { it.productId == ID }
            price = details?.oneTimePurchaseOfferDetails?.formattedPrice
        }
        restore()
    }

    /** 이미 산 평생권 다시 받기 (다른 폰 · 다시 설치). */
    fun restore() {
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()) { r, list ->
            if (r.responseCode == BillingClient.BillingResponseCode.OK) list.forEach(::grant)
        }
    }

    fun buy(activity: Activity): Boolean {
        val d = details ?: return false
        val p = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build()
        return client.launchBillingFlow(activity, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(p)).build()).responseCode == BillingClient.BillingResponseCode.OK
    }

    private fun grant(p: Purchase) {
        if (ID !in p.products || p.purchaseState != Purchase.PurchaseState.PURCHASED) return
        owned = true; prefs.edit().putBoolean("lifetime", true).apply()
        if (!p.isAcknowledged) client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()) {}
    }

    /** 캡처 · 시험용 (debug 빌드만). */
    fun debugSet(own: Boolean?, readyPrice: String?) {
        own?.let { owned = it }
        readyPrice?.let { price = it; forceReady = true }
    }
    var forceReady = false
        private set

    fun close() = runCatching { client.endConnection() }
}
