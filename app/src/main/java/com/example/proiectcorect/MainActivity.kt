package com.example.proiectcorect

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject


import android.app.Activity
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.widget.Toast
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS
import android.os.Build
import android.provider.Settings

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessaging
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import java.util.Calendar

data class Stock(
    val symbol: String = "",
    val closingPrice: Double = 0.0,
    val percentageChange: Double = 0.0
)



class MainActivity : ComponentActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var lightSensor: Sensor? = null

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    private var lightLevel = 0f // Variabilă pentru a stoca valoarea lux
    private var screenBrightness = 0 // Variabilă pentru a stoca luminozitatea ecranului

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inițializare senzori și Firestore
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // 🔵 2️⃣ Aici obții token-ul FCM
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                Log.d("FCM", "Token-ul FCM este: $token")

                // 🔵 Salvăm token-ul sub documentul utilizatorului curent (în colecția "users")
                val userId = FirebaseAuth.getInstance().currentUser?.uid
                if (userId != null) {
                    val db = FirebaseFirestore.getInstance()
                    val userDocRef = db.collection("users").document(userId)
                    userDocRef.update("fcmToken", token)
                        .addOnSuccessListener { Log.d("FCM", "Token salvat cu succes!") }
                        .addOnFailureListener { e -> Log.e("FCM", "Eroare la salvare: ${e.message}") }
                }
            } else {
                Log.w("FCM", "Eroare la obținerea token-ului", task.exception)
            }
        }

        auth.signOut()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(this)) {
            val intent = Intent(ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName"))
            startActivityForResult(intent, 200)
        } else {
            val initialScreen = if (auth.currentUser != null) "MainScreen" else "LoginRegisterChoice"
            uploadStocksToFirestore() {
                setContent {
                    var currentScreen by remember { mutableStateOf(initialScreen) }
                    ProiectCorectTheme {
                        when (currentScreen) {
                            "LoginRegisterChoice" -> {
                                // Dezactivează senzorul de lumină
                                sensorManager.unregisterListener(this)

                                LoginRegisterChoiceScreen(
                                    onNavigateToLogin = {
                                        currentScreen = "LoginScreen"
                                        sensorManager.registerListener(
                                            this,
                                            lightSensor,
                                            SensorManager.SENSOR_DELAY_NORMAL
                                        )
                                    },
                                    onNavigateToRegister = {
                                        currentScreen = "RegisterScreen"
                                        sensorManager.registerListener(
                                            this,
                                            lightSensor,
                                            SensorManager.SENSOR_DELAY_NORMAL
                                        )
                                    }
                                )
                            }

                            "LoginScreen" -> {
                                LoginScreen(
                                    auth = auth,
                                    onLoginSuccess = { currentScreen = "MainScreen" },
                                    onNavigateToRegister = { currentScreen = "RegisterScreen" }
                                )
                            }

                            "RegisterScreen" -> {
                                RegisterScreen(
                                    auth = auth,
                                    onRegisterSuccess = { currentScreen = "LoginRegisterChoice" },
                                    onNavigateToLogin = { currentScreen = "LoginScreen" }
                                )
                            }

                            "MainScreen" -> {
                                listenForStockUpdates()
                                MainScreen(
                                    name = auth.currentUser?.email ?: "User",
                                    lightLevel = lightLevel,
                                    screenBrightness = screenBrightness,
                                    onUploadStocks = {},
                                    onNavigateToSecondScreen = { currentScreen = "SecondScreen" }
                                )
                            }
                            "SecondScreen" -> {
                                SecondScreen(
                                    onBack = { currentScreen = "MainScreen" },
                                    onNavigateToPredictionHistory = { currentScreen = "PredictionHistory" },
                                    onNavigateToNewScreen = { currentScreen = "NewScreen" }
                                )
                            }

                            "PredictionHistory" -> {
                                PredictionHistoryScreen(
                                    onBack = { currentScreen = "SecondScreen" }
                                )
                            }
                            "NewScreen" -> {
                                NewScreen(
                                    onBack = { currentScreen = "SecondScreen" },
                                    onNavigateToPredictionComparison = { currentScreen = "PredictionComparison" },
                                    onNavigateToLeaderboard = { currentScreen = "Leaderboard" }

                                )
                            }
                            "PredictionComparison" -> {
                                PredictionComparisonScreen(onBack = { currentScreen = "NewScreen" })
                            }
                            "Leaderboard" -> {
                                LeaderboardScreen(onBack = { currentScreen = "NewScreen" })
                            }



                        }
                    }
                }
            }
        }


    }


    override fun onResume() {
        super.onResume()
        // Înregistrăm listener-ul pentru senzorul de lumină
        lightSensor?.also { light ->
            sensorManager.registerListener(this, light, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        // Oprirea listener-ului pentru senzorul de lumină
        sensorManager.unregisterListener(this)
    }

    // Implementarea metodei onSensorChanged pentru a detecta nivelul de lumină
    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null && event.sensor.type == Sensor.TYPE_LIGHT) {
            lightLevel = event.values[0]  // Valoarea luminii ambientale (lux)

            // Ajustăm luminozitatea ecranului în funcție de nivelul luminii ambientale
            adjustScreenBrightness(lightLevel)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Nu este necesar să gestionăm schimbările de acuratețe
    }

    // Ajustăm luminozitatea ecranului în funcție de nivelul luminii ambientale
    private fun adjustScreenBrightness(lightLevel: Float) {
        val brightness = when {
            lightLevel < 10 -> 255  // Lumină scăzută -> luminositate mare
            lightLevel > 100 -> 50  // Lumină puternică -> luminositate scăzută
            else -> (255 * (1 - (lightLevel / 100))).toInt()  // Calculăm o valoare intermediară
        }

        // Aplicăm modificările la setările de luminiscență
        val layoutParams = window.attributes
        layoutParams.screenBrightness =
            brightness / 255f  // Transformăm valoarea într-un interval între 0 și 1
        window.attributes = layoutParams

        // Calculăm valoarea procentuală și o afișăm
        screenBrightness = (brightness / 255f * 100).toInt()  // Luminozitatea în procente
    }

    fun listenForStockUpdates() {
        val db = FirebaseFirestore.getInstance()
        db.collection("stocks").addSnapshotListener { snapshot, _ ->
            if (snapshot != null && !snapshot.isEmpty) {
                val stocks = snapshot.documents.map { document ->
                    Stock(
                        symbol = document.getString("symbol") ?: "",
                        percentageChange = document.getDouble("percentageChange") ?: 0.0
                    )
                }

                // Încarcă pragurile și trimite notificările
                db.collection("stocksthreshold").get().addOnSuccessListener { thresholdResult ->
                    val thresholds = thresholdResult.documents.associate {
                        it.getString("symbol")!! to (it.getDouble("threshold") ?: 0.0)
                    }

                    val increasedStocks = stocks.filter { stock ->
                        val threshold = thresholds[stock.symbol] ?: 0.0
                        stock.percentageChange >= threshold && stock.percentageChange > 0
                    }
                    val decreasedStocks = stocks.filter { stock ->
                        val threshold = thresholds[stock.symbol] ?: 0.0
                        stock.percentageChange <= threshold && stock.percentageChange < 0
                    }

                    checkPermissionAndSendNotificationsCustomized(
                        context = this@MainActivity,
                        increasedStocks = increasedStocks,
                        decreasedStocks = decreasedStocks
                    )
                }
            }
        }
    }

    fun uploadStocksToFirestore(onComplete: () -> Unit) {
        val stockSymbols = listOf("AAPL","GOOGL","TWTR","TSLA","UBER")
        val stockRepository = StockRepository()

        stockRepository.fetchStockData(
            "d0u47upr01qgk5llqj6gd0u47upr01qgk5llqj70",
            stockSymbols
        ) { stocks ->
            Log.d("MainActivity", "Stockuri primite: ${stocks.size}")
            stocks.forEach {
                Log.d(
                    "MainActivity",
                    "Stock: ${it.symbol}, Closing: ${it.closingPrice}, Change: ${it.percentageChange}"
                )
            }

            val stocksCollection = firestore.collection("stocks")
            val stockThresholdCollection = firestore.collection("stocksthreshold")

            var completedTasks = 0
            val totalTasks = 2

            fun checkIfDone() {
                completedTasks++
                if (completedTasks == totalTasks) {
                    Log.d("MainActivity", "Upload complet pentru ambele colecții!")
                    onComplete()
                }
            }

            // 🔵 Upload la stocks
            stocksCollection.get().addOnSuccessListener { querySnapshot ->
                val batch = firestore.batch()
                for (document in querySnapshot.documents) {
                    batch.delete(document.reference)
                }
                batch.commit().addOnSuccessListener {
                    Log.d("MainActivity", "Toate documentele din 'stocks' au fost șterse.")

                    val total = stocks.size
                    var completed = 0
                    for (stock in stocks) {
                        stocksCollection.document(stock.symbol)
                            .set(
                                mapOf(
                                    "symbol" to stock.symbol,
                                    "closingPrice" to stock.closingPrice,
                                    "percentageChange" to stock.percentageChange
                                )
                            )
                            .addOnSuccessListener {
                                Log.d("MainActivity", "Stock added: ${stock.symbol}")
                                completed++
                                if (completed == total) {
                                    Log.d(
                                        "MainActivity",
                                        "Toate stocurile au fost adăugate în 'stocks'"
                                    )
                                    checkIfDone()
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.e("MainActivity", "Error adding stock: ${e.message}")
                            }
                    }
                }
            }

            // 🔵 Upload la stocksthreshold (cu aceleași simboluri corecte)
            stockThresholdCollection.get().addOnSuccessListener { querySnapshot ->
                val batch = firestore.batch()
                for (document in querySnapshot.documents) {
                    batch.delete(document.reference)
                }
                batch.commit().addOnSuccessListener {
                    Log.d("MainActivity", "Toate documentele din 'stocksthreshold' au fost șterse.")

                    val total = stocks.size
                    var completed = 0
                    for (stock in stocks) {
                        stockThresholdCollection.document(stock.symbol)
                            .set(
                                mapOf(
                                    "symbol" to stock.symbol,
                                    "threshold" to 0.0
                                )
                            )
                            .addOnSuccessListener {
                                Log.d("MainActivity", "Threshold added for stock: ${stock.symbol}")
                                completed++
                                if (completed == total) {
                                    Log.d(
                                        "MainActivity",
                                        "Toate pragurile au fost adăugate în 'stocksthreshold'"
                                    )
                                    checkIfDone()
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.e("MainActivity", "Error adding threshold: ${e.message}")
                            }
                    }
                }
            }
        }
    }
}


@Composable
fun LoginRegisterChoiceScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToRegister: () -> Unit
) {
    val background = painterResource(id = R.drawable.stock_background_good)

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Background image
        Image(
            painter = background,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize()
        )

        // ✅ Place title inside Box so align works
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Text(
                text = "Stocks Prices & Predictions",
                color = Color(0xFFFFD700), // Gold color
                fontSize = 36.sp,
                lineHeight = 44.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.TopCenter) // ✅ Now valid
                    .padding(top = 170.dp)       // 🔼 Adjust vertical position here
            )
        }

        // Buttons Column
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 350.dp, start = 32.dp, end = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = onNavigateToLogin,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC407A))
            ) {
                Text("Login", color = Color.White)
            }

            Button(
                onClick = onNavigateToRegister,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFAB47BC))
            ) {
                Text("Register", color = Color.White)
            }
        }
    }
}




