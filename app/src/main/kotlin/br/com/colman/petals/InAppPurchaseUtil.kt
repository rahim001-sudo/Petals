package br.com.colman.petals

import android.app.Activity
import android.content.Context
import br.com.colman.petals.settings.SettingsRepository
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingFlowParams.ProductDetailsParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject

class InAppPurchaseUtil(val context: Context) : PurchasesUpdatedListener {
  private lateinit var myBilled: BillingClient
  private val productId = if (BuildConfig.DEBUG) "android.test.purchased" else "petals_remove_ads"
  private val settingsRepository: SettingsRepository by inject(SettingsRepository::class.java)
  private var lstProductDetails: List<ProductDetails>? = null

  fun init() {
    myBilled = BillingClient.newBuilder(context)
      .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
      .setListener(this)
      .build()
    myBilled.startConnection(object : BillingClientStateListener {
      override fun onBillingSetupFinished(billingResult: BillingResult) {
        myBilled.queryProductDetailsAsync(
          QueryProductDetailsParams.newBuilder().setProductList(
            listOf(
              QueryProductDetailsParams.Product.newBuilder().setProductId(productId)
                .setProductType(ProductType.INAPP).build()
            )
          ).build()
        ) { billingResult, productDetails ->
          lstProductDetails = productDetails
        }
      }

      override fun onBillingServiceDisconnected() {}
    })
  }

  fun purchase(activity: Activity) {
    lstProductDetails?.let {
      if (it.isNotEmpty()) {
        myBilled.launchBillingFlow(
          activity, BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
              listOf(
                ProductDetailsParams.newBuilder().setProductDetails(
                  it[0]
                ).build()
              )
            ).build()
        )
      }
    }
  }

  override fun onPurchasesUpdated(p0: BillingResult, purchase: MutableList<Purchase>?) {
    if (purchase.isNullOrEmpty()) {
      settingsRepository.setAdFree(false)
    }
    purchase?.forEach {
      if (!it.isAcknowledged) {
        CoroutineScope(Dispatchers.IO).launch {
          myBilled.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder().setPurchaseToken(
              it.purchaseToken
            ).build()
          ) {

          }
        }
      }
      if (it.products[0] == productId && it.purchaseState == Purchase.PurchaseState.PURCHASED) {
        settingsRepository.setAdFree(true)
      }
    }
  }

}


