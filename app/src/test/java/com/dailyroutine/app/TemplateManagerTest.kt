package com.dailyroutine.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class TemplateManagerTest {
    private var templates = mutableListOf<MealTemplate>()
    private var ranges = mutableListOf<AppliedTemplateRange>()
    private val manager = TemplateManager<MealTemplate, Meal>(
        lock = Any(),
        readTemplates = { templates },
        writeTemplates = { templates = it.toMutableList() },
        readRanges = { ranges },
        writeRanges = { ranges = it.toMutableList() },
        copyWithName = { template, name -> template.copy(name = name) }
    )

    @Test
    fun createFromRange_copiesScheduledItemsAndRejectsInvalidOrEmptyRanges() {
        val source = Meal(id = 11, name = "Breakfast")
        val scheduled = mapOf("2026-03-01" to listOf(source))
        val created = manager.createFromRange(
            name = "  Week plan  ",
            startDate = "2026-03-01",
            endDate = "2026-03-02",
            allowEmpty = false,
            itemsForDate = { scheduled[it].orEmpty() },
            copyItem = { it.copy() },
            createTemplate = { name, duration, items -> MealTemplate("meal-id", name, duration, items) }
        )

        assertTrue(created)
        assertEquals("Week plan", templates.single().name)
        assertEquals(2, templates.single().durationDays)
        assertEquals(listOf("Breakfast"), templates.single().itemsByDayOffset[0]?.map { it.name })
        assertNotSame(source, templates.single().itemsByDayOffset[0]?.single())
        assertFalse(manager.createFromRange("Empty", "2026-03-01", "2026-03-02", false, { emptyList() }, { it.copy() }) { name, days, items ->
            MealTemplate(UUID.randomUUID().toString(), name, days, items)
        })
        assertFalse(manager.createFromRange("Bad date", "2026-03-03", "2026-03-01", true, { emptyList() }, { it.copy() }) { name, days, items ->
            MealTemplate(UUID.randomUUID().toString(), name, days, items)
        })
        assertEquals(1, templates.size)
    }

    @Test
    fun applyToRange_clonesItemsAndRejectsOverlapsAndRangesLongerThanTemplate() {
        val templateItems = mutableMapOf(0 to mutableListOf(Meal(id = 21, name = "Oats")), 1 to mutableListOf(Meal(id = 22, name = "Rice")))
        templates += MealTemplate("meal-id", "Two days", 2, templateItems)
        var nextId = 100
        val savedItems = mutableMapOf<String, List<Meal>>()
        val copy: (Meal) -> Meal = { it.copy(id = nextId++) }
        val save: (Map<String, List<Meal>>) -> Unit = { savedItems.putAll(it) }

        val result = manager.applyToRange("meal-id", "2026-04-10", "2026-04-11", copy, save)

        assertTrue(result.applied)
        assertEquals("2026-04-11", result.appliedEndDate)
        assertEquals(listOf("Oats"), savedItems["2026-04-10"]?.map { it.name })
        assertEquals(listOf("Rice"), savedItems["2026-04-11"]?.map { it.name })
        assertNotEquals(21, savedItems["2026-04-10"]?.single()?.id)
        assertEquals("Two days", ranges.single().templateName)

        val overlap = manager.applyToRange("meal-id", "2026-04-11", "2026-04-11", copy, save)
        assertFalse(overlap.applied)
        assertNotNull(overlap.conflictRange)

        val tooLong = manager.applyToRange("meal-id", "2026-04-13", "2026-04-15", copy, save)
        assertFalse(tooLong.applied)
        assertTrue(tooLong.failureReason.orEmpty().contains("2-day template"))
    }

    @Test
    fun renameAndDelete_keepTemplateNamesAndAppliedMarkersInSync() {
        templates += MealTemplate("meal-id", "Old name", 1)
        ranges += AppliedTemplateRange("meal-id", "Old name", "2026-05-01", "2026-05-01")

        assertTrue(manager.rename("meal-id", "  New name  "))
        assertEquals("New name", manager.list().single().name)
        assertEquals("New name", ranges.single().templateName)
        assertFalse(manager.rename("missing", "Name"))
        assertFalse(manager.rename("meal-id", "  "))

        manager.delete("meal-id")
        assertTrue(templates.isEmpty())
        assertTrue(ranges.isEmpty())
    }
}
