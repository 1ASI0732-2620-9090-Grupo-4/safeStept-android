package com.safestep.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.safestep.mobile.commerce.CartScreen
import com.safestep.mobile.commerce.OrdersScreen
import com.safestep.mobile.commerce.ProductDetailScreen
import com.safestep.mobile.commerce.StoreScreen
import com.safestep.mobile.gamification.ProgressScreen
import com.safestep.mobile.home.HomeScreen
import com.safestep.mobile.iam.ForgotPasswordScreen
import com.safestep.mobile.iam.LoginScreen
import com.safestep.mobile.iam.RegisterScreen
import com.safestep.mobile.profile.ProfileScreen
import com.safestep.mobile.simulation.SimulationListScreen
import com.safestep.mobile.simulation.SimulationScreen
import com.safestep.mobile.ui.LocalContainer
import com.safestep.mobile.ui.ProvideContainer
import com.safestep.mobile.ui.SafeStepTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as SafeStepApp).container
        setContent {
            SafeStepTheme {
                Surface {
                    ProvideContainer(container) { AppRoot() }
                }
            }
        }
    }
}

private object Route {
    const val Login = "login"
    const val Register = "register"
    const val Forgot = "forgot"
    const val Home = "home"
    const val Simulations = "simulations"
    const val Simulation = "simulation/{id}"
    const val Progress = "progress"
    const val Store = "store"
    const val Product = "product/{id}"
    const val Cart = "cart"
    const val Orders = "orders"
    const val Profile = "profile"
}

private data class Tab(val route: String, val label: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab(Route.Home, R.string.nav_home, Icons.Filled.Home),
    Tab(Route.Simulations, R.string.nav_simulations, Icons.Filled.MedicalServices),
    Tab(Route.Progress, R.string.nav_progress, Icons.Filled.EmojiEvents),
    Tab(Route.Store, R.string.nav_store, Icons.Filled.ShoppingBag),
    Tab(Route.Profile, R.string.nav_profile, Icons.Filled.Person),
)

@Composable
private fun AppRoot() {
    val container = LocalContainer.current
    val session by container.sessions.session.collectAsStateWithLifecycle()
    val current = session
    if (current == null) AuthHost() else MainHost(current.username)
}

@Composable
private fun AuthHost() {
    val nav = rememberNavController()
    NavHost(nav, startDestination = Route.Login) {
        composable(Route.Login) { LoginScreen(onRegister = { nav.navigate(Route.Register) }, onForgot = { nav.navigate(Route.Forgot) }) }
        composable(Route.Register) { RegisterScreen(onLogin = { nav.popBackStack() }) }
        composable(Route.Forgot) { ForgotPasswordScreen(onBack = { nav.popBackStack() }) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainHost(username: String) {
    val container = LocalContainer.current
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val isTopLevel = tabs.any { tab -> destination?.hierarchy?.any { it.route == tab.route } == true } &&
        tabs.any { it.route == destination?.route }
    val title = when (destination?.route) {
        Route.Simulation -> R.string.nav_simulations
        Route.Product -> R.string.nav_store
        Route.Cart -> R.string.cart
        Route.Orders -> R.string.my_orders
        else -> tabs.firstOrNull { it.route == destination?.route }?.label ?: R.string.app_name
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(title)) },
                navigationIcon = {
                    if (!isTopLevel) {
                        IconButton(onClick = { nav.popBackStack() }, modifier = Modifier.testTag("back")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = destination?.hierarchy?.any { it.route == tab.route } == true,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, null) },
                        label = { Text(stringResource(tab.label)) },
                        modifier = Modifier.testTag("tab_${tab.route}"),
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Route.Home, modifier = Modifier.padding(padding)) {
            composable(Route.Home) {
                HomeScreen(
                    username = username,
                    onOpenSimulation = { nav.navigate("simulation/$it") },
                    onSimulations = { nav.navigate(Route.Simulations) },
                    onProgress = { nav.navigate(Route.Progress) },
                )
            }
            composable(Route.Simulations) { SimulationListScreen(onOpen = { nav.navigate("simulation/$it") }) }
            composable(Route.Simulation, arguments = listOf(navArgument("id") { type = NavType.StringType })) { backStack ->
                SimulationScreen(
                    simulationId = backStack.arguments?.getString("id").orEmpty(),
                    onClose = { nav.popBackStack() },
                    onProgress = { nav.navigate(Route.Progress) },
                    onStore = { nav.navigate(Route.Store) },
                )
            }
            composable(Route.Progress) { ProgressScreen(username) }
            composable(Route.Store) {
                StoreScreen(onProduct = { nav.navigate("product/$it") }, onCart = { nav.navigate(Route.Cart) }, onOrders = { nav.navigate(Route.Orders) })
            }
            composable(Route.Product, arguments = listOf(navArgument("id") { type = NavType.StringType })) { backStack ->
                ProductDetailScreen(backStack.arguments?.getString("id").orEmpty(), onCart = { nav.navigate(Route.Cart) })
            }
            composable(Route.Cart) { CartScreen(onShop = { nav.popBackStack(Route.Store, false) }, onOrders = { nav.navigate(Route.Orders) }) }
            composable(Route.Orders) { OrdersScreen() }
            composable(Route.Profile) {
                ProfileScreen(username, onSignOut = { scope.launch { container.auth.signOut() } })
            }
        }
    }
}
