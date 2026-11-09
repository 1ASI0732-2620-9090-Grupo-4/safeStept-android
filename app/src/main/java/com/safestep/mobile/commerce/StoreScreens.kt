package com.safestep.mobile.commerce

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.safestep.mobile.R
import com.safestep.mobile.core.network.CouponDto
import com.safestep.mobile.core.network.KitDto
import com.safestep.mobile.core.network.ProductDto
import com.safestep.mobile.core.network.RecommendationDto
import com.safestep.mobile.ui.EmptyState
import com.safestep.mobile.ui.LoadContent
import com.safestep.mobile.ui.LocalContainer
import com.safestep.mobile.ui.RemoteImage
import com.safestep.mobile.ui.SectionTitle
import com.safestep.mobile.ui.StatCard
import com.safestep.mobile.ui.formatPrice

@Composable
fun StoreScreen(onProduct: (String) -> Unit, onCart: () -> Unit, onOrders: () -> Unit) {
    val container = LocalContainer.current
    val model: StoreViewModel = viewModel(factory = viewModelFactory { initializer { StoreViewModel(container.commerce) } })
    val state by model.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { model.refreshCartCount() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCart, modifier = Modifier.weight(1f).testTag("open_cart")) {
                Icon(Icons.Filled.ShoppingCart, null, Modifier.size(18.dp))
                Text(stringResource(R.string.cart_with_count, state.cartCount), modifier = Modifier.padding(start = 6.dp))
            }
            OutlinedButton(onClick = onOrders, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.my_orders)) }
        }
        PrimaryTabRow(selectedTabIndex = tab) {
            listOf(R.string.tab_products, R.string.tab_kits, R.string.tab_coupons).forEachIndexed { index, label ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(stringResource(label)) }, modifier = Modifier.testTag("store_tab_$index"))
            }
        }
        when (tab) {
            0 -> LoadContent(state.load, onRetry = model::load) { data ->
                ProductsTab(
                    products = state.visibleProducts,
                    categories = data.categories.map { it.name },
                    recommendations = data.recommendations,
                    allProducts = data.products,
                    query = state.query,
                    selectedCategory = state.category,
                    onQuery = model::search,
                    onCategory = model::filterByCategory,
                    onProduct = onProduct,
                )
            }
            1 -> LoadContent(state.load, onRetry = model::load) { data -> KitsTab(data.kits) }
            else -> CouponsTab()
        }
    }
}

@Composable
private fun ProductsTab(
    products: List<ProductDto>,
    categories: List<String>,
    recommendations: List<RecommendationDto>,
    allProducts: List<ProductDto>,
    query: String,
    selectedCategory: String?,
    onQuery: (String) -> Unit,
    onCategory: (String?) -> Unit,
    onProduct: (String) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.testTag("product_list")) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                label = { Text(stringResource(R.string.search_products)) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("product_search"),
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(selected = selectedCategory == null, onClick = { onCategory(null) }, label = { Text(stringResource(R.string.filter_all)) }) }
                items(categories) { name ->
                    FilterChip(selected = selectedCategory == name, onClick = { onCategory(if (selectedCategory == name) null else name) }, label = { Text(name) })
                }
            }
        }
        val suggested = recommendations.mapNotNull { r -> allProducts.firstOrNull { it.id == r.productId }?.let { it to r.reason } }.take(3)
        if (suggested.isNotEmpty() && query.isBlank() && selectedCategory == null) {
            item { SectionTitle(stringResource(R.string.recommended_for_you)) }
            items(suggested, key = { "rec-${it.first.id}" }) { (product, reason) -> ProductCard(product, onProduct, reason) }
        }
        item { SectionTitle(stringResource(R.string.all_products)) }
        if (products.isEmpty()) item { EmptyState(stringResource(R.string.no_products_found)) }
        items(products, key = { it.id }) { ProductCard(it, onProduct) }
    }
}

@Composable
private fun ProductCard(product: ProductDto, onProduct: (String) -> Unit, reason: String? = null) {
    Card(
        onClick = { onProduct(product.id) },
        modifier = Modifier.fillMaxWidth().testTag("product_${product.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RemoteImage(product.imageUrl, Modifier.size(80.dp), product.name)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(product.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                Text(product.category, style = MaterialTheme.typography.bodySmall)
                if (reason != null) Text(reason, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(formatPrice(product.price), fontWeight = FontWeight.Bold)
                    product.oldPrice?.takeIf { it > product.price }?.let {
                        Text(formatPrice(it), style = MaterialTheme.typography.bodySmall, textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)
                    }
                }
            }
        }
    }
}

