package com.example.proiectcorect


import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.proiectcorect.ui.theme.ProiectCorectTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale


data class Stock(
    val symbol: String = "",
    val closingPrice: Double = 0.0,
    val percentageChange: Double = 0.0,
    val currentPrice: Double
)



class MainActivity : ComponentActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var lightSensor: Sensor? = null

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    private var lightLevel = 0f // Variabilă pentru a stoca valoarea lux
    private var screenBrightness = 0 // Variabilă pentru a stoca luminozitatea ecranului
    private lateinit var disableBrightnessAuto: MutableState<Boolean>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inițializare senzori și Firestore
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        disableBrightnessAuto = mutableStateOf(false)

        //  Aici obțin token-ul FCM
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                Log.d("FCM", "Token-ul FCM este: $token")

                //  Salvez token-ul sub documentul utilizatorului curent (în colecția "users")
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
            if (auth.currentUser != null) {
            }
            uploadStocksToFirestore() {
                setContent {
                    disableBrightnessAuto = rememberSaveable { mutableStateOf(false) }
                    var currentScreen by remember { mutableStateOf(initialScreen) }
                    ProiectCorectTheme {
                        when (currentScreen) {
                            "LoginRegisterChoice" -> {
                                // Dezactivez senzorul de lumină
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
                                    onNavigateToSecondScreen = { currentScreen = "SecondScreen" },
                                    disableAutoBrightness = disableBrightnessAuto
                                )
                            }
                            "SecondScreen" -> {
                                SecondScreen(
                                    onBack = { currentScreen = "MainScreen" },
                                    onNavigateToPredictionHistory = { currentScreen = "PredictionHistory" },
                                    onNavigateToNewScreen = { currentScreen = "NewScreen" },
                                    disableAutoBrightness = disableBrightnessAuto
                                )
                            }

                            "PredictionHistory" -> {
                                PredictionHistoryScreen(
                                    onBack = { currentScreen = "SecondScreen" },
                                    disableAutoBrightness = disableBrightnessAuto
                                )
                            }
                            "NewScreen" -> {
                                NewScreen(
                                    onBack = { currentScreen = "SecondScreen" },
                                    onNavigateToPredictionComparison = { currentScreen = "PredictionComparison" },
                                    onNavigateToLeaderboard = { currentScreen = "Leaderboard" },
                                    disableAutoBrightness = disableBrightnessAuto

                                )
                            }
                            "PredictionComparison" -> {
                                PredictionComparisonScreen(onBack = { currentScreen = "NewScreen" },
                                    disableAutoBrightness = disableBrightnessAuto)
                            }
                            "Leaderboard" -> {
                                LeaderboardScreen(onBack = { currentScreen = "NewScreen" },
                                    disableAutoBrightness = disableBrightnessAuto)
                            }



                        }
                    }
                }
            }
        }


    }


    override fun onResume() {
        super.onResume()
        // Înregistrez listener-ul pentru senzorul de lumină
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
            lightLevel = event.values[0]

            //  Luminozitatea este ajustată DOAR dacă utilizatorul nu a dezactivat-o
            if (disableBrightnessAuto?.value == false) {
                adjustScreenBrightness(lightLevel)
            }
        }
    }


    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Nu este necesar să gestionez schimbările de acuratețe
    }

    // Ajustez luminozitatea ecranului în funcție de nivelul luminii ambientale
    private fun adjustScreenBrightness(lightLevel: Float) {
        val brightness = when {
            lightLevel < 10 -> 255
            lightLevel > 100 -> 50
            else -> (255 * (1 - (lightLevel / 100))).toInt()
        }

        val layoutParams = window.attributes
        layoutParams.screenBrightness = brightness / 255f
        window.attributes = layoutParams

        screenBrightness = (brightness / 255f * 100).toInt()
    }


    fun listenForStockUpdates() {
        val db = FirebaseFirestore.getInstance()
        db.collection("stocks").addSnapshotListener { snapshot, _ ->
            if (snapshot != null && !snapshot.isEmpty) {
                val stocks = snapshot.documents.map { document ->
                    Stock(
                        symbol = document.getString("symbol") ?: "",
                        percentageChange = document.getDouble("percentageChange") ?: 0.0,
                        currentPrice = document.getDouble("currentPrice") ?: 0.0  //  adăugat
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
        val stockSymbols = listOf("AAPL","GOOGL","META","TSLA","UBER","NVDA","AMZN","MSFT","NFLX", "ADBE","INTC","JPM")
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

            //  Upload la stocks
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
                                    "currentPrice" to stock.closingPrice,
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

            //  Upload la stocksthreshold (cu aceleași simboluri corecte)
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
        // Imagine de fundal
        Image(
            painter = background,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Text(
                text = "Stock Guardian",
                color = Color(0xFFFFD700), // Gold color
                fontSize = 36.sp,
                lineHeight = 44.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 170.dp)
            )
        }


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




fun fetchPrediction(saveToUser: Boolean = true, onResult: (String) -> Unit) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
            val today = Calendar.getInstance()
            val todayFormatted = dateFormat.format(today.time)

            val nextTradingDay = calculateNextTradingDay()
            val nextTradingDateStr = dateFormat.format(nextTradingDay.time)

            // Este zi nelucrătoare dacă data curentă NU e egală cu următoarea zi de tranzacționare
            val isNonTradingDay = todayFormatted != nextTradingDateStr

            val url = URL("https://us-central1-proiectcorect-b09a1.cloudfunctions.net/get_stock_prediction")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonResponse = JSONObject(response)
                val predictedPrice = jsonResponse.getDouble("predicted_price")
                val originalMessage = jsonResponse.getString("message")

                val timestamp = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()).format(Date())
                val userId = FirebaseAuth.getInstance().currentUser?.uid

                if (userId != null && saveToUser) {
                    val db = FirebaseFirestore.getInstance()
                    val predictionsRef = db.collection("users").document(userId).collection("predictions")
                    val predictionData = hashMapOf(
                        "timestamp" to timestamp,
                        "predictedPrice" to predictedPrice
                    )
                    predictionsRef.document(timestamp).set(predictionData)
                        .addOnSuccessListener {
                            Log.d("Prediction", "Predicția a fost salvată cu timestamp!")
                        }
                        .addOnFailureListener { e ->
                            Log.e("Prediction", "Eroare la salvare: ${e.message}")
                        }
                }

                withContext(Dispatchers.Main) {
                    val message = if (isNonTradingDay) {
                        "Predicția este pentru următoarea zi de tranzacționare: $nextTradingDateStr\nPreț prezis: $predictedPrice"
                    } else {
                        originalMessage
                    }
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








@Composable
fun LoginScreen(auth: FirebaseAuth, onLoginSuccess: () -> Unit,onNavigateToRegister: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

    val background = painterResource(id = R.drawable.loginpage)

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Image(
            painter = background,
            contentDescription = "Login Background",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = 0.4f))
        )


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
                                val userId = auth.currentUser?.uid
                                val email = auth.currentUser?.email ?: "unknown"
                                val username = email.substringBefore("@")
                                val db = FirebaseFirestore.getInstance()

                                if (userId != null) {
                                    db.collection("users").document(userId)
                                        .set(mapOf("username" to username), SetOptions.merge())
                                        .addOnSuccessListener {
                                            Log.d("Firestore", "Document users/$userId creat/actualizat.")
                                            //  Actualizează real_prices imediat ce aplicația pornește și utilizatorul e logat
                                            fetchPrediction(saveToUser = false) {
                                                Log.d("AutoUpdate", "real_prices actualizat fără salvare predicție")
                                            }
                                            onLoginSuccess()
                                        }
                                        .addOnFailureListener {
                                            Log.e("Firestore", "Eroare la creare document user: ${it.message}")
                                            onLoginSuccess() // Tot continuăm, dar logăm eroarea
                                        }
                                } else {
                                    onLoginSuccess()
                                }
                            }
                            else {
                                errorMessage = "Authentication failed: ${task.exception?.message}"
                            }
                        }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC407A))
            )
            {
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
                                    val userId = auth.currentUser?.uid
                                    val email = auth.currentUser?.email ?: "unknown"
                                    val username = email.substringBefore("@")
                                    val db = FirebaseFirestore.getInstance()

                                    if (userId != null) {
                                        db.collection("users").document(userId)
                                            .set(mapOf("username" to username), SetOptions.merge())
                                            .addOnSuccessListener {
                                                Log.d("Firestore", "Document users/$userId creat după înregistrare.")
                                                auth.signOut()
                                                onRegisterSuccess()
                                            }
                                            .addOnFailureListener {
                                                Log.e("Firestore", "Eroare la creare document user: ${it.message}")
                                                auth.signOut()
                                                onRegisterSuccess()
                                            }
                                    } else {
                                        auth.signOut()
                                        onRegisterSuccess()
                                    }
                                }
                                else {
                                    errorMessage = "Registration failed: ${task.exception?.message}"
                                }
                            }
                    } else {
                        errorMessage = "Passwords do not match!"
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFAB47BC))
            )
            {
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
                    color = Color(0xFFAB47BC),
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
fun MainScreen(name: String, lightLevel: Float, screenBrightness: Int, onUploadStocks: () -> Unit,onNavigateToSecondScreen: () -> Unit,disableAutoBrightness: MutableState<Boolean>) {
    var stocks by remember { mutableStateOf<List<Stock>>(emptyList()) }
    var thresholdSettingsOpen by remember { mutableStateOf(false) }
    var thresholds by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }

    val context = LocalContext.current

    // Gestionez luminozitatea și nivelul de lumină
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
    LaunchedEffect(disableAutoBrightness.value) {
        val layoutParams = window.attributes
        layoutParams.screenBrightness = if (disableAutoBrightness.value) {
            1f // maximă (manual)
        } else {
            WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE // automat
        }
        window.attributes = layoutParams
    }


    LaunchedEffect(Unit) {
        val db = FirebaseFirestore.getInstance()
        db.collection("stocks").get().addOnSuccessListener { result ->
            val loadedStocks = result.documents.map { document ->
                Stock(
                    symbol = document.getString("symbol") ?: "",
                    percentageChange = document.getDouble("percentageChange") ?: 0.0,
                    currentPrice = document.getDouble("currentPrice") ?: 0.0  //  adăugat
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
            },
            disableAutoBrightness = disableAutoBrightness
        )
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = R.drawable.mainscreen),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.4f))
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                Spacer(modifier = Modifier.height(90.dp))
                Text(text = "Welcome, $name!",
                    color = Color.White,
                    fontSize = 24.sp,
                    )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Light Level: %.2f lux".format(currentLightLevel.value),
                    color = Color.White,
                    fontSize = 18.sp
                )
                Text(
                    text = "Screen Brightness: %.3f%%".format(currentScreenBrightness.value * 100),
                    color = Color.White,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = modeText.value,
                    color = Color.White,
                    fontSize = 18.sp)

                Spacer(modifier = Modifier.height(16.dp))

                Button(onClick = onNavigateToSecondScreen) {
                    Text("Mergi la ecranul cu predicții")
                }


                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { thresholdSettingsOpen = true }) {
                    Text("Setează pragurile")
                }

                Spacer(modifier = Modifier.height(16.dp))


                Button(
                    onClick = {
                        disableAutoBrightness.value = true
                        Toast.makeText(context, "Luminozitatea automată a fost dezactivată.", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Dezactivează luminozitatea automată pentru următoarele ecrane")
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        disableAutoBrightness.value = false
                        Toast.makeText(context, "Luminozitatea automată a fost reactivată.", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reactivează luminozitatea automată pentru următoarele ecrane")
                }




            }
        }
    }
}




