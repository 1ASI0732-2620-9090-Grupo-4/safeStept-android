package com.safestep.mobile.commerce

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.safestep.mobile.core.CommerceRepository
import com.safestep.mobile.core.GamificationRepository
import com.safestep.mobile.core.network.CartItemDto
import com.safestep.mobile.core.network.CategoryDto
import com.safestep.mobile.core.network.CouponDto
import com.safestep.mobile.core.network.KitDto
import com.safestep.mobile.core.network.OrderDto
import com.safestep.mobile.core.network.ProductDto
import com.safestep.mobile.core.network.RecommendationDto
import com.safestep.mobile.core.network.RedeemedCouponDto
import com.safestep.mobile.ui.Load
import com.safestep.mobile.ui.toLoad
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---------------------------------------------------------------- catalogue

data class CatalogueData(
    val products: List<ProductDto>,
    val categories: List<CategoryDto>,
    val kits: List<KitDto>,
    val recommendations: List<RecommendationDto>,
)

data class StoreState(
    val load: Load<CatalogueData> = Load.Loading,
    val query: String = "",
    val category: String? = null,
    val cartCount: Int = 0,
) {
    val visibleProducts: List<ProductDto>
        get() = (load as? Load.Ready)?.data?.products.orEmpty().filter { product ->
            (category == null || product.category == category) &&
                (query.isBlank() || product.name.contains(query.trim(), ignoreCase = true))
        }
}

class StoreViewModel(private val commerce: CommerceRepository) : ViewModel() {
    private val mutable = MutableStateFlow(StoreState())
    val state: StateFlow<StoreState> = mutable.asStateFlow()

    init {
        load()
    }

    fun load() {
        mutable.update { it.copy(load = Load.Loading) }
        viewModelScope.launch {
            coroutineScope {
                val products = async { commerce.products() }
                val categories = async { commerce.categories() }
                val kits = async { commerce.kits() }
                val recommendations = async { commerce.recommendations() }
                val cart = async { commerce.cart() }
                val result = products.await().map {
                    CatalogueData(
                        it,
                        categories.await().getOrDefault(emptyList()).filter { c -> c.productCount > 0 },
                        kits.await().getOrDefault(emptyList()),
                        recommendations.await().getOrDefault(emptyList()),
                    )
                }
                mutable.update { it.copy(load = result.toLoad(), cartCount = cart.await().getOrDefault(emptyList()).sumOf { item -> item.quantity }) }
            }
        }
    }

    fun refreshCartCount() {
        viewModelScope.launch {
            commerce.cart().onSuccess { items -> mutable.update { it.copy(cartCount = items.sumOf { item -> item.quantity }) } }
        }
    }

    fun search(query: String) = mutable.update { it.copy(query = query) }

    fun filterByCategory(category: String?) = mutable.update { it.copy(category = category) }
}

// ---------------------------------------------------------------- product detail

data class ProductDetailState(
    val load: Load<ProductDto> = Load.Loading,
    val adding: Boolean = false,
    val added: Boolean = false,
    val error: String? = null,
)

class ProductDetailViewModel(private val productId: String, private val commerce: CommerceRepository) : ViewModel() {
    private val mutable = MutableStateFlow(ProductDetailState())
    val state: StateFlow<ProductDetailState> = mutable.asStateFlow()

    init {
        load()
    }

    fun load() {
        mutable.update { it.copy(load = Load.Loading) }
        viewModelScope.launch { commerce.product(productId).let { result -> mutable.update { it.copy(load = result.toLoad()) } } }
    }

    fun addToCart() {
        viewModelScope.launch {
            mutable.update { it.copy(adding = true, added = false, error = null) }
            commerce.addToCart(productId).fold(
                onSuccess = { mutable.update { state -> state.copy(adding = false, added = true) } },
                onFailure = { error -> mutable.update { it.copy(adding = false, error = error.message) } },
            )
        }
    }
}

// ---------------------------------------------------------------- coupons

data class CouponsData(
    val offers: List<CouponDto>,
    val mine: List<RedeemedCouponDto>,
    val balance: Int,
)

data class CouponsState(
    val load: Load<CouponsData> = Load.Loading,
    val redeemingId: String? = null,
    val message: String? = null,
    val error: String? = null,
)

class CouponsViewModel(private val commerce: CommerceRepository, private val gamification: GamificationRepository) : ViewModel() {
    private val mutable = MutableStateFlow(CouponsState())
    val state: StateFlow<CouponsState> = mutable.asStateFlow()

    init {
        load()
    }

    fun load() {
        mutable.update { it.copy(load = Load.Loading) }
        viewModelScope.launch { mutable.update { it.copy(load = fetch().toLoad()) } }
    }

    private suspend fun fetch(): Result<CouponsData> = coroutineScope {
        val offers = async { commerce.coupons() }
        val mine = async { commerce.redeemedCoupons() }
        val summary = async { gamification.summary() }
        offers.await().mapCatching { offered ->
            CouponsData(offered, mine.await().getOrDefault(emptyList()), summary.await().getOrThrow().safeCoins)
        }
    }

    fun redeem(coupon: CouponDto) {
        val data = (mutable.value.load as? Load.Ready)?.data ?: return
        if (data.balance < coupon.costCoins || mutable.value.redeemingId != null) return
        viewModelScope.launch {
            mutable.update { it.copy(redeemingId = coupon.id, message = null, error = null) }
            commerce.redeemCoupon(coupon.id).fold(
                onSuccess = {
                    val refreshed = fetch()
                    mutable.update { state ->
                        state.copy(
                            redeemingId = null,
                            message = coupon.title,
                            load = refreshed.fold({ Load.Ready(it) }, { state.load }),
                        )
                    }
                },
                onFailure = { error -> mutable.update { it.copy(redeemingId = null, error = error.message) } },
            )
        }
    }
}

