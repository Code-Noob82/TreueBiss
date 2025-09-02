package com.dominikbaki.treuebiss.feature_stamps.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dominikbaki.treuebiss.feature_stamps.domain.model.Stamp
import com.dominikbaki.treuebiss.feature_stamps.domain.repository.StampRepository
import com.dominikbaki.treuebiss.feature_vouchers.domain.model.Voucher
import com.dominikbaki.treuebiss.feature_vouchers.domain.repository.VoucherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StampCardViewModel @Inject constructor(
    private val stampRepository: StampRepository,
    private val voucherRepository: VoucherRepository // neue Abhängigkeit
) : ViewModel() {
    private val _stamps = stampRepository.observeStamps()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    val stamps: StateFlow<List<Stamp>> = _stamps

    // Ein Flow, um der UI einmalig mitzuteilen, dass ein Gutschein erstellt wurde.
    private val _voucherCreatedEvent = MutableSharedFlow<Unit>()
    val voucherCreatedEvent = _voucherCreatedEvent.asSharedFlow()

    fun onAddStampClicked() {
        viewModelScope.launch {
            val currentStampCount = stampRepository.count()
            // Nur hinzufügen, wenn die Karte noch nicht voll ist
            if (currentStampCount < 10) {
                val newStamp =
                    Stamp(id = 0, timestamp = System.currentTimeMillis(), isSynced = false)
                stampRepository.addStamp(newStamp)
                // Nur hinzufügen, wenn die Karte noch nicht voll ist
                if (currentStampCount + 1 == 10) {
                    voucherRepository.create(Voucher()) // Erstellt Gutschein mit Standard-Ablaufdatum
                    _voucherCreatedEvent.emit(Unit) // Signalisiert der UI, dass ein Gutschein erstellt wurde
                }
            }
        }
    }
}