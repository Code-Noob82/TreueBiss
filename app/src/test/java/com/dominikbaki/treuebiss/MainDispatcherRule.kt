package com.dominikbaki.treuebiss

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Ersetzt `Dispatchers.Main` für Tests, damit `viewModelScope` funktioniert.
 *
 * Der Test muss `runTest(rule.dispatcher)` verwenden, damit Test und ViewModel
 * denselben [TestCoroutineScheduler] teilen - sonst schiebt `advanceUntilIdle()`
 * nur die Zeit des Tests vor und die ViewModel-Coroutinen laufen nie an.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    scheduler: TestCoroutineScheduler = TestCoroutineScheduler(),
    val dispatcher: TestDispatcher = StandardTestDispatcher(scheduler)
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}
