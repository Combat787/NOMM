package com.combat.nomm

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import nuclearoptionmodmanager.composeapp.generated.resources.Res
import nuclearoptionmodmanager.composeapp.generated.resources.close_24px
import nuclearoptionmodmanager.composeapp.generated.resources.search_24px
import org.jetbrains.compose.resources.painterResource
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

fun <T> List<T>.sortFilterByQuery(
    query: String,
    minSimilarity: Double = 0.25,
    block: (T, String) -> Pair<T, Double>
): List<T> {
    if (query.isBlank()) return this

    return this.mapNotNull {
        val result = block(it, query)
        if (result.second >= minSimilarity) result else null
    }
        .sortedByDescending { it.second }
        .map { it.first }
}

fun fuzzyPowerScore(query: String, target: String): Double {
    if (query.isEmpty()) return 1.0
    val q = query.lowercase()
    val t = target.lowercase()
    if (t == q) return 1.0
    if (t.startsWith(q)) return 0.9

    val qLen = q.length
    val tLen = t.length

    var anyWordStarts = false
    val acronymBuilder = StringBuilder()
    var nextIsStart = true

    for (char in t) {
        if (char == ' ' || char == '-' || char == '_' || char == '.') {
            nextIsStart = true
        } else if (nextIsStart) {
            acronymBuilder.append(char)
            if (t.startsWith(q, t.indexOf(char))) anyWordStarts = true
            nextIsStart = false
        }
    }

    if (anyWordStarts) return 0.85
    if (acronymBuilder.toString().contains(q)) return 0.8

    var sequenceIndex = 0
    for (i in 0 until tLen) {
        if (sequenceIndex < qLen && t[i] == q[sequenceIndex]) {
            sequenceIndex++
        }
    }
    if (sequenceIndex == qLen) return 0.7
    if (t.contains(q)) return 0.65

    val maxDist = (qLen * 0.4).toInt().coerceAtLeast(1)
    val dist = measureDamerauLevenshtein(q, t, maxDist)

    if (dist > maxDist) return 0.0
    return 0.5 * (1.0 - dist.toDouble() / max(qLen, tLen))
}

fun measureDamerauLevenshtein(source: CharSequence, target: CharSequence, threshold: Int = Int.MAX_VALUE): Int {
    val sLen = source.length
    val tLen = target.length

    if (sLen == 0) return tLen
    if (tLen == 0) return sLen
    if (abs(sLen - tLen) > threshold) return Int.MAX_VALUE

    var prevRow = IntArray(tLen + 1) { it }
    var currRow = IntArray(tLen + 1)
    var transRow = IntArray(tLen + 1)

    for (i in 1..sLen) {
        currRow[0] = i
        val sChar = source[i - 1]
        var minRowDist = i

        for (j in 1..tLen) {
            val tChar = target[j - 1]
            val cost = if (sChar == tChar) 0 else 1

            var dist = min(currRow[j - 1] + 1, prevRow[j] + 1)
            dist = min(dist, prevRow[j - 1] + cost)

            if (i > 1 && j > 1 && sChar == target[j - 2] && source[i - 2] == tChar) {
                dist = min(dist, transRow[j - 2] + cost)
            }

            currRow[j] = dist
            minRowDist = min(minRowDist, dist)
        }

        if (minRowDist > threshold) return Int.MAX_VALUE

        val temp = transRow
        transRow = prevRow
        prevRow = currRow
        currRow = temp
    }

    return prevRow[tLen]
}


