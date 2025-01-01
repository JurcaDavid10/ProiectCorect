package com.example.proiectcorect

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



data class Stock(
    val symbol: String = "",
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

        // Inițializăm senzorii și Firestore
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Deconectăm utilizatorul la fiecare rulare a aplicației
        auth.signOut()

        // Solicităm permisiunea pentru a modifica setările sistemului (luminozitatea ecranului)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(this)) {
            val intent = Intent(ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName"))
            startActivityForResult(intent, 200)
        } else {
            // Permisiunea deja acordată sau pentru Android 6.0 sau mai târziu
            uploadStocksToFirestore()
        }

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
                        // Transmiterea valorilor către MainScreen
                        MainScreen(
                            name = auth.currentUser?.email ?: "User",
                            lightLevel = lightLevel,  // Transmiterea valorii lux
                            screenBrightness = screenBrightness,  // Transmiterea luminozității ecranului
                            onUploadStocks = {} // Încărcarea stocurilor e automată acum
                        )
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
        layoutParams.screenBrightness = brightness / 255f  // Transformăm valoarea într-un interval între 0 și 1
        window.attributes = layoutParams

        // Calculăm valoarea procentuală și o afișăm
        screenBrightness = (brightness / 255f * 100).toInt()  // Luminozitatea în procente
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
            .background(Color(0xFFE1BEE7))
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


@Composable
fun MainScreen(name: String, lightLevel: Float, screenBrightness: Int, onUploadStocks: () -> Unit) {
    var stocks by remember { mutableStateOf<List<Stock>>(emptyList()) }
    val context = LocalContext.current

    // Gestionăm luminozitatea și nivelul de lumină
    val currentLightLevel = remember { mutableStateOf(lightLevel) }
    val currentScreenBrightness = remember { mutableStateOf(screenBrightness / 100f) } // Conversie la procent

    // Variabila care va ține textul pentru mod
    val modeText = remember { mutableStateOf("Bright Mode") }

    // Obțineți managerul de senzori
    val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

    // Logica pentru actualizarea valorilor senzorului
    val lightSensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            event?.let {
                val light = it.values[0] // Valoarea luminozității
                currentLightLevel.value = light // Actualizăm starea nivelului de lumină

                // Calculăm luminozitatea pe baza luminii ambientale
                val brightness = (light / 500f).coerceIn(0.1f, 1f) // Valoare între 0.1 și 1
                currentScreenBrightness.value = 1f - brightness // Inversăm pentru moduri

                // Schimbăm textul în funcție de nivelul de lumină
                if (brightness < 0.5f) {
                    modeText.value = "Bright Mode"
                } else {
                    modeText.value = "Dark Mode"
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            // Nu este nevoie de implementare aici
        }
    }

    // Înregistrarea și dezactivarea listener-ului
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

    // Setăm manual luminozitatea ecranului
    val window = (context as Activity).window
    val layoutParams = window.attributes
    layoutParams.screenBrightness = currentScreenBrightness.value
    window.attributes = layoutParams

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
            .background(Color(0xFFE1BEE7))
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Welcome, $name!")
        Spacer(modifier = Modifier.height(16.dp))

        // Afișăm valoarea luminozității și textul care indică modul de luminozitate
        Text(text = "Light Level: %.2f lux".format(currentLightLevel.value))
        Text(text = "Screen Brightness: %.3f%%".format(currentScreenBrightness.value * 100)) // Afișăm în procent
        Spacer(modifier = Modifier.height(16.dp))

        // Afișăm textul "Bright Mode" sau "Dark Mode" în funcție de nivelul de lumină
        Text(text = modeText.value)

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
            .background(Color(0xFFE1BEE7))
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
            .background(Color(0xFFE1BEE7))
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