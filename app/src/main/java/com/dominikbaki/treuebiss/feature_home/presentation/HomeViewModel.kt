package com.dominikbaki.treuebiss.feature_home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dominikbaki.treuebiss.core.domain.repository.StampRepository
import com.dominikbaki.treuebiss.core.domain.repository.VoucherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

// Ein Daten-Container, der alle dynamischen Informationen für den HomeScreen bündelt.
data class HomeUiState(
    val stampCount: Int = 0,
    val voucherCount: Int = 0,
    val dailySpecial: DailySpecial? = null, // Vorerst optional
    val isLoading: Boolean = true
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val stampRepository: StampRepository,
    private val voucherRepository: VoucherRepository
) : ViewModel() {

    // Wir kombinieren die Live-Daten aus beiden Repositories in einen einzigen State.
    val uiState: StateFlow<HomeUiState> =
        combine(
            stampRepository.observeStamps(),
            voucherRepository.observeAll(includeRedeemed = false) // Nur die aktiven Gutscheine
        ) { stamps, vouchers ->
            HomeUiState(
                stampCount = stamps.size,
                voucherCount = vouchers.size,
                dailySpecial = DailySpecial( // Dummy-Daten für das Tagesangebot
                    title = "Unser Dinkel-Kracher",
                    description = "Heute frisch aus dem Ofen, nur 3,50€!",
                    imageUrl = null
                ),
                isLoading = false
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HomeUiState() // Startet mit Ladezustand
        )
}
