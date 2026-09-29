package com.coldchain.app.ui.screens.inspect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coldchain.app.data.models.OnChainRecord
import com.coldchain.app.data.models.SensorRecord
import com.coldchain.app.data.models.ShipmentTelemetryResponse
import com.coldchain.app.data.models.VerificationReport
import com.coldchain.app.data.repository.ShipmentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class InspectUiState {
    object Idle : InspectUiState()
    object Loading : InspectUiState()
    data class NotFound(val shipmentId: String) : InspectUiState()
    data class Error(val message: String) : InspectUiState()
    data class Success(
        val onChain: OnChainRecord,
        val telemetry: ShipmentTelemetryResponse,
        val records: List<SensorRecord>,
        val report: VerificationReport,
        val selectedBatchIndex: Int = -1 // -1 = All Batches (Aggregated)
    ) : InspectUiState()
}

@HiltViewModel
class PublicInspectViewModel @Inject constructor(
    private val repository: ShipmentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<InspectUiState>(InspectUiState.Idle)
    val uiState = _uiState.asStateFlow()

    private val _shipmentId = MutableStateFlow("")
    val shipmentId = _shipmentId.asStateFlow()

    private val _selectedBatchIndex = MutableStateFlow(-1)
    val selectedBatchIndex = _selectedBatchIndex.asStateFlow()

    fun onShipmentIdChanged(value: String) {
        _shipmentId.value = value
    }

    fun selectBatch(index: Int) {
        _selectedBatchIndex.value = index
        val current = _uiState.value
        if (current is InspectUiState.Success) {
            _uiState.value = current.copy(selectedBatchIndex = index)
        }
    }

    fun inspect(idOverride: String? = null) {
        val targetId = (idOverride ?: _shipmentId.value).trim()
        if (targetId.isEmpty()) return
        if (idOverride != null) _shipmentId.value = idOverride

        viewModelScope.launch {
            _uiState.value = InspectUiState.Loading
            _selectedBatchIndex.value = -1
            val result = repository.inspectShipment(targetId)
            result.fold(
                onSuccess = { (onChain, telemetry, report) ->
                    val allRecords = telemetry.batches?.flatMap { it.records ?: emptyList() } ?: emptyList()
                    _uiState.value = InspectUiState.Success(
                        onChain = onChain,
                        telemetry = telemetry,
                        records = allRecords,
                        report = report,
                        selectedBatchIndex = -1
                    )
                },
                onFailure = { e ->
                    val msg = e.message ?: "Unknown error"
                    if (msg.contains("not found", ignoreCase = true) ||
                        msg.contains("not certified", ignoreCase = true)
                    ) {
                        _uiState.value = InspectUiState.NotFound(targetId)
                    } else {
                        _uiState.value = InspectUiState.Error(msg)
                    }
                }
            )
        }
    }

    fun reset() {
        _uiState.value = InspectUiState.Idle
        _shipmentId.value = ""
        _selectedBatchIndex.value = -1
    }
}