@Composable
fun rememberFilteredExtensions(allMods: List<Extension>, searchQuery: String): List<Extension> {
    return rememberFilteredList(
        allItems = allMods,
        searchQuery = searchQuery,
        onBlankQuery = { items ->
            items.sortedByDescending { it.downloadCount }
        },
        onFilterQuery = { items, query ->
            items.sortFilterByQuery(query, minSimilarity = 0.3) { ext, q ->
                val nameScore = fuzzyPowerScore(q, ext.displayName)
                val idScore = fuzzyPowerScore(q, ext.id)
                val tagScore = ext.tags.maxOfOrNull { fuzzyPowerScore(q, it) } ?: 0.0
                val authorScore = ext.authors.maxOfOrNull { fuzzyPowerScore(q, it) } ?: 0.0

                val popularityFactor = log10((ext.downloadCount?.toDouble() ?: 1.0) + 1.0)
                val weightedScore =
                    ((nameScore * 5.0) + (idScore * 2.0) + (authorScore * 1.5) + tagScore) * (1.0 + (popularityFactor * 0.1))

                val lengthPenalty = if (ext.displayName.length > q.length * 3) 0.9 else 1.0
                ext to (weightedScore * lengthPenalty)
            }
        }
    )
}


@Composable
fun rememberFilteredServers(
    allServers: List<ServerEntry>,
    searchQuery: String,
    showUser: Boolean,
    showDedicated: Boolean,
    showPve: Boolean,
    showPvp: Boolean,
    sortBy: SortType
): List<ServerEntry> {
    return rememberFilteredList(
        allItems = allServers,
        searchQuery = searchQuery,
        showUser,
        showDedicated,
        showPve,
        showPvp,
        sortBy,
        onBlankQuery = { items ->
            var servers = items.filter {
                ((it.isLobby && showUser) || (!it.isLobby && showDedicated))
                        && ((it.missionData?.pvpType == "1" && showPvp) || (it.missionData?.pvpType == "2" && showPve))

            }

            servers = when (sortBy) {
                SortType.PING -> servers.sortedBy { it.info?.ping }
                SortType.PLAYERS -> servers.sortedByDescending { it.info?.players }
                SortType.DURATION -> servers.sortedByDescending { it.info?.timeLastPlayed }
            }

            servers.sortedBy { it.isFavorite }
        },
        onFilterQuery = { items, query ->
            items.sortFilterByQuery(query, minSimilarity = 0.3) { entry, q ->
                val nameScore = fuzzyPowerScore(q, entry.displayName)
                val idScore = entry.info?.map?.let { fuzzyPowerScore(q, it) } ?: 0.0

                val weightedScore = ((nameScore * 5.0) + (idScore * 2.0))

                val lengthPenalty = if (entry.displayName.length > q.length * 3) 0.9 else 1.0
                entry to (weightedScore * lengthPenalty)
            }
        }
    )
}


@Composable
fun <T> rememberFilteredList(
    allItems: List<T>,
    searchQuery: String,
    vararg keys: Any?,
    debounceTime: Duration = 250.milliseconds,
    onBlankQuery: (List<T>) -> List<T>,
    onFilterQuery: (List<T>, String) -> List<T>
): List<T> {
    var filteredItems by remember { mutableStateOf(onBlankQuery(allItems)) }
    var isInitialLoad by remember { mutableStateOf(true) }

    LaunchedEffect(searchQuery, allItems, *keys) {
        if (!isInitialLoad && searchQuery.isNotEmpty()) {
            delay(debounceTime)
        }

        val results = withContext(Dispatchers.Default) {
            if (searchQuery.isBlank()) {
                onBlankQuery(allItems)
            } else {
                onFilterQuery(allItems, searchQuery)
            }
        }
        filteredItems = results
        isInitialLoad = false
    }
    return filteredItems
}


@Composable
fun RowScope.SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier.Companion,
    placeholder: String = "Search mods...",
) {

    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .weight(1f),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.secondary,
            unfocusedContainerColor = MaterialTheme.colorScheme.secondary,

            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,


            cursorColor = MaterialTheme.colorScheme.onSecondary,
            focusedTextColor = MaterialTheme.colorScheme.onSecondary,
            unfocusedTextColor = MaterialTheme.colorScheme.onSecondary,
        ),
        placeholder = {
            Text(
                placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondary
            )
        },
        leadingIcon = {
            Icon(
                painterResource(Res.drawable.search_24px),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondary,
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        painterResource(Res.drawable.close_24px),
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.onSecondary
                    )
                }
            }
        },
        shape = MaterialTheme.shapes.small,
        singleLine = true,
    )
}