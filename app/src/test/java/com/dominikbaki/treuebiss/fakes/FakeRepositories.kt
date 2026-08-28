package com.dominikbaki.treuebiss.fakes

import com.dominikbaki.treuebiss.core.domain.models.Offer
import com.dominikbaki.treuebiss.core.domain.models.InvalidRedeemCodeException
import com.dominikbaki.treuebiss.core.domain.models.ProofAlreadyUsedException
import com.dominikbaki.treuebiss.core.domain.models.VoucherNotRedeemableException
import com.dominikbaki.treuebiss.core.domain.models.Stamp
import com.dominikbaki.treuebiss.core.domain.models.StampIssueResult
import com.dominikbaki.treuebiss.core.domain.models.StampProof
import com.dominikbaki.treuebiss.core.domain.models.Tenant
import com.dominikbaki.treuebiss.core.domain.models.Voucher
import com.dominikbaki.treuebiss.core.domain.repository.AuthRepository
import com.dominikbaki.treuebiss.core.domain.repository.AuthStatus
import com.dominikbaki.treuebiss.core.domain.repository.StampRepository
import com.dominikbaki.treuebiss.core.domain.repository.TenantRepository
import com.dominikbaki.treuebiss.core.domain.repository.UserPreferencesRepository
import com.dominikbaki.treuebiss.core.domain.repository.VoucherRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Instant

/**
 * Handgeschriebene Fakes statt eines Mocking-Frameworks: Sie sind hier kurz
 * genug und machen im Test sichtbar, welches Verhalten angenommen wird.
 */
class FakeAuthRepository(
    initialStatus: AuthStatus = AuthStatus.NotAuthenticated
) : AuthRepository {

    private val status = MutableStateFlow(initialStatus)

    /** Wird von [signInAnonymously] geworfen, wenn gesetzt. */
    var signInError: Exception? = null

    /** Wenn true, kehrt die Anmeldung nie zurück (simuliert einen Hänger). */
    var signInNeverCompletes: Boolean = false

    var signInCallCount: Int = 0
        private set

    /**
     * Wird zu Beginn von [signInAnonymously] aufgerufen. Erlaubt dem Test,
     * den genauen Zeitpunkt festzuhalten - `currentTime` nach
     * `advanceUntilIdle()` taugt dafür nicht, weil andere Flows die
     * virtuelle Uhr weiterschieben.
     */
    var onSignInStarted: (() -> Unit)? = null

    override fun observeAuthStatus(): Flow<AuthStatus> = status.asStateFlow()

    override suspend fun signInAnonymously() {
        signInCallCount++
        onSignInStarted?.invoke()
        signInError?.let { throw it }
        if (signInNeverCompletes) {
            CompletableDeferred<Unit>().await() // wartet bis zum Timeout
        }
        status.value = AuthStatus.Authenticated
    }
}

const val TEST_TENANT_ID = "test-tenant"

class FakeTenantRepository(
    tenant: Tenant = Tenant.fallback(TEST_TENANT_ID)
) : TenantRepository {

    private val tenantFlow = MutableStateFlow(tenant)
    private val offersFlow = MutableStateFlow<List<Offer>>(emptyList())

    var syncCallCount: Int = 0
        private set
    var syncError: Exception? = null

    override val activeTenantId: String = tenant.id

    override fun observeActiveTenant(): Flow<Tenant> = tenantFlow.asStateFlow()

    override fun observeOffers(): Flow<List<Offer>> = offersFlow.asStateFlow()

    override suspend fun syncFromRemote() {
        syncCallCount++
        syncError?.let { throw it }
    }

    fun setTenant(tenant: Tenant) {
        tenantFlow.value = tenant
    }
}

class FakeStampRepository(
    initialStamps: List<Stamp> = emptyList(),
    private val stampsPerCard: Int = Tenant.DEFAULT_STAMPS_PER_CARD
) : StampRepository {

    private val stamps = MutableStateFlow(initialStamps)

    /** Nachweise, die der "Server" schon gesehen hat. */
    private val usedProofs = mutableSetOf<String>()

    /** Wird von [issueStamp] geworfen, wenn gesetzt - simuliert fehlende Verbindung. */
    var issueError: Exception? = null

    val currentStamps: List<Stamp> get() = stamps.value
    val issuedProofs: List<String> get() = usedProofs.toList()

    var restoreCallCount: Int = 0
        private set
    var clearCallCount: Int = 0
        private set

    override suspend fun issueStamp(proof: StampProof): StampIssueResult {
        issueError?.let { throw it }
        if (!usedProofs.add(proof.reference)) {
            throw ProofAlreadyUsedException(proof.reference)
        }

        val stamp = Stamp(
            timestamp = Instant.fromEpochMilliseconds(stamps.value.size + 1L),
            tenantId = TEST_TENANT_ID
        )
        stamps.value = stamps.value + stamp

        val count = stamps.value.size
        val voucher = if (count >= stampsPerCard) {
            Voucher(
                createdAt = Instant.fromEpochMilliseconds(0),
                creationDate = 0,
                expiresAt = 1,
                tenantId = TEST_TENANT_ID
            )
        } else {
            null
        }
        return StampIssueResult(stampId = stamp.id, stampCount = count, voucher = voucher)
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

    override suspend fun cacheVoucher(voucher: Voucher) {
        created += voucher
        vouchers.value = vouchers.value + voucher
    }

    override fun observeOpenVouchers(): Flow<List<Voucher>> = vouchers.asStateFlow()

    /** Der Code, den der "Server" akzeptiert. */
    var expectedCode: String = "1234"

    /** Wird von [redeemVoucher] geworfen, wenn gesetzt. */
    var redeemError: Exception? = null

    /** Verlangt der "Betrieb" einen Code? Spiegelt tenants.requires_redeem_code. */
    var requiresCode: Boolean = true

    override suspend fun redeemVoucher(voucherId: String, code: String?) {
        redeemError?.let { throw it }
        if (requiresCode && code != expectedCode) {
            throw InvalidRedeemCodeException("falscher Code")
        }
        if (vouchers.value.none { it.id == voucherId }) {
            throw VoucherNotRedeemableException("unbekannt oder bereits eingelöst")
        }
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
