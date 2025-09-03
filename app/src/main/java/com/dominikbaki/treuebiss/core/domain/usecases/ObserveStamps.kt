package com.dominikbaki.treuebiss.core.domain.usecases

import com.dominikbaki.treuebiss.feature_stamps.domain.model.Stamp
import com.dominikbaki.treuebiss.core.domain.repository.StampRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use Case zum Beobachten aller gesammelten Stempel.
 *
 * Dieser Use Case gibt einfach den Flow aus dem Repository weiter.
 * In komplexeren Szenarien könnte hier noch zusätzliche Logik
 * (z.B. Filtern, Sortieren) hinzugefügt werden.
 */
class ObserveStamps @Inject constructor(
    private val stampRepository: StampRepository
) {
    operator fun invoke(): Flow<List<Stamp>> {
        return stampRepository.observeStamps()
    }
}