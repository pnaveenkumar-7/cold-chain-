package com.coldchain.app.ui.screens.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coldchain.app.data.models.Shipment
import com.coldchain.app.data.models.SystemStats
import com.coldchain.app.data.repository.ShipmentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AdminAuthState {
    object Idle : AdminAuthState()
    object Loading : AdminAuthState()
    data class Error(val message: String) : AdminAuthState()
    data class Authenticated(val email: String, val token: String) : AdminAuthState()
}

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val repository: ShipmentRepository
) : ViewModel() {

    private val _authState = MutableStateFlow<AdminAuthState>(AdminAuthState.Idle)
    val authState = _authState.asStateFlow()

    private val _stats = MutableStateFlow<SystemStats?>(null)
    val stats = _stats.asStateFlow()

    private val _shipments = MutableStateFlow<List<Shipment>>(emptyList())
    val shipments = _shipments.asStateFlow()

    private val _dataLoading = MutableStateFlow(false)
    val dataLoading = _dataLoading.asStateFlow()

    private val _dataError = MutableStateFlow<String?>(null)
    val dataError = _dataError.asStateFlow()

    init {
        // Automatically restore session if user is admin
        viewModelScope.launch {
            repository.sessionFlow.collect { session ->
                if (session != null && session.role == "admin") {
                    _authState.value = AdminAuthState.Authenticated(session.email, session.token)
                    loadAdminData(session.token)
                } else if (session == null && _authState.value is AdminAuthState.Authenticated) {
                    _authState.value = AdminAuthState.Idle
                    _stats.value = null
                    _shipments.value = emptyList()
                }
            }
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AdminAuthState.Loading
            repository.login(email, password).fold(
                onSuccess = { resp ->
                    if (resp.user.role != "admin") {
                        _authState.value = AdminAuthState.Error("Access denied: Admin Portal requires an 'admin' role account.")
                    } else {
                        _authState.value = AdminAuthState.Authenticated(resp.user.email, resp.token)
                        loadAdminData(resp.token)
                    }
                },
                onFailure = { _authState.value = AdminAuthState.Error(it.message ?: "Login failed") }
            )
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            _authState.value = AdminAuthState.Idle
            _stats.value = null
            _shipments.value = emptyList()
        }
    }

    fun loadAdminData(tokenOverride: String? = null) {
        val token = tokenOverride ?: (_authState.value as? AdminAuthState.Authenticated)?.token ?: return
        viewModelScope.launch {
            _dataLoading.value = true
            _dataError.value = null

            repository.getAdminStats(token).fold(
                onSuccess = { _stats.value = it },
                onFailure = { _dataError.value = it.message }
            )
            repository.getAllShipments(token).fold(
                onSuccess = { _shipments.value = it },
                onFailure = { _dataError.value = it.message }
            )

            _dataLoading.value = false
        }
    }

    fun updateStatus(shipmentId: String, newStatus: String) {
        val token = (_authState.value as? AdminAuthState.Authenticated)?.token ?: return
        viewModelScope.launch {
            repository.updateStatus(token, shipmentId, newStatus)
            loadAdminData(token)
        }
    }
}