@Composable
fun SecondScreen(
    onBack: () -> Unit,
    onNavigateToPredictionHistory: () -> Unit,
    onNavigateToNewScreen: () -> Unit,
    disableAutoBrightness: MutableState<Boolean>
) {

    val context = LocalContext.current
    var predictionResult by remember { mutableStateOf<String?>(null) }
    val window = (LocalContext.current as Activity).window
    LaunchedEffect(Unit) {
        if (disableAutoBrightness.value) {
            val layoutParams = window.attributes
            layoutParams.screenBrightness = 1f
            window.attributes = layoutParams
        }
    }


    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Image(
            painter = painterResource(id = R.drawable.secondscreen),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().padding(16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = {
                fetchPrediction { result ->
                    predictionResult = result
                }
            }) {
                Text("Predicție S&P 500")
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
                Text("Istoric Predicții")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = onNavigateToNewScreen) {
                Text("Mergi la ecranul cu predicții personalizabile")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = onBack) {
                Text("Înapoi")
            }
        }
    }
}




@Composable
fun PredictionHistoryScreen(onBack: () -> Unit,disableAutoBrightness: MutableState<Boolean>) {
    var predictions by remember { mutableStateOf<List<Pair<String, Double>>>(emptyList()) }
    val context = LocalContext.current
    val window = (LocalContext.current as Activity).window
    LaunchedEffect(Unit) {
        if (disableAutoBrightness.value) {
            val layoutParams = window.attributes
            layoutParams.screenBrightness = 1f
            window.attributes = layoutParams
        }
    }

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
        Image(
            painter = painterResource(id = R.drawable.historicalscreen),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x80000000))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Istoric Predicții",
                style = MaterialTheme.typography.titleLarge,
                color = Color(0xFFFFF9C4) // un galben pal contrastant (Lemon Chiffon)
            )


            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(predictions) { (timestamp, price) ->
                    Text(
                        "Data și ora: $timestamp → Preț: ${"%.2f".format(price)} USD",
                        color = Color(0xFFFFF9C4) // un galben pal contrastant (Lemon Chiffon)
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Înapoi")
            }
        }
    }
}

