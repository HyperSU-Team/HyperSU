package com.sukisu.ultra.ui.viewmodel

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import com.sukisu.ultra.Natives
import com.sukisu.ultra.R
import com.sukisu.ultra.data.model.Module
import com.sukisu.ultra.data.model.ModuleUpdateInfo
import com.sukisu.ultra.data.repository.ModuleRepository
import com.sukisu.ultra.data.repository.ModuleRepositoryImpl
import com.sukisu.ultra.data.repository.SettingsRepository
import com.sukisu.ultra.data.repository.SettingsRepositoryImpl
import com.sukisu.ultra.ksuApp
import com.sukisu.ultra.ui.component.SearchStatus
import com.sukisu.ultra.ui.screen.module.ModuleConfirmDialogState
import com.sukisu.ultra.ui.screen.module.ModuleConfirmRequest
import com.sukisu.ultra.ui.screen.module.ModuleEffect
import com.sukisu.ultra.ui.screen.module.ModuleUiState
import com.sukisu.ultra.ui.util.PinyinUtil
import com.sukisu.ultra.ui.util.hasMagisk
import com.sukisu.ultra.ui.util.module.fetchModuleDetail
import com.sukisu.ultra.ui.util.module.fetchReleaseDescriptionHtml
import okhttp3.Request
import java.text.Collator
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds
import com.sukisu.ultra.ui.util.toggleModule as toggleModuleUtil
import com.sukisu.ultra.ui.util.undoUninstallModule as undoUninstallModuleUtil
import com.sukisu.ultra.ui.util.uninstallModule as uninstallModuleUtil

