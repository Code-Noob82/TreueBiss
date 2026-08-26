package com.dominikbaki.treuebiss.feature_home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dominikbaki.treuebiss.R
import com.dominikbaki.treuebiss.core.domain.repository.StampRepository
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
    val voucherCount: Int = 0,
    val dailySpecial: DailySpecial? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    stampRepository: StampRepository,
    voucherRepository: VoucherRepository
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> =
        combine(
            stampRepository.observeStamps(),
            voucherRepository.observeOpenVouchers()
        ) { stamps, vouchers ->
            val now = Clock.System.now()
            HomeUiState(
                stampCount = stamps.size,
                // Abgelaufene Gutscheine sind nicht mehr einlösbar und
                // dürfen die angezeigte Anzahl nicht aufblähen.
                voucherCount = vouchers.count { it.isRedeemableAt(now) },
                dailySpecial = DailySpecial( // Dummy-Daten für das Tagesangebot
                    titleRes = R.string.home_daily_special_title,
                    descriptionRes = R.string.home_daily_special_description,
                    imageUrl = null
                )
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HomeUiState()
        )
}