fun calculateNextTradingDay(): Calendar {
    val calendar = Calendar.getInstance()
    val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

    fun isStaticHoliday(date: Calendar): Boolean {
        val formatted = sdf.format(date.time)
        return listOf("01.01", "04.07", "25.12").any { formatted.startsWith(it) }
    }

    fun isDynamicHoliday(date: Calendar): Boolean {
        val year = date.get(Calendar.YEAR)

        val mlkDay = Calendar.getInstance().apply {
            set(year, Calendar.JANUARY, 1)
            while (get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) add(Calendar.DAY_OF_YEAR, 1)
            add(Calendar.WEEK_OF_MONTH, 2)
        }

        val presidentsDay = Calendar.getInstance().apply {
            set(year, Calendar.FEBRUARY, 1)
            while (get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) add(Calendar.DAY_OF_YEAR, 1)
            add(Calendar.WEEK_OF_MONTH, 2)
        }

        val memorialDay = Calendar.getInstance().apply {
            set(year, Calendar.MAY, 31)
            while (get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) add(Calendar.DAY_OF_YEAR, -1)
        }

        val laborDay = Calendar.getInstance().apply {
            set(year, Calendar.SEPTEMBER, 1)
            while (get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) add(Calendar.DAY_OF_YEAR, 1)
        }

        val thanksgiving = Calendar.getInstance().apply {
            set(year, Calendar.NOVEMBER, 1)
            while (get(Calendar.DAY_OF_WEEK) != Calendar.THURSDAY) add(Calendar.DAY_OF_YEAR, 1)
            add(Calendar.WEEK_OF_MONTH, 3)
        }

        return listOf(mlkDay, presidentsDay, memorialDay, laborDay, thanksgiving)
            .any { sdf.format(it.time) == sdf.format(date.time) }
    }

    val today = calendar.clone() as Calendar
    val isWeekend = today.get(Calendar.DAY_OF_WEEK) in listOf(Calendar.SATURDAY, Calendar.SUNDAY)
    val isHoliday = isStaticHoliday(today) || isDynamicHoliday(today)

    if (!isWeekend && !isHoliday) {
        return today
    }

    // Dacă azi nu e zi de trading, căutăm următoarea
    while (true) {
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val dow = calendar.get(Calendar.DAY_OF_WEEK)
        val wknd = dow == Calendar.SATURDAY || dow == Calendar.SUNDAY
        val hld = isStaticHoliday(calendar) || isDynamicHoliday(calendar)

        if (!wknd && !hld) break
    }

    return calendar
}
fun getLastTradingDay(): Calendar {
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.DAY_OF_YEAR, -1)

    val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())

    fun isStaticHoliday(date: Calendar): Boolean {
        val formatted = sdf.format(date.time)
        return listOf("01.01", "04.07", "25.12").any { formatted.startsWith(it) }
    }

    fun isDynamicHoliday(date: Calendar): Boolean {
        val year = date.get(Calendar.YEAR)

        val mlk = Calendar.getInstance().apply {
            set(year, Calendar.JANUARY, 1)
            while (get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) add(Calendar.DAY_OF_YEAR, 1)
            add(Calendar.WEEK_OF_MONTH, 2)
        }

        val presidents = Calendar.getInstance().apply {
            set(year, Calendar.FEBRUARY, 1)
            while (get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) add(Calendar.DAY_OF_YEAR, 1)
            add(Calendar.WEEK_OF_MONTH, 2)
        }

        val memorial = Calendar.getInstance().apply {
            set(year, Calendar.MAY, 31)
            while (get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) add(Calendar.DAY_OF_YEAR, -1)
        }

        val labor = Calendar.getInstance().apply {
            set(year, Calendar.SEPTEMBER, 1)
            while (get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) add(Calendar.DAY_OF_YEAR, 1)
        }

        val thanksgiving = Calendar.getInstance().apply {
            set(year, Calendar.NOVEMBER, 1)
            while (get(Calendar.DAY_OF_WEEK) != Calendar.THURSDAY) add(Calendar.DAY_OF_YEAR, 1)
            add(Calendar.WEEK_OF_MONTH, 3)
        }

        return listOf(mlk, presidents, memorial, labor, thanksgiving)
            .any { sdf.format(it.time) == sdf.format(date.time) }
    }

    while (true) {
        val dow = calendar.get(Calendar.DAY_OF_WEEK)
        val isWeekend = dow == Calendar.SATURDAY || dow == Calendar.SUNDAY
        val isHoliday = isStaticHoliday(calendar) || isDynamicHoliday(calendar)
        if (!isWeekend && !isHoliday) break
        calendar.add(Calendar.DAY_OF_YEAR, -1)
    }

    return calendar
}