fun fetchPrediction(onResult: (String) -> Unit) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val url = URL("https://us-central1-proiectcorect-b09a1.cloudfunctions.net/get_stock_prediction")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonResponse = JSONObject(response)
                val predictedPrice = jsonResponse.getDouble("predicted_price")
                val message = jsonResponse.getString("message")

                // 🔵 Data și ora completă (ex: 06-06-2025 14:26:53)
                val timestamp = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()).format(Date())

                // 🔵 Obținem userId-ul utilizatorului curent
                val userId = FirebaseAuth.getInstance().currentUser?.uid
                if (userId != null) {
                    val db = FirebaseFirestore.getInstance()
                    val predictionsRef = db.collection("users").document(userId).collection("predictions")
                    val predictionData = hashMapOf(
                        "timestamp" to timestamp,
                        "predictedPrice" to predictedPrice
                    )

                    // 🔵 Salvăm fiecare predicție sub documentul identificat de timestamp
                    predictionsRef.document(timestamp).set(predictionData)
                        .addOnSuccessListener {
                            Log.d("Prediction", "Predicția a fost salvată cu timestamp!")
                        }
                        .addOnFailureListener { e ->
                            Log.e("Prediction", "Eroare la salvare: ${e.message}")
                        }
                } else {
                    Log.e("Prediction", "Eroare: utilizatorul nu este autentificat!")
                }

                withContext(Dispatchers.Main) {
                    onResult(message)
                }
            } else {
                withContext(Dispatchers.Main) {
                    onResult("Eroare la obținerea predicției!")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                onResult("Eroare: ${e.message}")
            }
        }
    }
}







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