// ---------------------------------------------------------------- cart and checkout

data class CartLine(val item: CartItemDto, val product: ProductDto?) {
    val subtotal: Double get() = (product?.price ?: 0.0) * item.quantity
}

data class CartData(val lines: List<CartLine>, val coupons: List<RedeemedCouponDto>) {
    val total: Double get() = lines.sumOf { it.subtotal }

    /** Coupons that can be applied to this cart: available and with the minimum purchase met. */
    val applicableCoupons: List<RedeemedCouponDto>
        get() = coupons.filter { it.status.equals("AVAILABLE", true) && total >= (it.minPurchaseAmount ?: 0.0) }

    fun totalWith(couponId: String?): Double {
        val coupon = applicableCoupons.firstOrNull { it.id == couponId } ?: return total
        return total * (100 - coupon.discountPercentage) / 100.0
    }
}

/** The payment started with Stripe: the player pays in the browser and comes back to verify it. */
data class PendingPayment(val order: OrderDto, val sessionId: String, val checkoutUrl: String)

data class CartState(
    val load: Load<CartData> = Load.Loading,
    val selectedCouponId: String? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val pending: PendingPayment? = null,
    val paidOrder: OrderDto? = null,
    /** One-shot: the checkout address the screen must open in the browser. */
    val openUrl: String? = null,
)

class CartViewModel(private val commerce: CommerceRepository) : ViewModel() {
    private val mutable = MutableStateFlow(CartState())
    val state: StateFlow<CartState> = mutable.asStateFlow()

    init {
        load()
    }

    fun load() {
        mutable.update { it.copy(load = Load.Loading) }
        viewModelScope.launch { mutable.update { it.copy(load = fetch().toLoad()) } }
    }

    private suspend fun fetch(): Result<CartData> = coroutineScope {
        val cart = async { commerce.cart() }
        val products = async { commerce.products() }
        val coupons = async { commerce.redeemedCoupons() }
        cart.await().mapCatching { items ->
            val byId = products.await().getOrThrow().associateBy { it.id }
            CartData(items.map { CartLine(it, byId[it.productId]) }, coupons.await().getOrDefault(emptyList()))
        }
    }

    fun selectCoupon(id: String?) = mutable.update { it.copy(selectedCouponId = id) }

    fun changeQuantity(line: CartLine, quantity: Int) {
        viewModelScope.launch {
            val result = if (quantity <= 0) commerce.removeFromCart(line.item.id) else commerce.setQuantity(line.item.id, quantity).map { }
            result.onFailure { error -> mutable.update { it.copy(error = error.message) } }
            mutable.update { it.copy(load = fetch().toLoad()) }
        }
    }

    /** Creates the order with the chosen coupon and opens the Stripe checkout for it. */
    fun checkout() {
        val data = (mutable.value.load as? Load.Ready)?.data ?: return
        if (data.lines.isEmpty() || mutable.value.busy) return
        val couponId = mutable.value.selectedCouponId?.takeIf { id -> data.applicableCoupons.any { it.id == id } }
        viewModelScope.launch {
            mutable.update { it.copy(busy = true, error = null) }
            val order = commerce.createOrder(couponId).getOrElse { return@launch fail(it) }
            val session = commerce.startStripeCheckout(order.id).getOrElse { return@launch fail(it) }
            mutable.update {
                it.copy(busy = false, pending = PendingPayment(order, session.sessionId, session.sessionUrl), openUrl = session.sessionUrl)
            }
        }
    }

    fun urlOpened() = mutable.update { it.copy(openUrl = null) }

    /** Asks the server whether the payment went through; called when the player returns to the app. */
    fun verifyPayment() {
        val pending = mutable.value.pending ?: return
        if (mutable.value.busy) return
        viewModelScope.launch {
            mutable.update { it.copy(busy = true, error = null) }
            commerce.confirmStripePayment(pending.order.id, pending.sessionId).fold(
                onSuccess = { order ->
                    if (order.status.equals("PAID", true) || order.paymentStatus.equals("PAID", true)) {
                        mutable.update { it.copy(busy = false, pending = null, paidOrder = order) }
                        load()
                    } else {
                        mutable.update { it.copy(busy = false) }
                    }
                },
                onFailure = { error -> mutable.update { it.copy(busy = false, error = error.message) } },
            )
        }
    }

    fun cancelPayment() {
        val pending = mutable.value.pending ?: return
        viewModelScope.launch {
            commerce.cancelStripePayment(pending.order.id, pending.sessionId)
            mutable.update { it.copy(pending = null, selectedCouponId = null) }
            load()
        }
    }

    fun dismissPaid() = mutable.update { it.copy(paidOrder = null) }

    private fun fail(error: Throwable) {
        mutable.update { it.copy(busy = false, error = error.message) }
    }
}

// ---------------------------------------------------------------- orders

class OrdersViewModel(private val commerce: CommerceRepository) : ViewModel() {
    private val mutable = MutableStateFlow<Load<List<OrderDto>>>(Load.Loading)
    val state: StateFlow<Load<List<OrderDto>>> = mutable.asStateFlow()

    init {
        load()
    }

    fun load() {
        mutable.value = Load.Loading
        viewModelScope.launch { mutable.value = commerce.orders().map { list -> list.sortedByDescending { it.createdAt.orEmpty() } }.toLoad() }
    }
}
