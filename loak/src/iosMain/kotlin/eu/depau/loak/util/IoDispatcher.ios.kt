package eu.depau.loak.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

// `Dispatchers.IO` is internal on Native in coroutines 1.11; Default is a
// background pool there anyway and is public on every target.
internal actual val IoDispatcher: CoroutineDispatcher = Dispatchers.Default