class ModuleViewModel(
    private val repo: ModuleRepository = ModuleRepositoryImpl(),
    private val settingsRepo: SettingsRepository = SettingsRepositoryImpl()
) : ViewModel() {
    
    companion object {
        private const val TAG = "ModuleViewModel"
    }

    private data class ModuleUpdateSignature(
        val updateJson: String,
        val versionCode: Long,
        val enabled: Boolean,
        val update: Boolean,
        val remove: Boolean
    )

    private data class ModuleUpdateCache(
        val signature: ModuleUpdateSignature,
        val info: ModuleUpdateInfo
    )

    private val _uiState = MutableStateFlow(ModuleUiState())
    val uiState: StateFlow<ModuleUiState> = _uiState.asStateFlow()

    // One-shot UI events (toast/snackbar): buffered Channel, never dropped/duplicated/overwritten
    private val _moduleEvent = Channel<ModuleEffect>(Channel.BUFFERED)
    val moduleEvent: Flow<ModuleEffect> = _moduleEvent.receiveAsFlow()

    private val updateInfoMutex = Mutex()
    private var updateInfoCache: MutableMap<String, ModuleUpdateCache> = mutableMapOf()
    private val updateInfoInFlight = mutableSetOf<String>()
    private val searchQuery = MutableStateFlow("")

    private var fetchJob: Job? = null

    var isNeedRefresh = false
        private set

    init {
        viewModelScope.launchSearchQueryCollector(searchQuery, ::applySearchText)
    }

    fun markNeedRefresh() {
        isNeedRefresh = true
    }

    fun initializePreferences() {
        _uiState.update {
            it.copy(
                checkModuleUpdate = settingsRepo.checkModuleUpdate,
                sortEnabledFirst = settingsRepo.moduleSortEnabledFirst,
                sortActionFirst = settingsRepo.moduleSortActionFirst,
            )
        }
        updateModuleList()
    }

    fun toggleSortActionFirst() {
        val newValue = !_uiState.value.sortActionFirst
        settingsRepo.moduleSortActionFirst = newValue
        _uiState.update { it.copy(sortActionFirst = newValue) }
        updateModuleList()
    }

    fun toggleSortEnabledFirst() {
        val newValue = !_uiState.value.sortEnabledFirst
        settingsRepo.moduleSortEnabledFirst = newValue
        _uiState.update { it.copy(sortEnabledFirst = newValue) }
        updateModuleList()
    }

    fun refreshEnvironmentState() {
        viewModelScope.launch {
            val magiskInstalled = withContext(Dispatchers.IO) { hasMagisk() }
            val isSafeMode = Natives.isSafeMode
            _uiState.update {
                it.copy(
                    magiskInstalled = magiskInstalled,
                    isSafeMode = isSafeMode,
                )
            }
        }
    }

    fun updateSearchStatus(status: SearchStatus) {
        val previous = _uiState.value.searchStatus
        _uiState.update { it.copy(searchStatus = status) }
        if (previous.searchText != status.searchText) {
            searchQuery.value = status.searchText
        }
    }

    fun updateSearchText(text: String) {
        updateSearchStatus(_uiState.value.searchStatus.copy(searchText = text))
    }

    private fun filterModules(modules: List<Module>, text: String): List<Module> {
        if (text.isEmpty()) return emptyList()

        return modules.filter {
            it.id.contains(text, true) || it.name.contains(text, true) ||
                    it.description.contains(text, true) || it.author.contains(text, true) ||
                    PinyinUtil.toPinyin(it.name).contains(text, true)
        }
    }

    private suspend fun applySearchText(text: String) {
        _uiState.update {
            it.copy(
                searchStatus = it.searchStatus.copy(
                    resultStatus = searchLoadingStatusFor(text)
                )
            )
        }

        if (text.isEmpty()) {
            updateModuleList()
            return
        }

        val result = withContext(Dispatchers.IO) {
            val state = _uiState.value
            filterModules(state.modules, text).sortedWith(moduleComparator(state))
        }

        _uiState.update {
            it.copy(
                searchResults = result,
                searchStatus = it.searchStatus.copy(
                    resultStatus = searchResultStatusFor(text, result.isEmpty())
                )
            )
        }
    }

    /**
     * Rebuilds `moduleList` / `searchResults` off the main thread.
     *
     * Used by the settings that only re-sort; `loadModuleList` calls [recomputeModuleList] directly
     * because it already runs on the main thread and must publish everything in one state change.
     */
    private fun updateModuleList(resort: Boolean = true) {
        viewModelScope.launch {
            recomputeModuleList(resort)
        }
    }

    private fun recomputeModuleList(resort: Boolean = true) {
        val state = _uiState.value
        val searchText = state.searchStatus.searchText

        // Reuse the objects that did not change.
        //
        // `loadModuleList` builds a fresh Module for every module on disk, and Module is a data
        // class, so two reads of an unchanged module are equal but are different instances.
        // Compose compares by equality so it does skip them - but the list itself was rebuilt,
        // which re-ran the sort, the search filter, and the banner state keyed on the module.
        // Mapping unchanged entries back onto the objects already in the list keeps the list
        // structurally identical when nothing changed, so a refresh that finds no difference
        // produces no visible work at all.
        val previousById = state.moduleList.associateBy { it.id }
        val reconciled = state.modules.map { fresh ->
            val old = previousById[fresh.id]
            if (old == fresh) old else fresh
        }

        val shorted = if (resort || state.moduleList.isEmpty()) {
            reconciled.sortedWith(moduleComparator(state))
        } else {
            // Order-preserving reload: keep order, refresh data, drop removed, append new (no re-sort on toggle/uninstall)
            val byId = reconciled.associateBy { it.id }
            val existingIds = state.moduleList.mapTo(HashSet()) { it.id }
            state.moduleList.mapNotNull { byId[it.id] } + reconciled.filter { it.id !in existingIds }
        }
        val searchResults = filterModules(shorted, searchText)

        _uiState.update {
            it.copy(
                moduleList = shorted,
                searchResults = searchResults,
                searchStatus = it.searchStatus.copy(
                    resultStatus = searchResultStatusFor(searchText, searchResults.isEmpty())
                )
            )
        }
    }

    private fun moduleComparator(state: ModuleUiState): Comparator<Module> {
        return compareBy<Module>(
            {
                val executable = it.hasWebUi || it.hasActionScript
                when {
                    it.metamodule && it.enabled -> 0
                    state.sortEnabledFirst && state.sortActionFirst -> when {
                        it.enabled && executable -> 1
                        it.enabled -> 2
                        executable -> 3
                        else -> 4
                    }

                    state.sortEnabledFirst && !state.sortActionFirst -> if (it.enabled) 1 else 2
                    !state.sortEnabledFirst && state.sortActionFirst -> if (executable) 1 else 2
                    else -> 1
                }
            },
            { if (state.sortEnabledFirst) !it.enabled else 0 },
            { if (state.sortActionFirst) !(it.hasWebUi || it.hasActionScript) else 0 },
        ).thenBy(Collator.getInstance(Locale.getDefault()), Module::id)
    }

    suspend fun loadModuleList(resort: Boolean = true) {
        val parsedModules = withContext(Dispatchers.IO) {
            repo.getModules().getOrElse {
                Log.e(TAG, "fetchModuleList: ", it)
                emptyList()
            }
        }

        withContext(Dispatchers.Main) {
            // `modules` is written once, together with the derived lists.
            //
            // It used to be published first and the derived lists filled in by a second coroutine,
            // so every refresh went through an intermediate state where `modules` was new and
            // `moduleList` still old - one extra recomposition of the whole list, per refresh.
            _uiState.update { it.copy(modules = parsedModules) }
            recomputeModuleList(resort)
            isNeedRefresh = false
        }
    }

    fun fetchModuleList(checkUpdate: Boolean = false, resort: Boolean = true) {
        fetchJob?.cancel()
        _uiState.update { it.copy(isRefreshing = true) }
        fetchJob = viewModelScope.launch {
            try {
                val start = SystemClock.elapsedRealtime()

                loadModuleList(resort)

                if (checkUpdate) syncModuleUpdateInfo(_uiState.value.modules)

                Log.i(TAG, "load cost: ${SystemClock.elapsedRealtime() - start}, modules: ${_uiState.value.modules}")
            } finally {
                _uiState.update { it.copy(isRefreshing = false, hasLoaded = true) }
            }
        }
    }

    private fun Module.toSignature(): ModuleUpdateSignature {
        return ModuleUpdateSignature(
            updateJson = updateJson,
            versionCode = versionCode,
            enabled = enabled,
            update = update,
            remove = remove
        )
    }

    suspend fun syncModuleUpdateInfo(modules: List<Module>) {
        if (!_uiState.value.checkModuleUpdate) return

        val modulesToFetch = mutableListOf<Triple<String, Module, ModuleUpdateSignature>>()
        val removedIds = mutableSetOf<String>()

        updateInfoMutex.withLock {
            val ids = modules.map { it.id }.toSet()
            updateInfoCache.keys.filter { it !in ids }.forEach { removedId ->
                removedIds += removedId
                updateInfoCache.remove(removedId)
                updateInfoInFlight.remove(removedId)
            }

            modules.forEach { module ->
                val signature = module.toSignature()
                val cached = updateInfoCache[module.id]
                if ((cached == null || cached.signature != signature) && updateInfoInFlight.add(module.id)) {
                    modulesToFetch += Triple(module.id, module, signature)
                }
            }
        }

        val fetchedEntries = coroutineScope {
            modulesToFetch.map { (id, module, signature) ->
                async {
                    val info = withTimeoutOrNull(5_000L.milliseconds) {
                        withContext(Dispatchers.IO) { checkUpdate(module) }
                    } ?: ModuleUpdateInfo.Empty
                    id to ModuleUpdateCache(signature, info)
                }
            }.awaitAll()
        }

        val changedEntries = mutableListOf<Pair<String, ModuleUpdateInfo>>()
        updateInfoMutex.withLock {
            fetchedEntries.forEach { (id, entry) ->
                val existing = updateInfoCache[id]
                if (existing == null || existing.signature != entry.signature || existing.info != entry.info) {
                    updateInfoCache[id] = entry
                    changedEntries += id to entry.info
                }
                updateInfoInFlight.remove(id)
            }
        }

        if (removedIds.isEmpty() && changedEntries.isEmpty()) {
            return
        }

        withContext(Dispatchers.Main) {
            _uiState.update { state ->
                val newMap = state.updateInfo.toMutableMap()
                removedIds.forEach { newMap.remove(it) }
                changedEntries.forEach { (id, info) ->
                    newMap[id] = info
                }
                state.copy(updateInfo = newMap)
            }
        }
    }

    fun requestUpdateConfirmation(module: Module, updateInfo: ModuleUpdateInfo) {
        val res = ksuApp.resources
        // Marked before the dialog is built so the card can show a spinner on the very next frame.
        _uiState.update {
            it.copy(preparingUpdateModuleIds = it.preparingUpdateModuleIds + module.id)
        }
        // The dialog is shown immediately, without the changelog, and the notes are filled in when
        // they arrive.
        //
        // It used to be built and then shown, which meant the network round trips that fetch the
        // changelog - one to the registry, sometimes a second to the raw URL - all happened between
        // the tap and anything appearing on screen. That is what made updating a module feel slow:
        // the tap was waiting on the network, not on an animation.
        _uiState.update {
            it.copy(
                confirmDialogState = ModuleConfirmDialogState(
                    request = ModuleConfirmRequest.Update(
                        module = module,
                        downloadUrl = updateInfo.downloadUrl,
                        fileName = "${module.name}-${updateInfo.version}.zip",
                    ),
                    title = res.getString(R.string.module_update),
                    // Never blank: the dialog skips its whole content area for a blank string, so
                    // an empty placeholder left the user with a title and two buttons and no word
                    // about which module or version was being installed. The changelog replaces
                    // this once it arrives.
                    content = res.getString(R.string.module_start_downloading).format(module.name),
                    markdown = false,
                    html = false,
                    confirm = res.getString(R.string.module_update),
                    dismiss = res.getString(android.R.string.cancel),
                ),
            )
        }
        // The spinner stays until the dialog is dismissed, not until it appears. Clearing it here
        // made it last a single frame - the dialog covers the card, so the user never saw the
        // feedback that the tap had been received.
        viewModelScope.launch {
            val changelog = runCatching { fetchUpdateChangelog(module, updateInfo) }.getOrNull()
            if (changelog.isNullOrBlank()) return@launch
            // Only fills in the notes if the same dialog is still open, so a slow fetch cannot
            // overwrite a dialog the user has since dismissed or replaced.
            _uiState.update { state ->
                val current = state.confirmDialogState
                if (current?.request is ModuleConfirmRequest.Update &&
                    (current.request as ModuleConfirmRequest.Update).module.id == module.id
                ) {
                    state.copy(
                        confirmDialogState = current.copy(
                            title = res.getString(R.string.module_changelog),
                            content = changelog,
                            html = true,
                        )
                    )
                } else {
                    state
                }
            }
        }
    }

    /** Clears the spinner for a module whose update dialog has been closed. */
    private fun clearPreparingUpdate(moduleId: String) {
        _uiState.update {
            it.copy(preparingUpdateModuleIds = it.preparingUpdateModuleIds - moduleId)
        }
    }

    fun requestUninstallConfirmation(module: Module) {
        val res = ksuApp.resources
        _uiState.update {
            it.copy(
                confirmDialogState = ModuleConfirmDialogState(
                    request = ModuleConfirmRequest.Uninstall(module),
                    title = res.getString(R.string.module),
                    content = (if (module.metamodule) res.getString(R.string.metamodule_uninstall_confirm) else res.getString(R.string.module_uninstall_confirm)).format(
                        module.name
                    ),
                    confirm = res.getString(R.string.uninstall),
                    dismiss = res.getString(android.R.string.cancel),
                )
            )
        }
    }

    fun dismissConfirmRequest() {
        // The update chip's spinner is cleared here rather than when the dialog opened, so the
        // feedback survives as long as the dialog does.
        (_uiState.value.confirmDialogState?.request as? ModuleConfirmRequest.Update)
            ?.module?.id
            ?.let { clearPreparingUpdate(it) }
        _uiState.update { it.copy(confirmDialogState = null) }
    }

    fun emitEffect(effect: ModuleEffect) {
        _moduleEvent.trySend(effect)
    }

    fun toggleModule(module: Module) {
        viewModelScope.launch {
            val res = ksuApp.resources
            val success = withContext(Dispatchers.IO) {
                toggleModuleUtil(module.id, !module.enabled)
            }
            if (success) {
                fetchModuleList(checkUpdate = true, resort = false)
                emitEffect(ModuleEffect.SnackBar(res.getString(R.string.reboot_to_apply)))
            } else {
                val message = if (module.enabled) R.string.module_failed_to_disable else R.string.module_failed_to_enable
                emitEffect(ModuleEffect.SnackBar(res.getString(message).format(module.name)))
            }
        }
    }

    fun uninstallModule(module: Module) {
        viewModelScope.launch {
            val res = ksuApp.resources
            val success = withContext(Dispatchers.IO) {
                uninstallModuleUtil(module.id)
            }
            if (success) {
                fetchModuleList(checkUpdate = true, resort = false)
            }
            _uiState.update { it.copy(confirmDialogState = null) }
            emitEffect(
                ModuleEffect.SnackBar(
                    res.getString(
                        if (success) R.string.module_uninstall_success else R.string.module_uninstall_failed
                    ).format(module.name)
                )
            )
        }
    }

    fun undoUninstallModule(module: Module) {
        viewModelScope.launch {
            val res = ksuApp.resources
            val success = withContext(Dispatchers.IO) {
                undoUninstallModuleUtil(module.id)
            }
            if (success) {
                fetchModuleList(checkUpdate = true, resort = false)
            }
            emitEffect(
                ModuleEffect.SnackBar(
                    res.getString(
                        if (success) R.string.module_undo_uninstall_success else R.string.module_undo_uninstall_failed
                    ).format(module.name)
                )
            )
        }
    }

    /**
     * The notes to show for an update, as HTML or markdown.
     *
     * Kept separate from the dialog itself so the dialog can appear first and this can run after.
     * The three sources are tried in order of how much they cost: the registry's own changelog for
     * the release, the URL the module declares, and finally the release notes the registry holds
     * for the module's newest tag.
     */
    private suspend fun fetchUpdateChangelog(
        module: Module,
        updateInfo: ModuleUpdateInfo,
    ): String? = withContext(Dispatchers.IO) {
        val changelogUrl = updateInfo.changelog

        if (changelogUrl.startsWith("#") && changelogUrl.contains('@')) {
            val parts = changelogUrl.substring(1).split('@', limit = 2)
            if (parts.size == 2) {
                fetchReleaseDescriptionHtml(parts[0], parts[1])?.let { return@withContext it }
            }
        }

        if (changelogUrl.isNotBlank()) {
            runCatching {
                ksuApp.okhttpClient.newCall(
                    Request.Builder().url(changelogUrl).build()
                ).execute().body.string()
            }.getOrNull()?.takeIf { it.isNotBlank() }?.let { return@withContext it }
        }

        runCatching {
            val latestTag = fetchModuleDetail(module.id)?.latestTag.orEmpty()
            if (latestTag.isNotBlank()) fetchReleaseDescriptionHtml(module.id, latestTag) else null
        }.getOrNull()
    }

    private suspend fun checkUpdate(m: Module): ModuleUpdateInfo {
        return repo.checkUpdate(m).getOrDefault(ModuleUpdateInfo.Empty)
    }
}
