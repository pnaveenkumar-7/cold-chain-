package com.coldchain.app.ui.screens.sender

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coldchain.app.data.models.CreateShipmentRequest
import com.coldchain.app.data.models.Shipment
import com.coldchain.app.data.repository.ShipmentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    data class Error(val message: String) : AuthUiState()
    data class Authenticated(val email: String, val token: String, val role: String) : AuthUiState()
}

@HiltViewModel
class SenderViewModel @Inject constructor(
    private val repository: ShipmentRepository
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val authState = _authState.asStateFlow()

    private val _myShipments = MutableStateFlow<List<Shipment>>(emptyList())
    val myShipments = _myShipments.asStateFlow()

    private val _shipmentsLoading = MutableStateFlow(false)
    val shipmentsLoading = _shipmentsLoading.asStateFlow()

    private val _shipmentsError = MutableStateFlow<String?>(null)
    val shipmentsError = _shipmentsError.asStateFlow()

    private val _createdShipment = MutableStateFlow<Shipment?>(null)
    val createdShipment = _createdShipment.asStateFlow()

    private val _createLoading = MutableStateFlow(false)
    val createLoading = _createLoading.asStateFlow()

    private val _createError = MutableStateFlow<String?>(null)
    val createError = _createError.asStateFlow()

    init {
        // Automatically restore session from DataStore
        viewModelScope.launch {
            repository.sessionFlow.collect { session ->
                if (session != null && session.role == "sender") {
                    _authState.value = AuthUiState.Authenticated(session.email, session.token, session.role)
                    loadMyShipments(session.token)
                } else if (session == null && _authState.value is AuthUiState.Authenticated) {
                    _authState.value = AuthUiState.Idle
                    _myShipments.value = emptyList()
                }
            }
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthUiState.Loading
            val result = repository.login(email, password)
            result.fold(
                onSuccess = { resp ->
                    if (resp.user.role != "sender") {
                        _authState.value = AuthUiState.Error("Access denied: Sender Portal requires a \"sender\" account.")
                    } else {
                        _authState.value = AuthUiState.Authenticated(resp.user.email, resp.token, resp.user.role)
                        loadMyShipments(resp.token)
                    }
                },
                onFailure = { _authState.value = AuthUiState.Error(it.message ?: "Login failed") }
            )
        }
    }

    fun signup(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthUiState.Loading
            val signupResult = repository.signup(email, password)
            signupResult.fold(
                onSuccess = { resp ->
                    _authState.value = AuthUiState.Authenticated(resp.user.email, resp.token, resp.user.role)
                    loadMyShipments(resp.token)
                },
                onFailure = { _authState.value = AuthUiState.Error(it.message ?: "Signup failed") }
            )
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            _authState.value = AuthUiState.Idle
            _myShipments.value = emptyList()
            _createdShipment.value = null
        }
    }

    fun loadMyShipments(tokenOverride: String? = null) {
        val token = tokenOverride ?: (authState.value as? AuthUiState.Authenticated)?.token ?: return
        viewModelScope.launch {
            _shipmentsLoading.value = true
            _shipmentsError.value = null
            repository.getMyShipments(token).fold(
                onSuccess = { _myShipments.value = it },
                onFailure = { _shipmentsError.value = it.message }
            )
            _shipmentsLoading.value = false
        }
    }

    fun createShipment(
        origin: String, destination: String, receiverName: String,
        receiverContact: String, vehicleId: String, cargoDescription: String
    ) {
        val token = (authState.value as? AuthUiState.Authenticated)?.token ?: return
        viewModelScope.launch {
            _createLoading.value = true
            _createError.value = null
            _createdShipment.value = null
            repository.createShipment(
                token,
                CreateShipmentRequest(origin, destination, receiverName, receiverContact, vehicleId, cargoDescription)
            ).fold(
                onSuccess = { shipment ->
                    _createdShipment.value = shipment
                    loadMyShipments(token)
                },
                onFailure = { _createError.value = it.message }
            )
            _createLoading.value = false
        }
    }

    fun updateStatus(shipmentId: String, newStatus: String) {
        val token = (authState.value as? AuthUiState.Authenticated)?.token ?: return
        viewModelScope.launch {
            repository.updateStatus(token, shipmentId, newStatus)
            loadMyShipments(token)
        }
    }
}
