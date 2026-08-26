package com.dominikbaki.treuebiss.feature_home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dominikbaki.treuebiss.core.domain.models.Offer
import com.dominikbaki.treuebiss.core.domain.models.Tenant
import com.dominikbaki.treuebiss.core.domain.repository.StampRepository
import com.dominikbaki.treuebiss.core.domain.repository.TenantRepository
import com.dominikbaki.treuebiss.core.domain.repository.VoucherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.Clock
import javax.inject.Inject

/** Bündelt die dynamischen Informationen für den HomeScreen. */
data class HomeUiState(
    val stampCount: Int = 0,
    val stampsPerCard: Int = Tenant.DEFAULT_STAMPS_PER_CARD,
    val voucherCount: Int = 0,
    /** Das aktuelle Angebot des Betriebs, oder null wenn keines hinterlegt ist. */
    val offer: Offer? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    stampRepository: StampRepository,
    voucherRepository: VoucherRepository,
    tenantRepository: TenantRepository
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> =
        combine(
            stampRepository.observeStamps(),
            voucherRepository.observeOpenVouchers(),
            tenantRepository.observeActiveTenant(),
            tenantRepository.observeOffers()
        ) { stamps, vouchers, tenant, offers ->
            val now = Clock.System.now()
            HomeUiState(
                stampCount = stamps.size,
                stampsPerCard = tenant.stampsPerCard,
                // Abgelaufene Gutscheine sind nicht mehr einlösbar und
                // dürfen die angezeigte Anzahl nicht aufblähen.
                voucherCount = vouchers.count { it.isRedeemableAt(now) },
                offer = offers.firstOrNull()
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HomeUiState()
        )
}
