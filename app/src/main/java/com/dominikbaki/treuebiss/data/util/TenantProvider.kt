package com.dominikbaki.treuebiss.data.util

// Platzhalter für Provider
interface TenantProvider { suspend fun get(): String }