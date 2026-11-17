package com.dailyroutine.app

/** Coordinates callers that need the same request while it is still running. */
internal class InFlightRequestRegistry<K, V> {
    private val lock = Any()
    private val callbacksByKey = mutableMapOf<K, MutableList<(V) -> Unit>>()

    /** Returns true for the caller responsible for starting the request. */
    fun addOrJoin(key: K, callback: (V) -> Unit): Boolean = synchronized(lock) {
        val existingCallbacks = callbacksByKey[key]
        if (existingCallbacks != null) {
            existingCallbacks.add(callback)
            false
        } else {
            callbacksByKey[key] = mutableListOf(callback)
            true
        }
    }

    /** Completes the active request and removes it so a later call can retry. */
    fun complete(key: K, result: V) {
        val callbacks = synchronized(lock) { callbacksByKey.remove(key).orEmpty() }
        callbacks.forEach { callback -> runCatching { callback(result) } }
    }
}
