package com.architectpsg.commandlibrary.feature.shell.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope

/**
 * Stands in for a ViewModel scope: a supervisor scope on the test scheduler whose work counts as
 * foreground, so advanceUntilIdle runs it (backgroundScope work is skipped by advanceUntilIdle).
 */
internal fun TestScope.ownerScope(): CoroutineScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
