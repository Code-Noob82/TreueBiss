package com.dominikbaki.treuebiss.feature_vouchers.presentation

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dominikbaki.treuebiss.R
import com.dominikbaki.treuebiss.core.domain.models.InvalidRedeemCodeException
import com.dominikbaki.treuebiss.core.domain.models.RedeemNotConfiguredException
import com.dominikbaki.treuebiss.core.domain.models.Voucher
import com.dominikbaki.treuebiss.core.domain.models.VoucherNotRedeemableException
import com.dominikbaki.treuebiss.core.domain.repository.TenantRepository
import com.dominikbaki.treuebiss.core.domain.repository.VoucherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import javax.inject.Inject

/** Einmalige Meldungen an die UI. */
sealed interface VoucherEvent {
    data object Redeemed : VoucherEvent
    data class Failed(@StringRes val messageRes: Int) : VoucherEvent
}

@HiltViewModel
class VoucherViewModel @Inject constructor(
    private val repository: VoucherRepository,
    tenantRepository: TenantRepository
): ViewModel() {

    /** Verlangt der Betrieb einen Code beim Einlösen? */
    val requiresRedeemCode: StateFlow<Boolean> =
        tenantRepository.observeActiveTenant()
            .map { it.requiresRedeemCode }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = false
            )

    val vouchers: StateFlow<List<Voucher>> = repository.observeOpenVouchers()
        // Einlösbare Gutscheine zuerst, abgelaufene ans Ende der Liste.
        .map { vouchers ->
            val now = Clock.System.now()
            vouchers.sortedBy { it.isExpiredAt(now) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    private val _events = MutableSharedFlow<VoucherEvent>()
    val events: SharedFlow<VoucherEvent> = _events.asSharedFlow()

    private val _isRedeeming = MutableStateFlow(false)
    val isRedeeming: StateFlow<Boolean> = _isRedeeming.asStateFlow()

    /**
     * Löst einen Gutschein gegen den Einlöse-Code des Betriebs ein.
     * Der Code kommt vom Personal an der Kasse.
     */
    fun onRedeemConfirmed(voucherId: String, code: String? = null) {
        if (_isRedeeming.value) return
        _isRedeeming.value = true

        viewModelScope.launch {
            try {
                repository.redeemVoucher(voucherId, code)
                _events.emit(VoucherEvent.Redeemed)
            } catch (e: InvalidRedeemCodeException) {
                Log.w("VoucherViewModel", "Wrong redeem code", e)
                _events.emit(VoucherEvent.Failed(R.string.voucher_error_wrong_code))
            } catch (e: VoucherNotRedeemableException) {
                Log.w("VoucherViewModel", "Voucher not redeemable", e)
                _events.emit(VoucherEvent.Failed(R.string.voucher_error_not_redeemable))
            } catch (e: RedeemNotConfiguredException) {
                Log.e("VoucherViewModel", "Tenant requires a code but has none", e)
                _events.emit(VoucherEvent.Failed(R.string.voucher_error_not_configured))
            } catch (e: Exception) {
                Log.e("VoucherViewModel", "Redeeming failed", e)
                _events.emit(VoucherEvent.Failed(R.string.voucher_error_offline))
            } finally {
                _isRedeeming.value = false
            }
        }
    }
}