package com.example.proiectcorect

data class StockQuoteResponse(
    val c: Double,   // Current price
    val d: Double,   // Change in price
    val dp: Double,  // Percentage change
    val h: Double,   // High price
    val l: Double,   // Low price
    val o: Double,   // Open price
    val pc: Double   // Previous close price
)
