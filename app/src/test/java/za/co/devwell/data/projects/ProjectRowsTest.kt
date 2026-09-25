package za.co.devwell.data.projects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import za.co.devwell.model.ProjectTask
import za.co.devwell.model.TaskPriority
import za.co.devwell.model.TaskStatus

class ProjectRowsTest {
    @Test
    fun `maps project and task rows to domain models`() {
        val project = ProjectRow(
            id = "p1",
            userId = "u1",
            name = "Release",
            description = "Ship it",
            colour = "#123456",
            deadline = "2026-09-18",
            progress = 50,
        ).toDomain(
            listOf(
                ProjectTaskRow(
                    id = "t1",
                    projectId = "p1",
                    title = "Verify build",
                    deadline = "2026-09-17",
                    priority = TaskPriority.HIGH,
                    note = "Run the checks",
                    status = TaskStatus.DONE,
                ).toDomain(),
            ),
        )

        assertEquals("Release", project.name)
        assertEquals("18 Sep", project.deadline)
        assertEquals(50, project.progress)
        assertEquals("Verify build", project.tasks.single().title)
        assertEquals("17 Sep", project.tasks.single().deadline)
        assertEquals(TaskPriority.HIGH, project.tasks.single().priority)
        assertEquals("Run the checks", project.tasks.single().note)
        assertEquals(TaskStatus.DONE, project.tasks.single().status)
    }

    @Test
    fun `converts display and ISO deadlines for persistence`() {
        assertEquals("2026-09-18", storageDeadline("2026-09-18"))
        assertEquals("2027-09-18", storageDeadline("18 Sep 2027"))
        assertTrue(storageDeadline("18 Sep").endsWith("-09-18"))
        assertEquals("18 Sep", formatDeadline("2026-09-18"))
        assertTrue(isValidDeadline("18 Sep 2027"))
        assertFalse(isValidDeadline("not-a-date"))
    }
}
