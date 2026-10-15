package za.co.devwell.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun `reports overdue open tasks only`() {
        val tips = SmartTipsEngine.generate(
            listOf(project(tasks = listOf(task("Overdue", "1 Jan"), task("Done", "1 Jan", status = TaskStatus.DONE)))),
            today = LocalDate.of(2026, 9, 4),
        )

        assertTrue(tips.any { it.id == "overdue" && it.message.contains("1 overdue") })
    }

    @Test
    fun `prioritises the nearest task due within two days`() {
        val tips = SmartTipsEngine.generate(
            listOf(project(tasks = listOf(task("Later", "7 Sep"), task("Soon", "5 Sep")))),
            today = LocalDate.of(2026, 9, 4),
        )

        val deadlineTip = tips.first { it.id == "deadline" }
        assertTrue(deadlineTip.message.contains("Soon"))
    }

    @Test
    fun `suggests breaking down low progress projects with tasks`() {
        val tips = SmartTipsEngine.generate(
            listOf(project(progress = 25, tasks = listOf(task("Work", "30 Sep")))),
            today = LocalDate.of(2026, 9, 4),
        )

        assertTrue(tips.any { it.id == "progress" && it.message.contains("25%") })
    }

    @Test
    fun `suggests a focus block for high priority open tasks`() {
        val tips = SmartTipsEngine.generate(
            listOf(                project(progress = 100, tasks = listOf(task("Important", "30 Sep", priority = TaskPriority.HIGH)))),
            today = LocalDate.of(2026, 9, 4),
        )

        assertTrue(tips.any { it.id == "priority" && it.message.contains("1 high-priority") })
    }

    @Test
    fun `ignores invalid deadlines and empty projects`() {
        val tips = SmartTipsEngine.generate(
            listOf(
                project(progress = 0, tasks = emptyList()),
                project(id = "p2", progress = 100, tasks = listOf(task("Unknown", "not-a-date"))),
            ),
            today = LocalDate.of(2026, 9, 4),
        )

        assertFalse(tips.any { it.id == "overdue" || it.id == "deadline" || it.id == "progress" })
    }

    @Test
    fun `supports ISO deadlines and year boundary dates`() {
        val tips = SmartTipsEngine.generate(
            listOf(
                project(progress = 100, tasks = listOf(task("ISO", "2026-09-05"))),
                project(id = "p2", progress = 100, tasks = listOf(task("New year", "1 Jan", priority = TaskPriority.MEDIUM))),
            ),
            today = LocalDate.of(2026, 12, 31),
        )

        assertTrue(tips.any { it.id == "deadline" && it.message.contains("New year") })
    }

    @Test
    fun `returns no tips when there are no projects`() {
        assertTrue(SmartTipsEngine.generate(emptyList(), today = LocalDate.of(2026, 9, 4)).isEmpty())
    }

    @Test
    fun `never exceeds three tips with many projects`() {
        val projects = (1..5).map { index ->
            project(
                id = "p$index",
                progress = 10,
                tasks = listOf(
                    task("Overdue $index", "1 Jan", priority = TaskPriority.HIGH),
                    task("Soon $index", "5 Sep", priority = TaskPriority.HIGH),
                ),
            )
        }

        val tips = SmartTipsEngine.generate(projects, today = LocalDate.of(2026, 9, 4))
        assertEquals(3, tips.size)
    }

    @Test
    fun `tips refresh when a task is completed`() {
        val overdue = project(tasks = listOf(task("Overdue", "1 Jan", priority = TaskPriority.HIGH)))
        val before = SmartTipsEngine.generate(listOf(overdue), today = LocalDate.of(2026, 9, 4))
        assertTrue(before.any { it.id == "overdue" })

        val completed = overdue.copy(tasks = overdue.tasks.map { it.copy(status = TaskStatus.DONE) })
        val after = SmartTipsEngine.generate(listOf(completed), today = LocalDate.of(2026, 9, 4))
        assertFalse(after.any { it.id == "overdue" || it.id == "priority" })
    }

    @Test
    fun `topNudge exposes the most urgent tip for notifications`() {
        val projects = listOf(project(tasks = listOf(task("Overdue", "1 Jan", priority = TaskPriority.HIGH))))
        val today = LocalDate.of(2026, 9, 4)

        assertEquals(SmartTipsEngine.generate(projects, today).first().message, SmartTipsEngine.topNudge(projects, today))
        assertEquals(null, SmartTipsEngine.topNudge(emptyList(), today))
    }

    private fun project(
        id: String = "p1",
        progress: Int = 100,
        tasks: List<ProjectTask> = emptyList(),
    ) = DevProject(
        id = id,
        name = "App",
        deadline = "30 Sep",
        progress = progress,
        tasks = tasks,
    )

    private fun task(
        title: String,
        deadline: String,
        priority: TaskPriority = TaskPriority.MEDIUM,
        status: TaskStatus = TaskStatus.OPEN,
    ) = ProjectTask("task-${title}-$deadline", title, deadline, priority, status = status)
}
