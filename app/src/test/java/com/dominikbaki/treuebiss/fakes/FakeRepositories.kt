package com.dominikbaki.treuebiss.fakes

import com.dominikbaki.treuebiss.core.domain.models.Stamp
import com.dominikbaki.treuebiss.core.domain.models.Voucher
import com.dominikbaki.treuebiss.core.domain.repository.AuthRepository
import com.dominikbaki.treuebiss.core.domain.repository.StampRepository
import com.dominikbaki.treuebiss.core.domain.repository.UserPreferencesRepository
import com.dominikbaki.treuebiss.core.domain.repository.VoucherRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Handgeschriebene Fakes statt eines Mocking-Frameworks: Sie sind hier kurz
 * genug und machen im Test sichtbar, welches Verhalten angenommen wird.
 */
class FakeAuthRepository(
    initiallyAuthenticated: Boolean = false
) : AuthRepository {

    private val authState = MutableStateFlow(initiallyAuthenticated)

    /** Wird von [signInAnonymously] geworfen, wenn gesetzt. */
    var signInError: Exception? = null

    /** Wenn true, meldet sich der Fake nie an (simuliert eine hängende Anmeldung). */
    var signInNeverCompletes: Boolean = false

    var signInCallCount: Int = 0
        private set

    override fun observeAuthState(): Flow<Boolean> = authState.asStateFlow()

    override suspend fun signInAnonymously() {
        signInCallCount++
        signInError?.let { throw it }
        if (signInNeverCompletes) {
            CompletableDeferred<Unit>().await() // wartet bis zum Timeout
        }
        authState.value = true
    }
}

class FakeStampRepository(
    initialStamps: List<Stamp> = emptyList()
) : StampRepository {

    private val stamps = MutableStateFlow(initialStamps)

    var restoreCallCount: Int = 0
        private set
    var clearCallCount: Int = 0
        private set

    val currentStamps: List<Stamp> get() = stamps.value

    override suspend fun addStamp(stamp: Stamp): Int {
        stamps.value = stamps.value + stamp
        return stamps.value.size
    }

    override fun observeStamps(): Flow<List<Stamp>> = stamps.asStateFlow()

    override suspend fun clearStamps() {
        clearCallCount++
        stamps.value = emptyList()
    }

    override suspend fun restoreFromRemote() {
        restoreCallCount++
    }
}

class FakeVoucherRepository(
    initialVouchers: List<Voucher> = emptyList()
) : VoucherRepository {

    private val vouchers = MutableStateFlow(initialVouchers)

    val created = mutableListOf<Voucher>()
    val redeemed = mutableListOf<String>()
    var restoreCallCount: Int = 0
        private set

    override suspend fun createVoucher(voucher: Voucher) {
        created += voucher
        vouchers.value = vouchers.value + voucher
    }

    override fun observeOpenVouchers(): Flow<List<Voucher>> = vouchers.asStateFlow()

    override suspend fun redeemVoucher(voucherId: String) {
        redeemed += voucherId
        vouchers.value = vouchers.value.filterNot { it.id == voucherId }
    }

    override suspend fun restoreFromRemote() {
        restoreCallCount++
    }
}

class FakeUserPreferencesRepository(
    onboardingCompleted: Boolean = true,
    remoteDataRestored: Boolean = true
) : UserPreferencesRepository {

    private val onboarding = MutableStateFlow(onboardingCompleted)
    private val restored = MutableStateFlow(remoteDataRestored)

    override val hasCompletedOnboarding: Flow<Boolean> = onboarding.asStateFlow()
    override val hasRestoredRemoteData: Flow<Boolean> = restored.asStateFlow()

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        onboarding.value = completed
    }

    override suspend fun setRemoteDataRestored(restoredValue: Boolean) {
        restored.value = restoredValue
    }
}
