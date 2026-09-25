package za.co.devwell.domain

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import za.co.devwell.data.MockProjectRepository
import za.co.devwell.model.DevProject
import za.co.devwell.model.ProjectTask
import za.co.devwell.model.TaskPriority
import za.co.devwell.model.TaskStatus

class ProjectRepositoryTest {
    private val repository = MockProjectRepository()

    @Test
    fun `progress derives from completed tasks`() = runBlocking {
        val p1 = repository.projects.first().first { it.id == "p1" }
        assertEquals(33, p1.progress)
    }

    @Test
    fun `createProject adds project and generates id`() = runBlocking {
        repository.createProject(
            DevProject(
                id = "",
                name = "New Project",
                description = "demo",
                colour = "#174A35",
                deadline = "1 Oct",
                progress = 0,
                tasks = emptyList(),
            )
        )
        val created = repository.projects.first().first { it.name == "New Project" }
        assertFalse(created.id.isBlank())
        assertEquals(0, created.progress)
    }

    @Test
    fun `updateProject persists changes`() = runBlocking {
        val original = repository.projects.first().first { it.id == "p1" }
        repository.updateProject(original.copy(name = "Renamed", deadline = "31 Dec", colour = "#112233"))
        val updated = repository.projects.first().first { it.id == "p1" }
        assertEquals("Renamed", updated.name)
        assertEquals("31 Dec", updated.deadline)
        assertEquals(33, updated.progress)
    }

    @Test
    fun `deleteProject removes project`() = runBlocking {
        repository.deleteProject("p2")
        assertFalse(repository.projects.first().any { it.id == "p2" })
    }

    @Test
    fun `addTask recomputes progress`() = runBlocking {
        repository.addTask(
            "p2",
            ProjectTask("", "New task", "1 Oct", TaskPriority.LOW, status = TaskStatus.DONE),
        )
        val p2 = repository.projects.first().first { it.id == "p2" }
        assertEquals(2, p2.tasks.size)
        assertEquals(50, p2.progress)
    }

    @Test
    fun `addTask generates unique ids`() = runBlocking {
        repository.addTask("p1", ProjectTask("", "A", "1 Oct", TaskPriority.LOW, note = ""))
        repository.addTask("p1", ProjectTask("", "B", "1 Oct", TaskPriority.LOW, note = ""))
        val p1 = repository.projects.first().first { it.id == "p1" }
        val taskIds = p1.tasks.map { it.id }
        assertEquals(taskIds.size, taskIds.toSet().size)
    }

    @Test
    fun `updateTask recomputes progress`() = runBlocking {
        val p1 = repository.projects.first().first { it.id == "p1" }
        val target = p1.tasks.first { it.id == "t2" }
        repository.updateTask("p1", target.copy(status = TaskStatus.DONE))
        val updated = repository.projects.first().first { it.id == "p1" }
        assertEquals(2, updated.tasks.count { it.isDone })
        assertEquals(66, updated.progress)
    }

    @Test
    fun `deleteTask recomputes progress`() = runBlocking {
        repository.deleteTask("p1", "t1")
        val p1 = repository.projects.first().first { it.id == "p1" }
        assertEquals(2, p1.tasks.size)
        assertEquals(0, p1.progress)
    }

    @Test
    fun `toggleTask flips status and recomputes progress`() = runBlocking {
        repository.toggleTask("p1", "t2")
        val checked = repository.projects.first().first { it.id == "p1" }
        assertEquals(2, checked.tasks.count { it.isDone })
        assertEquals(66, checked.progress)

        repository.toggleTask("p1", "t2")
        val unchecked = repository.projects.first().first { it.id == "p1" }
        assertEquals(1, unchecked.tasks.count { it.isDone })
        assertEquals(33, unchecked.progress)
    }

    @Test
    fun `empty project list is supported`() = runBlocking {
        repository.deleteProject("p1")
        repository.deleteProject("p2")
        assertTrue(repository.projects.first().isEmpty())
    }
}