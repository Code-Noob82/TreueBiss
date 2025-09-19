package com.dominikbaki.treuebiss.feature_stamps.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dominikbaki.treuebiss.core.domain.repository.StampRepository
import com.dominikbaki.treuebiss.core.domain.repository.VoucherRepository
import com.dominikbaki.treuebiss.core.domain.models.Stamp
import com.dominikbaki.treuebiss.core.domain.models.Voucher
import com.dominikbaki.treuebiss.core.presentation.MainViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import javax.inject.Inject
import kotlin.time.Duration.Companion.days

@HiltViewModel
class StampCardViewModel @Inject constructor(
    private val stampRepository: StampRepository,
    private val voucherRepository: VoucherRepository
) : ViewModel() {
    // Beobachtet alle Stempel in Echtzeit
    val stamps = stampRepository.observeStamps()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Signalisiert der UI einmalig, dass ein neuer Gutschein erstellt wurde
    private val _voucherCreatedEvent = MutableSharedFlow<String>()
    val voucherCreatedEvent: SharedFlow<String> = _voucherCreatedEvent.asSharedFlow()

    fun onAddStampClicked() {
        viewModelScope.launch {
            val currentStampCount = stamps.value.size
            val newStampCount = currentStampCount + 1
            // Stempel hinzufügen
            val newStamp = Stamp(
                timestamp = Clock.System.now()
            )
            stampRepository.addStamp(newStamp)
            // Alle 10 Stempel → Gutschein erstellen
            if (newStampCount > 0 && newStampCount % 10 == 0) {
                val currentTime = Clock.System.now()
                val expiryTime = currentTime.plus(90.days) // 90 Tage in die Zukunft

                val newVoucher = Voucher(
                    createdAt = currentTime,
                    creationDate = currentTime.toEpochMilliseconds(),
                    expiresAt = expiryTime.toEpochMilliseconds()
                )
                voucherRepository.createVoucher(newVoucher) // Erstellt einen neuen Gutschein
                // UI informieren
                _voucherCreatedEvent.emit(newVoucher.id) // Signalisiert die UI, dass ein Gutschein erstellt wurde
                // Karte zurücksetzen
                stampRepository.clearStamps()
            }
        }
    }
}