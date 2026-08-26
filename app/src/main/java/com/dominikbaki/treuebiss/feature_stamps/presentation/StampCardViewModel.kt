package com.dominikbaki.treuebiss.feature_stamps.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dominikbaki.treuebiss.core.domain.models.Stamp
import com.dominikbaki.treuebiss.core.domain.models.Tenant
import com.dominikbaki.treuebiss.core.domain.models.Voucher
import com.dominikbaki.treuebiss.core.domain.repository.StampRepository
import com.dominikbaki.treuebiss.core.domain.repository.TenantRepository
import com.dominikbaki.treuebiss.core.domain.repository.VoucherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import javax.inject.Inject
import kotlin.time.Duration.Companion.days

/** Zustand der Stempelkarte. Die Kartengröße kommt vom Betrieb. */
data class StampCardUiState(
    val stampCount: Int = 0,
    val stampsPerCard: Int = Tenant.DEFAULT_STAMPS_PER_CARD,
    val isAddingStamp: Boolean = false
) {
    val isComplete: Boolean get() = stampCount >= stampsPerCard
}

@HiltViewModel
class StampCardViewModel @Inject constructor(
    private val stampRepository: StampRepository,
    private val voucherRepository: VoucherRepository,
    private val tenantRepository: TenantRepository
) : ViewModel() {

    private companion object {
        /**
         * Kurze Pause, damit die volle Karte und die Erfolgsmeldung sichtbar
         * sind, bevor die Karte zurückgesetzt wird.
         */
        const val CARD_COMPLETE_VISIBLE_MS = 1_200L
    }

    /** Verhindert, dass ein zweiter Klick startet, während der erste noch läuft. */
    private val _isAddingStamp = MutableStateFlow(false)

    val uiState: StateFlow<StampCardUiState> = combine(
        stampRepository.observeStamps(),
        tenantRepository.observeActiveTenant(),
        _isAddingStamp
    ) { stamps, tenant, isAdding ->
        StampCardUiState(
            stampCount = stamps.size,
            stampsPerCard = tenant.stampsPerCard,
            isAddingStamp = isAdding
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StampCardUiState()
    )

    // Signalisiert der UI einmalig, dass ein neuer Gutschein erstellt wurde
    private val _voucherCreatedEvent = MutableSharedFlow<String>()
    val voucherCreatedEvent: SharedFlow<String> = _voucherCreatedEvent.asSharedFlow()

    fun onAddStampClicked() {
        if (_isAddingStamp.value) return
        _isAddingStamp.value = true

        viewModelScope.launch {
            try {
                val tenant = tenantRepository.observeActiveTenant().first()

                // Die neue Anzahl kommt aus derselben Transaktion wie das
                // Einfügen - nicht aus dem zuletzt beobachteten UI-Stand.
                val newStampCount = stampRepository.addStamp(
                    Stamp(timestamp = Clock.System.now(), tenantId = tenant.id)
                )

                if (newStampCount >= tenant.stampsPerCard) {
                    createVoucherAndResetCard(tenant)
                }
            } catch (e: Exception) {
                Log.e("StampCardViewModel", "Failed to add stamp", e)
            } finally {
                _isAddingStamp.value = false
            }
        }
    }

    private suspend fun createVoucherAndResetCard(tenant: Tenant) {
        val now = Clock.System.now()
        val newVoucher = Voucher(
            createdAt = now,
            creationDate = now.toEpochMilliseconds(),
            expiresAt = now.plus(tenant.voucherValidityDays.days).toEpochMilliseconds(),
            tenantId = tenant.id
        )
        voucherRepository.createVoucher(newVoucher)
        _voucherCreatedEvent.emit(newVoucher.id)

        // Erst die volle Karte zeigen, dann zurücksetzen.
        delay(CARD_COMPLETE_VISIBLE_MS)
        stampRepository.clearStamps()
    }
}
