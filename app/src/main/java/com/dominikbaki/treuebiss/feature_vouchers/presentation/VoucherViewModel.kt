package com.dominikbaki.treuebiss.feature_vouchers.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dominikbaki.treuebiss.core.domain.models.Voucher
import com.dominikbaki.treuebiss.core.domain.repository.VoucherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import javax.inject.Inject

@HiltViewModel
class VoucherViewModel @Inject constructor(
    private val repository: VoucherRepository
): ViewModel() {

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
    fun onRedeemClicked(voucherId: String) {
        viewModelScope.launch {
            repository.redeemVoucher(voucherId)
        }
    }
}