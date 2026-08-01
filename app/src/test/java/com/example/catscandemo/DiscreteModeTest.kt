package com.example.catscandemo

import com.example.catscandemo.domain.model.TemplateMode
import com.example.catscandemo.domain.model.TemplateModel
import com.example.catscandemo.domain.use_case.AddTemplateUseCase
import com.example.catscandemo.domain.use_case.TemplateRepository
import com.example.catscandemo.ui.components.BarcodeStabilizer
import org.junit.Assert.assertEquals
import org.junit.Test

class DiscreteModeTest {

    @Test
    fun continuousSameBarcodeEmitsOnlyOnceUntilItLeavesFrame() {
        var now = 100L
        val emitted = mutableListOf<String>()
        val stabilizer = BarcodeStabilizer(
            nowMs = { now },
            absenceResetMs = 1_500L
        )

        stabilizer.stabilize("A", emitted::add)
        now += 100
        stabilizer.stabilize("A", emitted::add)

        repeat(5) {
            now += 100
            stabilizer.stabilize("A", emitted::add)
        }
        assertEquals(listOf("A"), emitted)

        now += 1_600
        stabilizer.stabilize("A", emitted::add)
        now += 100
        stabilizer.stabilize("A", emitted::add)

        assertEquals(listOf("A", "A"), emitted)
    }

    @Test
    fun newDiscreteTemplateStartsWithFirstRoomSelected() {
        val repository = FakeTemplateRepository()
        val template = AddTemplateUseCase(repository)(
            name = "离散测试",
            mode = TemplateMode.DISCRETE
        )

        assertEquals(TemplateMode.DISCRETE, template.mode)
        assertEquals(1, template.lastSelectedFloor)
        assertEquals(listOf("101"), template.selectedRooms)
        assertEquals(template, repository.addedTemplate)
    }
}

private class FakeTemplateRepository : TemplateRepository {
    var addedTemplate: TemplateModel? = null

    override fun addTemplate(template: TemplateModel) {
        addedTemplate = template
    }

    override fun deleteTemplate(id: String): TemplateModel? = null

    override fun updateTemplate(template: TemplateModel) = Unit

    override fun getTemplateById(id: String): TemplateModel? = null

    override fun getActiveTemplate(): TemplateModel? = null

    override fun setActiveTemplate(id: String) = Unit

    override fun getAllTemplates(): List<TemplateModel> = emptyList()

    override fun saveTemplates(templates: List<TemplateModel>, activeId: String?) = Unit

    override fun loadTemplates(): Pair<List<TemplateModel>, String?> = emptyList<TemplateModel>() to null
}
