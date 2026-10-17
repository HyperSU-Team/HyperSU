package com.sukisu.ultra.data.registry

/**
 * Where the module registry lives, and the shape of the paths under it.
 *
 * The registry moved to a versioned API. The old flat documents - `modules.json` at the site root
 * and `module/<id>.json` beside it - are gone and answer 404, which is what left the module
 * repository screen with nothing but a fetch error. The entry point is now
 * `api/v1/index.json`, which names the list document; every path in this file is relative to the
 * site root and is resolved against [BASE_URL].
 */
object RegistryUrls {

    /** Site root. Every path below is appended to it. */
    const val BASE_URL = "https://modules.kernelsu.org/"

    /** The index that names the current list document. */
    const val INDEX_PATH = "api/v1/index.json"

    /**
     * The list document used when the index cannot be read.
     *
     * The index only exists to point at this, so falling back to it costs nothing and keeps the
     * screen working if the index is momentarily unavailable.
     */
    const val FALLBACK_MODULES_PATH = "api/v1/modules.json"

    /** Resolves a registry-relative path against [BASE_URL]. */
    fun resolve(path: String): String = BASE_URL + path.trimStart('/')

    /** Whether [url] is a document on the registry itself, rather than somewhere else. */
    fun isRegistryUrl(url: String): Boolean = url.startsWith(BASE_URL)
}