@Composable
fun NewScreen(
    onBack: () -> Unit,
    onNavigateToPredictionComparison: () -> Unit,
    onNavigateToLeaderboard: () -> Unit,
    disableAutoBrightness: MutableState<Boolean>
) {
    val formatter = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault())
    val timestampFormatted = formatter.format(Date())
    val context = LocalContext.current
    val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    val nextTradingDay = remember {
        calculateNextTradingDay()
    }
    val tomorrowDateStr = sdf.format(nextTradingDay.time)


    var userPrediction by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    val window = (LocalContext.current as Activity).window
    LaunchedEffect(Unit) {
        if (disableAutoBrightness.value) {
            val layoutParams = window.attributes
            layoutParams.screenBrightness = 1f
            window.attributes = layoutParams
        }
    }


    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.predictionscreen),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        )
        {
            Spacer(modifier = Modifier.height(80.dp))
            Text(
                text = "Preziceți prețul pentru data\n$tomorrowDateStr",
                color = Color(0xFFEC407A),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 30.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(190.dp))

            TextField(
                value = userPrediction,
                onValueChange = { userPrediction = it },
                label = { Text("Introduceți prețul prezis", color = Color.Black) },
                textStyle = LocalTextStyle.current.copy(color = Color.Black),
                colors = TextFieldDefaults.colors(
                    unfocusedContainerColor = Color.White.copy(alpha = 0.6f),
                    focusedContainerColor = Color.White.copy(alpha = 0.6f),
                    focusedIndicatorColor = Color(0xFF0D47A1),
                    focusedLabelColor = Color.Black,
                    cursorColor = Color(0xFF0D47A1)
                ),
                modifier = Modifier.fillMaxWidth()
            )





            Button(
                onClick = {
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
                            "timestamp" to timestampFormatted
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
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC407A)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Salvează predicția")
            }

            message?.let {
                Text(it, color = Color.White)
            }

            Spacer(modifier = Modifier.weight(1f)) // împinge butoanele jos

            Button(
                onClick = onNavigateToPredictionComparison,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFAB47BC)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = "Vezi diferențele între predicții și prețul real",
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )

            }

            Button(
                onClick = { onNavigateToLeaderboard() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFAB47BC)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Vezi Clasamentul")
            }

            Button(
                onClick = { onBack() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFAB47BC)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("Înapoi la ecranul anterior")
            }
        }
    }
}