@Composable
fun LoginScreen(auth: FirebaseAuth, onLoginSuccess: () -> Unit,onNavigateToRegister: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

    val background = painterResource(id = R.drawable.loginpage)

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // 🔹 Background Image
        Image(
            painter = background,
            contentDescription = "Login Background",
            contentScale = ContentScale.Crop, // or .FillBounds if needed
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = 0.4f))
        )

        // 🔹 Foreground Content
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
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Nu ai cont? Înregistrează-te",
                    fontSize = 23.sp,
                    color = Color(0xFFEC407A),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onNavigateToRegister() }
                )
            }



            if (errorMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = errorMessage, color = Color.Red)
            }
        }
    }
}


@Composable
fun RegisterScreen(
    auth: FirebaseAuth,
    onRegisterSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize()) {
        // 🖼️ Fundalul
        Image(
            painter = painterResource(id = R.drawable.registerpage),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize()
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = 0.4f))
        )
        // 🧱 Conținutul
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

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Ai deja cont? Loghează-te",
                    fontSize = 23.sp,
                    color = Color(0xFFEC407A),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onNavigateToLogin() }
                )
            }

            if (errorMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = errorMessage, color = Color.Red)
            }
        }
    }
}


@Composable
fun MainScreen(name: String, lightLevel: Float, screenBrightness: Int, onUploadStocks: () -> Unit,onNavigateToSecondScreen: () -> Unit) {
    var stocks by remember { mutableStateOf<List<Stock>>(emptyList()) }
    var thresholdSettingsOpen by remember { mutableStateOf(false) }
    var thresholds by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        val today = sdf.format(Date())

        updateLeaderboardForDate(today) { success ->
            if (success) {
                Toast.makeText(context, "Clasamentul a fost actualizat!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Eroare la actualizarea clasamentului.", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }
    // Gestionăm luminozitatea și nivelul de lumină
    val currentLightLevel = remember { mutableStateOf(lightLevel) }
    val currentScreenBrightness = remember { mutableStateOf(screenBrightness / 100f) }
    val modeText = remember { mutableStateOf("Bright Mode") }

    // Obțineți managerul de senzori
    val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

    val lightSensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            event?.let {
                val light = it.values[0]
                currentLightLevel.value = light
                val brightness = (light / 500f).coerceIn(0.1f, 1f)
                currentScreenBrightness.value = 1f - brightness
                modeText.value = if (brightness < 0.5f) "Bright Mode" else "Dark Mode"
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    DisposableEffect(context) {
        lightSensor?.let {
            sensorManager.registerListener(
                lightSensorListener,
                it,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }
        onDispose {
            sensorManager.unregisterListener(lightSensorListener)
        }
    }

    val window = (context as Activity).window
    val layoutParams = window.attributes
    layoutParams.screenBrightness = currentScreenBrightness.value
    window.attributes = layoutParams

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

    if (thresholdSettingsOpen) {
        StockThresholdSettingsScreen(
            stocks = stocks,
            context = context,
            onSaveThresholds = { newThresholds -> // Callback pentru salvarea pragurilor
                thresholds = newThresholds
                saveThresholdsToFirestore(newThresholds) // Opțional: Salvează în Firestore
                thresholdSettingsOpen = false // Închide ecranul de setări
            }
        )
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            // 1. Imaginea de fundal
            Image(
                painter = painterResource(id = R.drawable.mainscreen), // asigură-te că ai mainscreen.jpg în res/drawable
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )

            // 2. Overlay negru semi-transparent
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.4f))
            )

            // 3. Conținutul principal
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Button(onClick = onNavigateToSecondScreen) {
                    Text("Mergi la Second Screen")
                }

                Text(text = "Welcome, $name!", color = Color.White)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Light Level: %.2f lux".format(currentLightLevel.value),
                    color = Color.White
                )
                Text(
                    text = "Screen Brightness: %.3f%%".format(currentScreenBrightness.value * 100),
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = modeText.value, color = Color.White)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { thresholdSettingsOpen = true }) {
                    Text("Set Stock Thresholds")
                }
                Spacer(modifier = Modifier.height(16.dp))

                Button(onClick = {
                    val increasedStocks = stocks.filter { it.percentageChange >= 2.0 }
                    val decreasedStocks = stocks.filter { it.percentageChange <= -2.0 }

                    sendNotification(
                        context = context,
                        increasedStocks = increasedStocks,
                        decreasedStocks = decreasedStocks
                    )
                }) {
                    Text("Send Notifications for 2% Threshold")
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(onClick = {
                    val filteredIncreasedStocks = stocks.filter { stock ->
                        val threshold = thresholds[stock.symbol] ?: 0.0
                        stock.percentageChange >= threshold && stock.percentageChange > 0
                    }

                    val filteredDecreasedStocks = stocks.filter { stock ->
                        val threshold = thresholds[stock.symbol] ?: 0.0
                        stock.percentageChange <= threshold && stock.percentageChange < 0
                    }

                    checkPermissionAndSendNotificationsCustomized(
                        context = context,
                        increasedStocks = filteredIncreasedStocks,
                        decreasedStocks = filteredDecreasedStocks
                    )
                }) {
                    Text("Send Notifications")
                }
            }
        }
    }
}




