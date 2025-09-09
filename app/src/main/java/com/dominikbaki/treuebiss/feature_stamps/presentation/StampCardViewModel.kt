package com.dominikbaki.treuebiss.feature_stamps.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dominikbaki.treuebiss.core.domain.repository.StampRepository
import com.dominikbaki.treuebiss.core.domain.repository.VoucherRepository
import com.dominikbaki.treuebiss.core.domain.models.Stamp
import com.dominikbaki.treuebiss.core.domain.models.Voucher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import javax.inject.Inject
import kotlin.time.Duration.Companion.days

@HiltViewModel
class StampCardViewModel @Inject constructor(
    private val stampRepository: StampRepository,
    private val voucherRepository: VoucherRepository // neue Abhängigkeit
) : ViewModel() {
    val stamps = stampRepository.observeStamps()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Ein Flow, um der UI einmalig mitzuteilen, dass ein Gutschein erstellt wurde.
    private val _voucherCreatedEvent = MutableSharedFlow<Unit>()
    val voucherCreatedEvent = _voucherCreatedEvent.asSharedFlow()

    fun onAddStampClicked() {
        viewModelScope.launch {
            // Effizientere Zählung direkt aus dem StateFlow.
            val currentStampCount = stamps.value.size
            val newStampCount = currentStampCount + 1

            // Füge immer zuerst den neuen Stempel hinzu.
            // Wir erstellen ein `Stamp`-Objekt, das nur die nötigen Informationen
            // enthält (siehe Datenmodell). Die `id` wird von Supabase generiert.
            val newStamp = Stamp(
                timestamp = Clock.System.now()
            )
            stampRepository.addStamp(newStamp)

            if (newStampCount > 0 && newStampCount % 10 == 0) {
                val currentTime = Clock.System.now()
                val expiryTime = currentTime.plus(90.days) // 90 Tage in die Zukunft

                val newVoucher = Voucher(
                    createdAt = currentTime,
                    creationDate = currentTime.toEpochMilliseconds(),
                    expiresAt = expiryTime.toEpochMilliseconds()
                )
                voucherRepository.createVoucher(newVoucher) // Erstellt einen neuen Gutschein
                _voucherCreatedEvent.emit(Unit) // Signalisiert die UI, dass ein Gutschein erstellt wurde

                stampRepository.clearStamps()
            }
        }
    }
}