@Composable
fun PredictionComparisonScreen(onBack: () -> Unit, disableAutoBrightness: MutableState<Boolean>) {
    val context = LocalContext.current
    val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    var comparisons by remember { mutableStateOf<List<Triple<String, Double, Double>>>(emptyList()) }
    val window = (LocalContext.current as Activity).window
    LaunchedEffect(Unit) {
        if (disableAutoBrightness.value) {
            val layoutParams = window.attributes
            layoutParams.screenBrightness = 1f
            window.attributes = layoutParams
        }
    }


    LaunchedEffect(Unit) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
        val db = FirebaseFirestore.getInstance()

        db.collection("users").document(userId).collection("user_predictions")
            .get().addOnSuccessListener { predictionsSnapshot ->
                val fetchedComparisons = mutableListOf<Triple<String, Double, Double>>()

                for (predictionDoc in predictionsSnapshot.documents) {
                    val date = predictionDoc.id
                    val predictedPrice = predictionDoc.getDouble("predictedPrice") ?: continue

                    db.collection("real_prices").document(date).get().addOnSuccessListener { stockDoc ->
                        val actualPrice = stockDoc.getDouble("closingPrice")
                        if (actualPrice != null) {
                            fetchedComparisons.add(Triple(date, predictedPrice, actualPrice))
                            val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                            comparisons = fetchedComparisons.sortedBy { dateFormat.parse(it.first) }
                        }
                    }

                }
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.comparisonscreen),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Comparație Predicții vs Preț Real",
                color = Color(0xFF0D47A1),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 25.sp,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.fillMaxWidth()
            )


            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                items(comparisons) { (date, predicted, actual) ->
                    val diff = actual - predicted
                    Text(
                        "Data: $date | Prezis: $predicted | Real: $actual | Dif: ${"%.2f".format(diff)} USD",
                        color = Color(0xFFFFF9C4) // un galben pal contrastant (Lemon Chiffon)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Înapoi")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "\"I've never met a dollar I didn't like\"",
                color = Color(0xFF0D47A1),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 32.sp,
                    lineHeight = 28.sp
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

    }
}



