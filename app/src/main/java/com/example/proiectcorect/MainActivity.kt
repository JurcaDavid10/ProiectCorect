package com.example.proiectcorect

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.proiectcorect.ui.theme.ProiectCorectTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat


data class Stock(
    val symbol: String = "",
    val percentageChange: Double = 0.0
)

class MainActivity : ComponentActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Deconectăm utilizatorul la fiecare rulare a aplicației
        auth.signOut()

        // Apelăm funcția pentru încărcarea stocurilor la început
        uploadStocksToFirestore()

        setContent {
            ProiectCorectTheme {
                var currentScreen by remember { mutableStateOf("LoginRegisterChoice") }

                if (auth.currentUser != null) {
                    currentScreen = "MainScreen"
                }

                when (currentScreen) {
                    "LoginRegisterChoice" -> {
                        LoginRegisterChoiceScreen(
                            onNavigateToLogin = { currentScreen = "LoginScreen" },
                            onNavigateToRegister = { currentScreen = "RegisterScreen" }
                        )
                    }
                    "LoginScreen" -> {
                        LoginScreen(
                            auth = auth,
                            onLoginSuccess = { currentScreen = "MainScreen" }
                        )
                    }
                    "RegisterScreen" -> {
                        RegisterScreen(
                            auth = auth,
                            onRegisterSuccess = { currentScreen = "LoginRegisterChoice" }
                        )
                    }
                    "MainScreen" -> {
                        MainScreen(
                            name = auth.currentUser?.email ?: "User",
                            onUploadStocks = {} // Încărcarea stocurilor e automată acum
                        )
                    }
                }
            }
        }
    }

    fun uploadStocksToFirestore() {
        val stocks = listOf(
            Stock("AAPL", 2.3),
            Stock("GOOGL", -1.5),
            Stock("MSFT", 0.8),
            Stock("AMZN", 3.2),
            Stock("MSFT", 2.0),
            Stock("TSLA", 3.5),
            Stock("META", -2.5),
            Stock("NFLX", -3.0),
            Stock("NVDA", -2.1),
            Stock("SPY", -2.3),
            Stock("INTC", -2.0),
            Stock("ORCL", 4.0),
            Stock("BABA", 5.3),
            Stock("ADBE", 1.8),
            Stock("PYPL", -1.5),
            Stock("CRM", 3.7),
            Stock("SHOP", 2.4),
            Stock("SQ", -2.8),
            Stock("ZM", -2.0),
            Stock("TWTR", 2.1),
            Stock("UBER", -3.5)
        )

        for (stock in stocks) {
            firestore.collection("stocks").document(stock.symbol)  // Folosim simbolul ca Document ID
                .set(mapOf(
                    "symbol" to stock.symbol,
                    "percentageChange" to stock.percentageChange
                ))
                .addOnSuccessListener { Log.d("MainActivity", "Stock added: ${stock.symbol}") }
                .addOnFailureListener { e -> Log.e("MainActivity", "Error adding stock: ${e.message}") }
        }
    }
}

@Composable
fun LoginRegisterChoiceScreen(onNavigateToLogin: () -> Unit, onNavigateToRegister: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Button(onClick = { onNavigateToLogin() }) {
            Text("Login")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { onNavigateToRegister() }) {
            Text("Register")
        }
    }
}

// Funcția actualizată pentru trimiterea notificărilor
// Funcția actualizată pentru trimiterea notificărilor
fun sendNotification(context: Context, increasedStocks: List<Stock>, decreasedStocks: List<Stock>) {
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val channelId = "stock_notifications"

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            channelId, "Stock Notifications", NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationManager.createNotificationChannel(channel)
    }

    // Notificare pentru stocurile care au crescut
    if (increasedStocks.isNotEmpty()) {
        val increasedMessage = "The following stocks have increased by 2% or more:\n" +
                increasedStocks.joinToString("\n") { "${it.symbol} +${it.percentageChange}%" } // Adăugăm și procentul
        val increasedNotification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Stocks Increased")
            .setContentText(increasedMessage)
            .setStyle(NotificationCompat.BigTextStyle().bigText(increasedMessage))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .build()

        notificationManager.notify("increased_stocks".hashCode(), increasedNotification)
    }

    // Notificare pentru stocurile care au scăzut
    if (decreasedStocks.isNotEmpty()) {
        val decreasedMessage = "The following stocks have decreased by 2% or more:\n" +
                decreasedStocks.joinToString("\n") { "${it.symbol} ${it.percentageChange}%" } // Adăugăm și procentul
        val decreasedNotification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Stocks Decreased")
            .setContentText(decreasedMessage)
            .setStyle(NotificationCompat.BigTextStyle().bigText(decreasedMessage))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .build()

        notificationManager.notify("decreased_stocks".hashCode(), decreasedNotification)
    }
}



// Funcția de verificare a permisiunilor și trimiterea notificărilor
fun checkPermissionAndSendNotifications(context: Context, increasedStocks: List<Stock>, decreasedStocks: List<Stock>) {
    if (ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED) {
        sendNotification(context, increasedStocks, decreasedStocks)
    } else {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                context as? MainActivity ?: return,
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                1
            )
        }
    }
}


// Secțiunea modificată în MainScreen
@Composable
fun MainScreen(name: String, onUploadStocks: () -> Unit) {
    var stocks by remember { mutableStateOf<List<Stock>>(emptyList()) }
    val context = LocalContext.current

    // Încărcăm stocurile din Firestore
    LaunchedEffect(Unit) {
        val db = FirebaseFirestore.getInstance()
        db.collection("stocks").get().addOnSuccessListener { result ->
            val loadedStocks = result.documents.map { document ->
                Stock(
                    symbol = document.getString("symbol") ?: "",
                    percentageChange = document.getDouble("percentageChange") ?: 0.0
                )
            }
            stocks = loadedStocks
        }
    }

    // Filtrăm stocurile care au o schimbare de 2% sau mai mult, pe creștere și scădere
    val increasedStocks = stocks.filter { it.percentageChange >= 2.0 }
    val decreasedStocks = stocks.filter { it.percentageChange <= -2.0 }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Welcome, $name!")
        Spacer(modifier = Modifier.height(16.dp))

        // Buton pentru trimiterea notificărilor
        Button(
            onClick = {
                checkPermissionAndSendNotifications(context, increasedStocks, decreasedStocks)
            }
        ) {
            Text("Send Notifications")
        }
    }
}




@Composable
fun LoginScreen(auth: FirebaseAuth, onLoginSuccess: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        TextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            onLoginSuccess()
                        } else {
                            errorMessage = "Authentication failed: ${task.exception?.message}"
                        }
                    }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Login")
        }

        if (errorMessage.isNotEmpty()) {
            Text(text = errorMessage, color = Color.Red)
        }
    }
}

@Composable
fun RegisterScreen(auth: FirebaseAuth, onRegisterSuccess: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        TextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text("Confirm Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (password == confirmPassword) {
                    auth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                auth.signOut()
                                onRegisterSuccess()
                            } else {
                                errorMessage = "Registration failed: ${task.exception?.message}"
                            }
                        }
                } else {
                    errorMessage = "Passwords do not match!"
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Register")
        }

        if (errorMessage.isNotEmpty()) {
            Text(text = errorMessage, color = Color.Red)
        }
    }
}