@Composable
fun SecondScreen(
    onBack: () -> Unit,
    onNavigateToPredictionHistory: () -> Unit,
    onNavigateToNewScreen: () -> Unit
) {
    val context = LocalContext.current
    var predictionResult by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Background image
        Image(
            painter = painterResource(id = R.drawable.secondscreen),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Foreground UI content
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().padding(16.dp)
        ) {
            Text("Acesta este ecranul secundar!", color = Color.White)
            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = {
                fetchPrediction { result ->
                    predictionResult = result
                }
            }) {
                Text("Predictie S&P 500")
            }

            Spacer(modifier = Modifier.height(16.dp))

            predictionResult?.let {
                Text(
                    text = "$it",
                    color = Color.White,
                    modifier = Modifier.padding(start = 24.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = onNavigateToPredictionHistory) {
                Text("Istoric Predictii")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = onNavigateToNewScreen) {
                Text("Mergi la ecranul nou")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = onBack) {
                Text("Înapoi")
            }
        }
    }
}




@Composable
fun PredictionHistoryScreen(onBack: () -> Unit) {
    var predictions by remember { mutableStateOf<List<Pair<String, Double>>>(emptyList()) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            val db = FirebaseFirestore.getInstance()
            db.collection("users").document(userId).collection("predictions")
                .get()
                .addOnSuccessListener { result ->
                    val loadedPredictions = result.documents.mapNotNull { document ->
                        val timestamp = document.getString("timestamp") ?: document.id
                        val price = document.getDouble("predictedPrice") ?: return@mapNotNull null
                        timestamp to price
                    }.sortedByDescending { it.first }
                    predictions = loadedPredictions
                }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Background image
        Image(
            painter = painterResource(id = R.drawable.historicalscreen),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Foreground content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Istoric Predictii",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White // Better contrast
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn {
                items(predictions) { (timestamp, price) ->
                    Text(
                        "Data și ora: $timestamp → Preț: ${"%.2f".format(price)} USD",
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = onBack) {
                Text("Înapoi")
            }
        }
    }
}



@Composable
fun NewScreen(
    onBack: () -> Unit,
    onNavigateToPredictionComparison: () -> Unit,
    onNavigateToLeaderboard: () -> Unit
) {
    val context = LocalContext.current
    val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
    val tomorrowDateStr = sdf.format(tomorrow.time)

    var userPrediction by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Background image
        Image(
            painter = painterResource(id = R.drawable.predictionscreen),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Foreground UI
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Preziceți prețul pentru data $tomorrowDateStr",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(16.dp))

            TextField(
                value = userPrediction,
                onValueChange = { userPrediction = it },
                label = { Text("Introduceți prețul prezis") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = {
                val currentUser = FirebaseAuth.getInstance().currentUser
                val userId = currentUser?.uid
                val email = currentUser?.email ?: "unknown"
                val username = email.substringBefore("@")

                if (userId != null && userPrediction.toDoubleOrNull() != null) {
                    val predictionValue = userPrediction.toDouble()
                    val db = FirebaseFirestore.getInstance()

                    val predictionData = mapOf(
                        "predictedPrice" to predictionValue,
                        "username" to username,
                        "timestamp" to System.currentTimeMillis()
                    )

                    db.collection("users")
                        .document(userId)
                        .collection("user_predictions")
                        .document(tomorrowDateStr)
                        .set(predictionData)
                        .addOnSuccessListener {
                            Toast.makeText(context, "Predicția a fost salvată!", Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener {
                            Toast.makeText(context, "Eroare la salvarea predicției.", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    message = "Introduceți un număr valid."
                }
            }) {
                Text("Salvează predicția")
            }

            Spacer(modifier = Modifier.height(16.dp))

            message?.let {
                Text(it, color = Color.White)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(onClick = onNavigateToPredictionComparison) {
                Text("Vezi toate diferențele între predicții și prețul real")
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(onClick = { onNavigateToLeaderboard() }) {
                Text("Vezi Clasamentul")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = { onBack() }) {
                Text("Înapoi la Second Screen")
            }
        }
    }
}



@Composable
fun PredictionComparisonScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    var comparisons by remember { mutableStateOf<List<Triple<String, Double, Double>>>(emptyList()) }

    LaunchedEffect(Unit) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
        val db = FirebaseFirestore.getInstance()

        db.collection("users").document(userId).collection("user_predictions")
            .get().addOnSuccessListener { predictionsSnapshot ->
                val fetchedComparisons = mutableListOf<Triple<String, Double, Double>>()

                for (predictionDoc in predictionsSnapshot.documents) {
                    val date = predictionDoc.id
                    val predictedPrice = predictionDoc.getDouble("predictedPrice") ?: continue

                    db.collection("stocks").document("S&P500").get().addOnSuccessListener { stockDoc ->
                        val actualPrice = stockDoc.getDouble("closingPrice") ?: return@addOnSuccessListener
                        fetchedComparisons.add(Triple(date, predictedPrice, actualPrice))
                        comparisons = fetchedComparisons.sortedBy { it.first }
                    }
                }
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Background image
        Image(
            painter = painterResource(id = R.drawable.comparisonscreen),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Foreground content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Comparație Predicții vs Preț Real",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn {
                items(comparisons) { (date, predicted, actual) ->
                    val diff = actual - predicted
                    Text(
                        "Data: $date | Prezis: $predicted | Real: $actual | Dif: ${"%.2f".format(diff)} USD",
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Spacer(modifier = Modifier.height(530.dp))

            Button(onClick = onBack) {
                Text("Înapoi")
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 60.dp), // distance from the bottom
                contentAlignment = Alignment.BottomCenter
            ) {
                Text(
                    text = "\"I've never met a dollar I didn't like\"",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                        lineHeight = 36.sp
                    ),
                    textAlign = TextAlign.Center
                )
            }


        }
    }
}



fun updateLeaderboardForDate(targetDate: String, onComplete: (Boolean) -> Unit) {
    val db = FirebaseFirestore.getInstance()

    db.collection("users").get().addOnSuccessListener { usersSnapshot ->
        val stockRef = db.collection("stocks").document("S&P500")
        stockRef.get().addOnSuccessListener { stockDoc ->
            val actualPrice = stockDoc.getDouble("closingPrice") ?: return@addOnSuccessListener

            for (userDoc in usersSnapshot.documents) {
                val userId = userDoc.id
                val predictionsRef = db.collection("users").document(userId)
                    .collection("user_predictions").document(targetDate)

                predictionsRef.get().addOnSuccessListener { predictionDoc ->
                    if (predictionDoc.exists()) {
                        val predicted = predictionDoc.getDouble("predictedPrice") ?: return@addOnSuccessListener
                        val username = predictionDoc.getString("username") ?: "anonim"
                        val score = kotlin.math.abs(actualPrice - predicted)

                        val leaderboardEntry = mapOf(
                            "username" to username,
                            "score" to score
                        )

                        db.collection("leaderboard")
                            .document(targetDate)
                            .collection("entries")
                            .document(userId)
                            .set(leaderboardEntry)
                    }
                }
            }
            onComplete(true)
        }.addOnFailureListener {
            onComplete(false)
        }
    }.addOnFailureListener {
        onComplete(false)
    }
}


@Composable
fun LeaderboardScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    val today = sdf.format(Date())
    var leaderboard by remember { mutableStateOf<List<Pair<String, Double>>>(emptyList()) }

    LaunchedEffect(Unit) {
        val db = FirebaseFirestore.getInstance()
        db.collection("leaderboard")
            .document(today)
            .collection("entries")
            .get()
            .addOnSuccessListener { snapshot ->
                val results = snapshot.documents.mapNotNull { doc ->
                    val username = doc.getString("username")
                    val score = doc.getDouble("score")
                    if (username != null && score != null) {
                        username to score
                    } else null
                }.sortedBy { it.second } // scor mai mic = predicție mai precisă
                leaderboard = results
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Background image
        Image(
            painter = painterResource(id = R.drawable.leaderboardscreen),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Foreground content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Clasament Predictii - $today",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn {
                itemsIndexed(leaderboard) { index, (username, score) ->
                    Text(
                        text = "${index + 1}. $username — Diferență: ${"%.2f".format(score)} USD",
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(onClick = onBack) {
                Text("Înapoi")
            }
        }
    }
}




@Composable
fun StockThresholdSettingsScreen(
    stocks: List<Stock>,
    context: Context,
    onSaveThresholds: (Map<String, Double>) -> Unit
) {
    var stockThresholds by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Image(
            painter = painterResource(id = R.drawable.thresholdpage),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Set Thresholds for Stocks",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White // Optional: for better contrast
            )

            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                items(stocks) { stock ->
                    var sliderValue by remember { mutableStateOf(stockThresholds[stock.symbol] ?: 0.0) }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "${stock.symbol}: ${"%.2f".format(sliderValue)}%",
                            color = Color.White // Optional
                        )
                        Slider(
                            value = sliderValue.toFloat(),
                            onValueChange = { sliderValue = it.toDouble() },
                            valueRange = -100f..100f,
                            steps = 200,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    stockThresholds = stockThresholds.toMutableMap().apply {
                        put(stock.symbol, sliderValue)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = {
                onSaveThresholds(stockThresholds)
            }) {
                Text("Salvează Praguri")
            }

            Button(
                onClick = {
                    val increasedStocks = stocks.filter { stock ->
                        val threshold = stockThresholds[stock.symbol] ?: 0.0
                        stock.percentageChange >= threshold && stock.percentageChange > 0
                    }

                    val decreasedStocks = stocks.filter { stock ->
                        val threshold = stockThresholds[stock.symbol] ?: 0.0
                        stock.percentageChange <= threshold && stock.percentageChange < 0
                    }

                    checkPermissionAndSendNotificationsCustomized(
                        context = context,
                        increasedStocks = increasedStocks,
                        decreasedStocks = decreasedStocks
                    )
                },
                modifier = Modifier.padding(16.dp)
            ) {
                Text("Trimite notificări pentru pragurile setate")
            }
        }
    }
}





fun saveThresholdsToFirestore(thresholds: Map<String, Double>) {
    val db = FirebaseFirestore.getInstance()

    // 🔵 Actualizăm colecția „stockthreshold” (sau „stock_thresholds” – alege una!)
    val thresholdsCollection = db.collection("stocksthreshold")

    thresholds.forEach { (symbol, threshold) ->
        thresholdsCollection.document(symbol).set(
            mapOf(
                "symbol" to symbol,
                "threshold" to threshold
            )
        )
    }
}


fun sendNotificationsCustomized(
    context: Context,
    increasedStocks: List<Stock>,  // Modificat pentru a include și procentul
    decreasedStocks: List<Stock>   // Modificat pentru a include și procentul
) {
    // Verificare permisiune pentru notificări (Android 13+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("NotificationError", "Permisiunea pentru notificări nu este acordată!")
            return // Ieșim din funcție dacă permisiunea nu este acordată
        }
    }

    val notificationManager =
        context.getSystemService(NotificationManager::class.java) as NotificationManager

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            "channel_02", "Channel 2", NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationManager.createNotificationChannel(channel)
    }

    // Notificare pentru stocurile care au crescut
    if (increasedStocks.isNotEmpty()) {
        val stockUpdatesIncreased = increasedStocks.joinToString(separator = "\n") {
            "${it.symbol} +${it.percentageChange}%"  // Afișăm și procentul
        }
        val notificationIncreased = NotificationCompat.Builder(context, "channel_02")
            .setContentTitle("Stockuri care au crescut cu o valoare mai mare decât pragul minim")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Stockurile care au crescut:\n$stockUpdatesIncreased")
            )
            .setSmallIcon(R.drawable.ic_notification)

        NotificationManagerCompat.from(context).notify(3, notificationIncreased.build())
    }

    // Notificare pentru stocurile care au scăzut
    if (decreasedStocks.isNotEmpty()) {
        val stockUpdatesDecreased = decreasedStocks.joinToString(separator = "\n") {
            "${it.symbol} ${it.percentageChange}%"  // Afișăm și procentul
        }
        val notificationDecreased = NotificationCompat.Builder(context, "channel_02")
            .setContentTitle("Stockuri care au scăzut cu o valoare mai mare decât pragul minim")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Stockurile care au scăzut:\n$stockUpdatesDecreased")
            )
            .setSmallIcon(R.drawable.ic_notification)

        NotificationManagerCompat.from(context).notify(4, notificationDecreased.build())
    }
}




fun checkPermissionAndSendNotificationsCustomized(
    context: Context,
    increasedStocks: List<Stock>,   // Modificat pentru a accepta lista de obiecte Stock
    decreasedStocks: List<Stock>    // Modificat pentru a accepta lista de obiecte Stock
) {
    if (ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED) {
        sendNotificationsCustomized(context, increasedStocks, decreasedStocks)
    } else {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                context as? MainActivity ?: return,
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                1
            )
        } else {
            sendNotificationsCustomized(context, increasedStocks, decreasedStocks)
        }
    }
}