fun updateLeaderboardForDate(targetDate: String, onComplete: (Boolean) -> Unit) {
    val db = FirebaseFirestore.getInstance()
    val formatter = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault())

    Log.d("Leaderboard", "Pornim updateLeaderboard pentru data: $targetDate")

    db.collection("users").get().addOnSuccessListener { usersSnapshot ->
        Log.d("Leaderboard", "Număr utilizatori găsiți: ${usersSnapshot.size()}")

        val stockRef = db.collection("real_prices").document(targetDate)
        stockRef.get().addOnSuccessListener { stockDoc ->
            val actualPrice = stockDoc.getDouble("closingPrice")
            Log.d("Leaderboard", "closingPrice extras pentru $targetDate: $actualPrice")

            if (actualPrice == null) {
                Log.e("Leaderboard", "Nu există closingPrice în real_prices/$targetDate")
                onComplete(false)
                return@addOnSuccessListener
            }

            val predictionDate = targetDate
            Log.d("Leaderboard", "Vom căuta predicțiile în user_predictions/$predictionDate")

            var processedUsers = 0
            val totalUsers = usersSnapshot.size()
            if (totalUsers == 0) {
                Log.w("Leaderboard", "Niciun utilizator găsit.")
                onComplete(true)
                return@addOnSuccessListener
            }

            for (userDoc in usersSnapshot.documents) {
                val userId = userDoc.id
                val predictionsRef = db.collection("users").document(userId)
                    .collection("user_predictions").document(predictionDate)

                Log.d("Leaderboard", "Verificăm predicția pentru user: $userId")

                predictionsRef.get().addOnSuccessListener { predictionDoc ->
                    if (predictionDoc.exists()) {
                        val predicted = predictionDoc.getDouble("predictedPrice")
                        val username = predictionDoc.getString("username") ?: "anonim"

                        if (predicted == null) {
                            Log.w("Leaderboard", "Predicted price este null pentru user $userId")
                            processedUsers++
                            if (processedUsers == totalUsers) onComplete(true)
                            return@addOnSuccessListener
                        }

                        val score = kotlin.math.abs(actualPrice - predicted)
                        Log.d("Leaderboard", "User $username ($userId): Predicted = $predicted, Real = $actualPrice, Score = $score")
                        val timestampRaw = predictionDoc.get("timestamp")
                        val timestampStr = when (timestampRaw) {
                            is String -> timestampRaw
                            else -> ""
                        }
                        val timeMillis  = try { formatter.parse(timestampStr)?.time ?: Long.MAX_VALUE }
                        catch (e: Exception) { Long.MAX_VALUE }

                        val leaderboardEntry = mapOf(
                            "username" to username,
                            "score"    to score,
                            "time"     to timeMillis  //  îl salvez ca Long
                        )

                        db.collection("leaderboard")
                            .document(targetDate)
                            .collection("entries")
                            .document(userId)
                            .set(leaderboardEntry)
                            .addOnSuccessListener {
                                Log.d("Leaderboard", "Scor salvat pentru $username")
                                processedUsers++
                                if (processedUsers == totalUsers) onComplete(true)
                            }
                            .addOnFailureListener {
                                Log.e("Leaderboard", "Eroare la salvare pentru $username: ${it.message}")
                                processedUsers++
                                if (processedUsers == totalUsers) onComplete(true)
                            }
                    } else {
                        Log.w("Leaderboard", "Nu există predicție pentru user $userId în $predictionDate")
                        processedUsers++
                        if (processedUsers == totalUsers) onComplete(true)
                    }
                }.addOnFailureListener {
                    Log.e("Leaderboard", "Eroare la citirea predicției pentru $userId: ${it.message}")
                    processedUsers++
                    if (processedUsers == totalUsers) onComplete(true)
                }
            }
        }.addOnFailureListener {
            Log.e("Leaderboard", "Eroare la citirea prețului real pentru $targetDate: ${it.message}")
            onComplete(false)
        }
    }.addOnFailureListener {
        Log.e("Leaderboard", "Eroare la citirea utilizatorilor: ${it.message}")
        onComplete(false)
    }
}





