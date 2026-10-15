package za.co.devwell.domain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import za.co.devwell.data.MockAiGuidanceRepository
import za.co.devwell.model.AiMessage

class AiGuidanceRepositoryTest {
    private val repository = MockAiGuidanceRepository()

    @Test
    fun `ask returns guidance for a user question`() = runBlocking {
        val result = repository.ask(listOf(AiMessage("1", fromUser = true, text = "How can I focus?")))
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().isNotBlank())
    }

    @Test
    fun `ask uses the running conversation and answers the latest turn`() = runBlocking {
        val conversation = listOf(
            AiMessage("1", fromUser = true, text = "I'm feeling overloaded."),
            AiMessage("2", fromUser = false, text = "Let's talk about workload."),
            AiMessage("3", fromUser = true, text = "What can I do this week?"),
        )
        val result = repository.ask(conversation)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `ask fails when the conversation is empty`() = runBlocking {
        assertTrue(repository.ask(emptyList()).isFailure)
    }

    @Test
    fun `ask fails when the last turn is not from the user`() = runBlocking {
        val result = repository.ask(listOf(AiMessage("1", fromUser = false, text = "Anything else?")))
        assertTrue(result.isFailure)
    }

    @Test
    fun `ask fails for a blank question`() = runBlocking {
        val result = repository.ask(listOf(AiMessage("1", fromUser = true, text = "   ")))
        assertTrue(result.isFailure)
    }
}
