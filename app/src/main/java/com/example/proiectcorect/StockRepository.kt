////RJ START
//package com.example.proiectcorect
//
//import android.util.Log
//import retrofit2.Call
//import retrofit2.Callback
//import retrofit2.Response
//
//class StockRepository {
//
//    // Function to fetch stock data and return a list of stocks with their latest value
//    fun fetchStockData(apiKey: String, symbols: List<String>, onResult: (List<Stock>) -> Unit) {
//        val stocks = mutableListOf<Stock>()
//
//        // Loop through the list of stock symbols and make API calls for each
//        for (symbol in symbols) {
//            Log.d("StockRepository", "Fetching data for symbol: $symbol")
//
//            RetrofitClient.api.getStockPrice(symbol = symbol, apiKey = apiKey).enqueue(object : Callback<AlphaVantageResponse> {
//                override fun onResponse(call: Call<AlphaVantageResponse>, response: Response<AlphaVantageResponse>) {
//                    Log.d("StockRepository", "Response received for symbol: $symbol")
//
//                    if (response.isSuccessful) {
//                        Log.d("StockRepository", "Response successful for symbol: $symbol")
//
//                        val stockResponse = response.body()
//                        if (stockResponse != null) {
//                            val latestData = stockResponse.timeSeries?.entries?.firstOrNull()
//
//                            if (latestData != null) {
//                                val stockSymbol = stockResponse.metaData.symbol
//                                val latestClosePrice = latestData.value.close.toDoubleOrNull() ?: 0.0
//
//                                // Assuming here that the percentage change will be calculated or fetched from elsewhere
//                                val changePercentage = calculateChangePercentage(latestClosePrice)
//
//                                // Add stock data to the list
//                                stocks.add(Stock(stockSymbol, changePercentage))
//
//                                Log.d("StockRepository", "Stock added: $stockSymbol, Price: $latestClosePrice, Change: $changePercentage%")
//                            } else {
//                                Log.e("StockRepository", "No data found for $symbol")
//                            }
//                        } else {
//                            Log.e("StockRepository", "Error: Response body is null for $symbol")
//                        }
//                    } else {
//                        Log.e("StockRepository", "Failed to fetch data for $symbol. Response code: ${response.code()}")
//                    }
//
//                    // Call the onResult lambda with the updated list of stocks once all requests are processed
//                    if (stocks.size == symbols.size) {
//                        Log.d("StockRepository", "All symbols processed, returning result")
//                        onResult(stocks)
//                    }
//                }
//
//                override fun onFailure(call: Call<AlphaVantageResponse>, t: Throwable) {
//                    Log.e("StockRepository", "Request failed for symbol: $symbol, Error: ${t.message}")
//                }
//            })
//        }
//    }
//
//    // Simple function to calculate stock percentage change (to be replaced with actual logic)
//    private fun calculateChangePercentage(latestClosePrice: Double): Double {
//        // This is a placeholder for change calculation logic
//        val changePercentage = latestClosePrice * 0.02 // Assume a 2% change for now
//        Log.d("StockRepository", "Calculated change percentage: $changePercentage%")
//        return changePercentage
//    }
//}
////RJ END

package com.example.proiectcorect

import android.util.Log
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.atomic.AtomicInteger

class StockRepository {

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://finnhub.io/api/v1/")  // Finnhub base URL
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val api = retrofit.create(FinnhubApi::class.java)

    // Function to fetch stock data and return a list of stocks with their latest value
    fun fetchStockData(apiKey: String, symbols: List<String>, onResult: (List<Stock>) -> Unit) {
        val stocks = mutableListOf<Stock>()
        val totalSymbols = symbols.size
        val completedRequests = AtomicInteger(0) // Atomic counter to track completed requests

        // Maximum retry attempts
        val maxRetryAttempts = 3

        // Function to fetch a single stock with retry logic
        fun fetchStock(symbol: String, retryCount: Int = 0) {
            Log.d("StockRepository", "Fetching data for symbol: $symbol (Attempt: ${retryCount + 1})")

            api.getStockQuote(symbol = symbol, apiKey = apiKey).enqueue(object : Callback<StockQuoteResponse> {
                override fun onResponse(call: Call<StockQuoteResponse>, response: Response<StockQuoteResponse>) {
                    if (response.isSuccessful) {
                        val stockResponse = response.body()
                        if (stockResponse != null) {
                            val stockSymbol = symbol
                            val latestClosePrice = stockResponse.pc.toDouble() // Previous close price
                            val changePercentage = stockResponse.dp // Percentage change

                            // Add stock data to the list
                            stocks.add(Stock(stockSymbol, latestClosePrice, changePercentage))
                            Log.d("StockRepository", "Stock added: $stockSymbol, Change: $changePercentage%")
                        } else {
                            Log.e("StockRepository", "Response body is null for $symbol")
                        }
                    } else {
                        Log.e("StockRepository", "Failed to fetch data for $symbol. Response code: ${response.code()}")
                    }

                    // Increment completed requests counter
                    if (completedRequests.incrementAndGet() == totalSymbols) {
                        Log.d("StockRepository", "All symbols processed, returning result")
                        onResult(stocks)
                    }
                }

                override fun onFailure(call: Call<StockQuoteResponse>, t: Throwable) {
                    if (retryCount < maxRetryAttempts) {
                        Log.e("StockRepository", "Request failed for $symbol. Retrying... (${retryCount + 1}/${maxRetryAttempts})")
                        fetchStock(symbol, retryCount + 1)
                    } else {
                        Log.e("StockRepository", "Request failed for $symbol after $maxRetryAttempts attempts. Error: ${t.message}")

                        // Increment completed requests counter even if the retries are exhausted
                        if (completedRequests.incrementAndGet() == totalSymbols) {
                            Log.d("StockRepository", "All symbols processed, returning result")
                            onResult(stocks)
                        }
                    }
                }
            })
        }

        // Loop through the list of stock symbols and fetch data for each
        for (symbol in symbols) {
            fetchStock(symbol)
        }
    }
}

