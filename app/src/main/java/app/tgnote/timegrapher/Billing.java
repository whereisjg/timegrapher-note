package app.tgnote.timegrapher;

import android.content.SharedPreferences;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;
import java.util.Collections;
import java.util.List;
import org.json.JSONObject;

/**
 * One-time "pro" unlock through Google Play Billing (Play Billing Library 8).
 * Product id in Play Console: pro_unlock (one-time product).
 * The pro flag is cached so the app works offline, and re-checked against Play on every resume.
 */
public class Billing implements PurchasesUpdatedListener {
    static final String PRODUCT_ID = "pro_unlock";

    private final MainActivity act;
    private final WebView web;
    private final SharedPreferences prefs;
    private final BillingClient client;
    private volatile ProductDetails details;

    Billing(MainActivity act, WebView web) {
        this.act = act;
        this.web = web;
        this.prefs = act.getSharedPreferences("billing", 0);
        this.client = BillingClient.newBuilder(act)
                .setListener(this)
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .enableAutoServiceReconnection()
                .build();
        client.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult r) {
                if (r.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    queryDetails();
                    queryOwned();
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                // auto reconnection is enabled
            }
        });
    }

    // ---- called from the page ----

    @JavascriptInterface
    public boolean isPro() {
        return prefs.getBoolean("pro", false);
    }

    @JavascriptInterface
    public String price() {
        return prefs.getString("price", "");
    }

    @JavascriptInterface
    public void refresh() {
        if (client.isReady()) {
            queryOwned();
            if (details == null) queryDetails();
        }
    }

    @JavascriptInterface
    public void buy() {
        act.runOnUiThread(() -> {
            if (!client.isReady() || details == null) {
                push(null, "구글 플레이 결제에 연결하지 못했습니다. 인터넷 연결을 확인하고 잠시 후 다시 눌러 주세요.", true);
                if (client.isReady()) queryDetails();
                return;
            }
            BillingFlowParams.ProductDetailsParams pdp = BillingFlowParams.ProductDetailsParams.newBuilder()
                    .setProductDetails(details)
                    .build();
            BillingFlowParams params = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(Collections.singletonList(pdp))
                    .build();
            BillingResult r = client.launchBillingFlow(act, params);
            if (r.getResponseCode() == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) queryOwned();
            else if (r.getResponseCode() != BillingClient.BillingResponseCode.OK)
                push(null, "결제 창을 열지 못했습니다. (" + r.getResponseCode() + ")", true);
        });
    }

    void close() {
        client.endConnection();
    }

    // ---- Play callbacks ----

    @Override
    public void onPurchasesUpdated(BillingResult r, List<Purchase> purchases) {
        int code = r.getResponseCode();
        if (code == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (Purchase p : purchases) handle(p, true);
        } else if (code == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            queryOwned();
        } else if (code != BillingClient.BillingResponseCode.USER_CANCELED) {
            push(null, "결제가 완료되지 않았습니다. (" + code + ")", true);
        }
    }

    private void queryDetails() {
        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(Collections.singletonList(
                        QueryProductDetailsParams.Product.newBuilder()
                                .setProductId(PRODUCT_ID)
                                .setProductType(BillingClient.ProductType.INAPP)
                                .build()))
                .build();
        client.queryProductDetailsAsync(params, (r, result) -> {
            if (r.getResponseCode() != BillingClient.BillingResponseCode.OK) return;
            List<ProductDetails> list = result.getProductDetailsList();
            if (list == null || list.isEmpty()) return;
            details = list.get(0);
            ProductDetails.OneTimePurchaseOfferDetails offer = details.getOneTimePurchaseOfferDetails();
            if (offer != null) prefs.edit().putString("price", offer.getFormattedPrice()).apply();
            push(null, null, false);
        });
    }

    private void queryOwned() {
        QueryPurchasesParams params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build();
        client.queryPurchasesAsync(params, (r, purchases) -> {
            if (r.getResponseCode() != BillingClient.BillingResponseCode.OK) return; // offline: keep the cached flag
            boolean owned = false;
            for (Purchase p : purchases) {
                if (p.getProducts().contains(PRODUCT_ID) && p.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                    owned = true;
                    acknowledge(p);
                }
            }
            setPro(owned, null);
        });
    }

    private void handle(Purchase p, boolean fresh) {
        if (!p.getProducts().contains(PRODUCT_ID)) return;
        if (p.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
            acknowledge(p);
            setPro(true, fresh ? "프로가 활성화되었습니다. 고맙습니다!" : null);
        } else if (p.getPurchaseState() == Purchase.PurchaseState.PENDING) {
            push(null, "결제가 승인 대기 중입니다. 완료되면 프로가 자동으로 켜집니다.", false);
        }
    }

    private void acknowledge(Purchase p) {
        if (p.isAcknowledged()) return;
        AcknowledgePurchaseParams ap = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(p.getPurchaseToken())
                .build();
        client.acknowledgePurchase(ap, r -> { });
    }

    private void setPro(boolean v, String msg) {
        prefs.edit().putBoolean("pro", v).apply();
        push(v, msg, false);
    }

    private void push(Boolean pro, String msg, boolean err) {
        try {
            JSONObject o = new JSONObject();
            o.put("pro", pro == null ? JSONObject.NULL : pro);
            o.put("price", prefs.getString("price", ""));
            if (msg != null) o.put("msg", msg);
            o.put("err", err);
            final String js = "window.__billing&&window.__billing(" + o.toString() + ")";
            web.post(() -> web.evaluateJavascript(js, null));
        } catch (Exception ignored) {
        }
    }
}
