package com.example.proiectcorect

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface FinnhubApi {

    @GET("quote")
    fun getStockQuote(
        @Query("symbol") symbol: String,
        @Query("token") apiKey: String
    ): Call<StockQuoteResponse>
}
