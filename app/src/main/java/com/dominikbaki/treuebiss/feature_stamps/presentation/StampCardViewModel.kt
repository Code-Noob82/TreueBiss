package com.dominikbaki.treuebiss.feature_stamps.presentation

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dominikbaki.treuebiss.BuildConfig
import com.dominikbaki.treuebiss.R
import com.dominikbaki.treuebiss.core.domain.models.ProofAlreadyUsedException
import com.dominikbaki.treuebiss.core.domain.models.StampProof
import com.dominikbaki.treuebiss.core.domain.models.Tenant
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** Zustand der Stempelkarte. Die Kartengröße kommt vom Betrieb. */
data class StampCardUiState(
    val stampCount: Int = 0,
    val stampsPerCard: Int = Tenant.DEFAULT_STAMPS_PER_CARD,
    val isIssuing: Boolean = false,
    /** Nur in Debug-Builds: der Knopf, der ohne Beleg stempelt. */
    val isDemoEnabled: Boolean = BuildConfig.DEBUG
) {
    val isComplete: Boolean get() = stampCount >= stampsPerCard
}

/** Einmalige Meldungen an die UI. */
sealed interface StampCardEvent {
    data class VoucherCreated(val voucherId: String) : StampCardEvent
    data class Failed(@StringRes val messageRes: Int) : StampCardEvent
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

    private val _isIssuing = MutableStateFlow(false)

    val uiState: StateFlow<StampCardUiState> = combine(
        stampRepository.observeStamps(),
        tenantRepository.observeActiveTenant(),
        _isIssuing
    ) { stamps, tenant, isIssuing ->
        StampCardUiState(
            stampCount = stamps.size,
            stampsPerCard = tenant.stampsPerCard,
            isIssuing = isIssuing
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StampCardUiState()
    )

    private val _events = MutableSharedFlow<StampCardEvent>()
    val events: SharedFlow<StampCardEvent> = _events.asSharedFlow()

    /**
     * Vergibt einen Stempel gegen einen gescannten Kassenbon.
     *
     * @param proofReference Eindeutige Kennung vom Beleg (TSE-Transaktionsnummer).
     */
    fun onReceiptScanned(proofReference: String) {
        issue(StampProof(reference = proofReference, source = StampProof.Source.Receipt))
    }

    /**
     * Vergibt einen Stempel ohne Beleg. Nur für Debug-Builds gedacht - der
     * Nachweis ist eine zufällige Kennung und damit wertlos.
     */
    fun onDemoStampClicked() {
        if (!BuildConfig.DEBUG) return
        issue(StampProof(reference = "demo-${UUID.randomUUID()}", source = StampProof.Source.Demo))
    }

    private fun issue(proof: StampProof) {
        if (_isIssuing.value) return
        _isIssuing.value = true

        viewModelScope.launch {
            try {
                val result = stampRepository.issueStamp(proof)

                result.voucher?.let { voucher ->
                    voucherRepository.cacheVoucher(voucher)
                    _events.emit(StampCardEvent.VoucherCreated(voucher.id))

                    // Erst die volle Karte zeigen, dann zurücksetzen.
                    delay(CARD_COMPLETE_VISIBLE_MS)
                    stampRepository.clearStamps()
                }
            } catch (e: ProofAlreadyUsedException) {
                Log.w("StampCardViewModel", "Proof already used", e)
                _events.emit(StampCardEvent.Failed(R.string.stamp_error_proof_used))
            } catch (e: Exception) {
                // Ohne Verbindung ist keine Vergabe möglich: Nur der Server
                // kann prüfen, ob der Beleg echt und unbenutzt ist.
                Log.e("StampCardViewModel", "Failed to issue stamp", e)
                _events.emit(StampCardEvent.Failed(R.string.stamp_error_offline))
            } finally {
                _isIssuing.value = false
            }
        }
    }
}