@Composable
private fun KitsTab(kits: List<KitDto>) {
    if (kits.isEmpty()) return EmptyState(stringResource(R.string.no_kits))
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(kits, key = { it.externalId }) { kit ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    RemoteImage(kit.imageUrl, Modifier.fillMaxWidth().height(120.dp), kit.name)
                    Text(kit.name + if (kit.popular) " ★" else "", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                    Text(kit.description, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(formatPrice(kit.kitPrice), fontWeight = FontWeight.Bold)
                        Text(
                            formatPrice(kit.individualPrice),
                            style = MaterialTheme.typography.bodySmall,
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough,
                        )
                        AssistChip(onClick = {}, label = { Text(stringResource(R.string.kit_savings, kit.savingsPercentage)) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CouponsTab() {
    val container = LocalContainer.current
    val model: CouponsViewModel = viewModel(factory = viewModelFactory { initializer { CouponsViewModel(container.commerce, container.gamification) } })
    val state by model.state.collectAsStateWithLifecycle()
    LoadContent(state.load, onRetry = model::load) { data ->
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("coupon_list")) {
            item { StatCard(stringResource(R.string.safecoins), data.balance.toString(), Modifier.fillMaxWidth().testTag("coupon_balance")) }
            state.message?.let { item { Text(stringResource(R.string.coupon_redeemed, it), color = MaterialTheme.colorScheme.secondary, modifier = Modifier.testTag("coupon_redeemed")) } }
            state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            item { SectionTitle(stringResource(R.string.available_coupons)) }
            items(data.offers, key = { it.id }) { coupon -> CouponOffer(coupon, data.balance, state.redeemingId == coupon.id) { model.redeem(coupon) } }
            item { SectionTitle(stringResource(R.string.my_coupons)) }
            if (data.mine.isEmpty()) item { EmptyState(stringResource(R.string.no_coupons_yet)) }
            items(data.mine, key = { it.id }) { mine ->
                val used = mine.status.equals("USED", true)
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (used) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(mine.title, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(if (used) R.string.coupon_used else R.string.coupon_available), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun CouponOffer(coupon: CouponDto, balance: Int, redeeming: Boolean, onRedeem: () -> Unit) {
    val affordable = balance >= coupon.costCoins
    Card(Modifier.fillMaxWidth().testTag("offer_${coupon.id}")) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(coupon.title, fontWeight = FontWeight.SemiBold)
                coupon.minPurchaseAmount?.let { Text(stringResource(R.string.min_purchase, formatPrice(it)), style = MaterialTheme.typography.bodySmall) }
                Text("${coupon.costCoins} SafeCoins", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            Button(onClick = onRedeem, enabled = affordable && !redeeming, modifier = Modifier.testTag("redeem_${coupon.id}")) {
                Text(stringResource(if (affordable) R.string.redeem else R.string.not_enough_coins))
            }
        }
    }
}

@Composable
fun ProductDetailScreen(productId: String, onCart: () -> Unit) {
    val container = LocalContainer.current
    val model: ProductDetailViewModel = viewModel(
        key = productId,
        factory = viewModelFactory { initializer { ProductDetailViewModel(productId, container.commerce) } },
    )
    val state by model.state.collectAsStateWithLifecycle()
    LoadContent(state.load, onRetry = model::load) { product ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            RemoteImage(product.imageUrl, Modifier.fillMaxWidth().height(200.dp), product.name)
            Text(product.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("${product.category} · ★ ${product.rating}", style = MaterialTheme.typography.bodyMedium)
            Text(formatPrice(product.price), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(product.description)
            if (product.tags.isNotEmpty()) Text(product.tags.joinToString(" · "), style = MaterialTheme.typography.labelMedium)
            Text(
                if (product.stock > 0) stringResource(R.string.in_stock, product.stock) else stringResource(R.string.out_of_stock),
                color = if (product.stock > 0) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
            )
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (state.added) {
                Text(stringResource(R.string.added_to_cart), color = MaterialTheme.colorScheme.secondary, modifier = Modifier.testTag("added_to_cart"))
                OutlinedButton(onClick = onCart, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.go_to_cart)) }
            }
            Button(
                onClick = model::addToCart,
                enabled = product.stock > 0 && !state.adding,
                modifier = Modifier.fillMaxWidth().testTag("add_to_cart"),
            ) { Text(stringResource(R.string.add_to_cart)) }
        }
    }
}

@Composable
fun CartScreen(onShop: () -> Unit, onOrders: () -> Unit) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val model: CartViewModel = viewModel(factory = viewModelFactory { initializer { CartViewModel(container.commerce) } })
    val state by model.state.collectAsStateWithLifecycle()
    // Coming back from the browser after paying: ask the server what happened.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (model.state.value.pending != null) model.verifyPayment() }
    LaunchedEffect(state.openUrl) {
        state.openUrl?.let { url ->
            CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
            model.urlOpened()
        }
    }
    val paid = state.paidOrder
    if (paid != null) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.payment_success), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("payment_success"))
            Text(stringResource(R.string.order_total_paid, formatPrice(paid.finalTotal ?: paid.total)))
            Button(onClick = { model.dismissPaid(); onOrders() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.my_orders)) }
            OutlinedButton(onClick = { model.dismissPaid(); onShop() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.keep_shopping)) }
        }
        return
    }
    val pending = state.pending
    if (pending != null) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.payment_pending), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("payment_pending"))
            Text(stringResource(R.string.payment_pending_hint), style = MaterialTheme.typography.bodyMedium)
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = { model.verifyPayment() }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("verify_payment")) { Text(stringResource(R.string.verify_payment)) }
            OutlinedButton(onClick = { CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(pending.checkoutUrl)) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.reopen_checkout)) }
            OutlinedButton(onClick = model::cancelPayment, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.cancel_payment)) }
        }
        return
    }
    LoadContent(state.load, onRetry = model::load) { data ->
        if (data.lines.isEmpty()) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                EmptyState(stringResource(R.string.cart_empty))
                Button(onClick = onShop) { Text(stringResource(R.string.keep_shopping)) }
            }
            return@LoadContent
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("cart_list")) {
            items(data.lines, key = { it.item.id }) { line ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(line.product?.name ?: line.item.productId, fontWeight = FontWeight.SemiBold)
                            Text(formatPrice(line.subtotal), style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { model.changeQuantity(line, line.item.quantity - 1) }) { Icon(Icons.Filled.Remove, stringResource(R.string.decrease)) }
                        Text(line.item.quantity.toString(), modifier = Modifier.testTag("quantity_${line.item.productId}"))
                        IconButton(onClick = { model.changeQuantity(line, line.item.quantity + 1) }) { Icon(Icons.Filled.Add, stringResource(R.string.increase)) }
                        IconButton(onClick = { model.changeQuantity(line, 0) }) { Icon(Icons.Filled.Delete, stringResource(R.string.remove)) }
                    }
                }
            }
            item {
                SectionTitle(stringResource(R.string.apply_coupon))
                if (data.applicableCoupons.isEmpty()) Text(stringResource(R.string.no_applicable_coupons), style = MaterialTheme.typography.bodySmall)
            }
            items(data.applicableCoupons, key = { it.id }) { coupon ->
                Row(
                    Modifier.fillMaxWidth().clickable { model.selectCoupon(if (state.selectedCouponId == coupon.id) null else coupon.id) }.testTag("apply_${coupon.couponId}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = state.selectedCouponId == coupon.id, onClick = { model.selectCoupon(if (state.selectedCouponId == coupon.id) null else coupon.id) })
                    Text(coupon.title)
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.subtotal, formatPrice(data.total)))
                    Text(stringResource(R.string.total_to_pay, formatPrice(data.totalWith(state.selectedCouponId))), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("cart_total"))
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(onClick = model::checkout, enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("checkout")) { Text(stringResource(R.string.pay_with_stripe)) }
                    Text(stringResource(R.string.stripe_notice), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun OrdersScreen() {
    val container = LocalContainer.current
    val model: OrdersViewModel = viewModel(factory = viewModelFactory { initializer { OrdersViewModel(container.commerce) } })
    val state by model.state.collectAsStateWithLifecycle()
    LoadContent(state, onRetry = model::load) { orders ->
        if (orders.isEmpty()) return@LoadContent EmptyState(stringResource(R.string.no_orders))
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(orders, key = { it.id }) { order ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${order.createdAt.orEmpty()} · ${order.status}", fontWeight = FontWeight.SemiBold)
                        order.items.forEach { Text("${it.quantity} × ${it.productName}", style = MaterialTheme.typography.bodySmall) }
                        order.appliedDiscountPercentage?.takeIf { it > 0 }?.let { Text(stringResource(R.string.discount_applied, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
                        Text(formatPrice(order.finalTotal ?: order.total), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
