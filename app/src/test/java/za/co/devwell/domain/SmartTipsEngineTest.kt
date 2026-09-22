package za.co.devwell.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import za.co.devwell.model.DevProject
import za.co.devwell.model.ProjectTask
import za.co.devwell.model.SmartTipsEngine
import za.co.devwell.model.TaskPriority
import za.co.devwell.model.TaskStatus
import java.time.LocalDate

class SmartTipsEngineTest {
    @Test
    fun `returns at most three tips`() {
        val projects = listOf(
            DevProject(
                id = "p1",
                name = "App",
                deadline = "1 Jan",
                progress = 10,
                tasks = listOf(
                    ProjectTask("t1", "Overdue A", "1 Jan", TaskPriority.HIGH, status = TaskStatus.OPEN),
                    ProjectTask("t2", "Overdue B", "2 Jan", TaskPriority.HIGH, status = TaskStatus.OPEN),
                    ProjectTask("t3", "Soon", "5 Sep", TaskPriority.MEDIUM, status = TaskStatus.OPEN),
                ),
            )
        )
        val tips = SmartTipsEngine.generate(projects, today = LocalDate.of(2026, 9, 4))
        assertTrue(tips.size <= 3)
        assertEquals(3, tips.size.coerceAtMost(3))
    }
}
