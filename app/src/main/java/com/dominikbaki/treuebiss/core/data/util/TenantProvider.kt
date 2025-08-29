package com.dominikbaki.treuebiss.core.data.util

// Platzhalter für Provider
interface TenantProvider { suspend fun get(): String }