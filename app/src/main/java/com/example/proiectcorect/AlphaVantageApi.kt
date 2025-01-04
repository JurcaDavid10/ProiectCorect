package com.example.proiectcorect

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.google.gson.annotations.SerializedName

object RetrofitClient {
    private const val BASE_URL = "https://www.alphavantage.co/"

    val api: AlphaVantageApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AlphaVantageApi::class.java)
    }
}

interface AlphaVantageApi {
    @GET("query")
    fun getStockPrice(
        @Query("function") function: String = "TIME_SERIES_INTRADAY",
        @Query("symbol") symbol: String,
        @Query("interval") interval: String = "1min",
        @Query("apikey") apiKey: String
    ): Call<AlphaVantageResponse>
}

data class AlphaVantageResponse(
    @SerializedName("Meta Data") val metaData: MetaData,
    @SerializedName("Time Series (1min)") val timeSeries: Map<String, TimeSeriesData>
)

data class MetaData(
    @SerializedName("1. Information") val info: String,
    @SerializedName("2. Symbol") val symbol: String
)

data class TimeSeriesData(
    @SerializedName("1. open") val open: String,
    @SerializedName("2. high") val high: String,
    @SerializedName("3. low") val low: String,
    @SerializedName("4. close") val close: String,
    @SerializedName("5. volume") val volume: String
)