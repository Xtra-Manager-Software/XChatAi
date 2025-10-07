package id.xms.xcai.data.model

data class GroqModel(
    val id: String,
    val name: String,
    val developer: String,
    val contextWindow: Int,
    val description: String
) {
    companion object {
        val availableModels = listOf(
            GroqModel(
                id = "llama-3.3-70b-versatile",
                name = "Llama 3.3 70B",
                developer = "Meta",
                contextWindow = 131072,
                description = "Versatile model with 70B parameters, best for general tasks"
            ),
            GroqModel(
                id = "meta-llama/llama-4-maverick-17b-128e-instruct",
                name = "Llama 4 Maverick",
                developer = "Meta",
                contextWindow = 131072,
                description = "Multimodal model with 17B active parameters and 128 experts, great for creative writing and image understanding"
            ),
            GroqModel(
                id = "llama-3.1-8b-instant",
                name = "Llama 3.1 8B Instant",
                developer = "Meta",
                contextWindow = 131072,
                description = "Fast and efficient 8B model for quick responses"
            ),
            GroqModel(
                id = "moonshotai/kimi-k2-instruct-0905",
                name = "Kimi K2",
                developer = "Moonshot AI",
                contextWindow = 256000,
                description = "MoE model with 1T parameters, 32B activated. Excellence in tool use, coding, and agentic tasks with 256K context"
            ),
            GroqModel(
                id = "deepseek-r1-distill-llama-70b",
                name = "DeepSeek R1 Distill Llama 70B",
                developer = "DeepSeek",
                contextWindow = 131072,
                description = "Reasoning-focused model with enhanced Chain-of-Thought capabilities for complex problem solving"
            ),
            GroqModel(
                id = "openai/gpt-oss-120b",
                name = "GPT-OSS 120B",
                developer = "OpenAI",
                contextWindow = 131072,
                description = "OpenAI's flagship open-weight model with 120B parameters"
            ),
            GroqModel(
                id = "openai/gpt-oss-20b",
                name = "GPT-OSS 20B",
                developer = "OpenAI",
                contextWindow = 131072,
                description = "Efficient OpenAI model with 20B parameters"
            ),
            GroqModel(
                id = "meta-llama/llama-guard-4-12b",
                name = "Llama Guard 4 12B",
                developer = "Meta",
                contextWindow = 131072,
                description = "Safety-focused model for content moderation"
            )
        )

        val defaultModel = availableModels[0] // llama-3.3-70b-versatile
    }
}
