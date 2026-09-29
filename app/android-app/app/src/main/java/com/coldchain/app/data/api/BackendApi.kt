package com.coldchain.app.data.api

import com.coldchain.app.data.models.*
import retrofit2.http.*

interface BackendApi {

    // ── Auth ────────────────────────────────────────────────────────────────

    @POST("auth/login")
    suspend fun login(@Body request: AuthRequest): AuthResponse

    @POST("auth/signup")
    suspend fun signup(@Body request: AuthRequest): AuthResponse

    @GET("auth/me")
    suspend fun getMe(@Header("Authorization") bearerToken: String): UserInfo

    // ── Public Shipment ─────────────────────────────────────────────────────

    @GET("shipment/{id}")
    suspend fun getShipmentTelemetry(@Path("id") shipmentId: String): ShipmentTelemetryResponse

    // ── Sender ──────────────────────────────────────────────────────────────

    @POST("shipments")
    suspend fun createShipment(
        @Header("Authorization") bearerToken: String,
        @Body request: CreateShipmentRequest
    ): ShipmentResponse

    @GET("shipments/mine")
    suspend fun getMyShipments(
        @Header("Authorization") bearerToken: String
    ): MyShipmentsResponse

    @PATCH("shipments/{id}/status")
    suspend fun updateShipmentStatus(
        @Header("Authorization") bearerToken: String,
        @Path("id") shipmentId: String,
        @Body request: UpdateStatusRequest
    ): ShipmentResponse

    // ── Admin ────────────────────────────────────────────────────────────────

    @GET("admin/stats")
    suspend fun getAdminStats(
        @Header("Authorization") bearerToken: String
    ): AdminStatsResponse

    @GET("admin/shipments")
    suspend fun getAllShipments(
        @Header("Authorization") bearerToken: String
    ): AdminShipmentsResponse
}
