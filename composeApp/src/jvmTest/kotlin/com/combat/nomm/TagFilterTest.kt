package com.combat.nomm

import kotlin.test.Test
import kotlin.test.assertEquals

class TagFilterTest {
    private fun extension(id: String, vararg tags: String): Extension = Extension(
        id = id,
        tags = tags.toList(),
        artifacts = emptyList()
    )

    @Test
    fun `normalizeTag folds case, whitespace and synonyms`() {
        assertEquals("qol", normalizeTag("QoL"))
        assertEquals("qol", normalizeTag("  qol  "))
        assertEquals("flavor", normalizeTag("Flavour"))
        assertEquals("weapon", normalizeTag("weapons"))
        assertEquals("weapon", normalizeTag("WEAPON"))
    }

    @Test
    fun `commonTagFilters keeps only tags used by at least two mods`() {
        val mods = listOf(
            extension("a", "qol"),
            extension("b", "qol"),
            extension("c", "aircraft")
        )
        val filters = mods.commonTagFilters()
        assertEquals(listOf("qol"), filters.map { it.tag })
        assertEquals(2, filters.single().count)
        assertEquals("Qol", filters.single().label)
    }

    @Test
    fun `commonTagFilters excludes tags from the exclusion list`() {
        val mods = listOf(
            extension("a", "mod", "qol"),
            extension("b", "MOD", "qol")
        )
        val filters = mods.commonTagFilters()
        assertEquals(listOf("qol"), filters.map { it.tag })
    }

    @Test
    fun `commonTagFilters merges case variants and counts distinct mods`() {
        val mods = listOf(
            extension("a", "qol", "QoL"),
            extension("b", "QOL")
        )
        val filters = mods.commonTagFilters()
        assertEquals(1, filters.size)
        assertEquals("qol", filters.single().tag)
        assertEquals(2, filters.single().count)
    }

    @Test
    fun `commonTagFilters merges synonyms`() {
        val mods = listOf(
            extension("a", "flavor"),
            extension("b", "flavour"),
            extension("c", "weapons"),
            extension("d", "weapon")
        )
        val filters = mods.commonTagFilters()
        assertEquals(listOf("flavor", "weapon"), filters.map { it.tag }.sorted())
        filters.forEach { assertEquals(2, it.count) }
    }

    @Test
    fun `commonTagFilters sorts by count descending and labels with the most common casing`() {
        val mods = listOf(
            extension("a", "qol", "Aircraft"),
            extension("b", "qol", "aircraft"),
            extension("c", "aircraft")
        )
        val filters = mods.commonTagFilters()
        assertEquals(listOf("aircraft", "qol"), filters.map { it.tag })
        assertEquals("Aircraft", filters.first().label)
        assertEquals(3, filters.first().count)
        assertEquals(2, filters.last().count)
    }

    @Test
    fun `filterByTags returns all mods when nothing is selected`() {
        val mods = listOf(extension("a", "qol"), extension("b", "hud"))
        assertEquals(mods, mods.filterByTags(emptySet()))
    }

    @Test
    fun `filterByTags matches any selected tag case-insensitively`() {
        val mods = listOf(
            extension("a", "QoL"),
            extension("b", "hud"),
            extension("c", "aircraft")
        )
        val filtered = mods.filterByTags(setOf("qol", "aircraft"))
        assertEquals(listOf("a", "c"), filtered.map { it.id })
    }

    @Test
    fun `filterByTags matches synonyms`() {
        val mods = listOf(
            extension("a", "flavour"),
            extension("b", "hud")
        )
        val filtered = mods.filterByTags(setOf("flavor"))
        assertEquals(listOf("a"), filtered.map { it.id })
    }
}