@Composable
fun LeaderboardScreen(onBack: () -> Unit,disableAutoBrightness: MutableState<Boolean>) {
    val context = LocalContext.current
    val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    val today = sdf.format(Calendar.getInstance().time)
    val lastTradingDay = remember { sdf.format(getLastTradingDay().time) }

    var leaderboard by remember {
        mutableStateOf<List<Pair<String, Pair<Double, Long>>>>(emptyList())
    }
    var loading by remember { mutableStateOf(true) }
    val window = (LocalContext.current as Activity).window
    LaunchedEffect(Unit) {
        if (disableAutoBrightness.value) {
            val layoutParams = window.attributes
            layoutParams.screenBrightness = 1f
            window.attributes = layoutParams
        }
    }


    LaunchedEffect(lastTradingDay) {
        updateLeaderboardForDate(lastTradingDay) { success ->
            if (success) {
                val db = FirebaseFirestore.getInstance()
                db.collection("leaderboard")
                    .document(lastTradingDay)
                    .collection("entries")
                    .get()
                    .addOnSuccessListener { snapshot ->
                        val results = snapshot.documents.mapNotNull { doc ->
                            val username = doc.getString("username")
                            val score    = doc.getDouble("score")
                            val time     = doc.getLong("time") ?: Long.MAX_VALUE
                            if (username != null && score != null) {
                                username to (score to time)
                            } else null
                        }
                            .sortedWith(compareBy<Pair<String, Pair<Double, Long>>> { it.second.first }
                            .thenBy { it.second.second }) // dacă scorul e egal, sortez după timestamp (primul intrat)
                        leaderboard = results
                        loading = false
                    }
                    .addOnFailureListener {
                        Toast.makeText(context, "Eroare la încărcarea clasamentului.", Toast.LENGTH_SHORT).show()
                        loading = false
                    }
            } else {
                Toast.makeText(context, "Eroare la actualizarea clasamentului.", Toast.LENGTH_SHORT).show()
                loading = false
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.leaderboardscreen),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Clasament Predicții - $today",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = Color(0xFFB71C1C),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(60.dp))

            Text(
                text = "Ultima zi de tranzacționare: $lastTradingDay",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 25.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = Color(0xFFB71C1C),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (loading) {
                CircularProgressIndicator(color = Color.White)
            } else if (leaderboard.isEmpty()) {
                Text(
                    text = "Nicio predicție disponibilă pentru această zi.",
                    color = Color.White,
                    fontSize = 18.sp
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f)
                ) {
                    itemsIndexed(leaderboard) { index, (username, scoreAndTime) ->
                        val score = scoreAndTime.first
                        Text(
                            text = "${index + 1}. $username — Diferență: ${"%.2f".format(score)} USD",
                            fontSize = 18.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Înapoi")
            }
        }
    }
}






@Composable
fun StockThresholdSettingsScreen(
    stocks: List<Stock>,
    context: Context,
    onSaveThresholds: (Map<String, Double>) -> Unit,
    disableAutoBrightness: MutableState<Boolean>
) {
    var stockThresholds by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    val window = (LocalContext.current as Activity).window
    LaunchedEffect(Unit) {
        if (disableAutoBrightness.value) {
            val layoutParams = window.attributes
            layoutParams.screenBrightness = 1f
            window.attributes = layoutParams
        }
    }

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
                color = Color.White
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
    increasedStocks: List<Stock>,
    decreasedStocks: List<Stock>
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
            "${it.symbol} ${String.format("%+.2f", it.percentageChange)}%  (${it.currentPrice} USD)"
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
            "${it.symbol} ${String.format("%+.2f", it.percentageChange)}%  (${it.currentPrice} USD)"
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
    increasedStocks: List<Stock>,
    decreasedStocks: List<Stock>
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