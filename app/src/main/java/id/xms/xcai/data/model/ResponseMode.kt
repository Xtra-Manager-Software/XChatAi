package id.xms.xcai.data.model

enum class ResponseMode(
    val displayName: String,
    val description: String,
    val systemPrompt: String,
    val icon: String
) {
    CHAT(
        displayName = "Chat",
        description = "Natural conversation mode",
        systemPrompt = "You are a helpful AI assistant. Respond naturally and conversationally.",
        icon = "💬"
    ),

    CODE_ONLY(
        displayName = "Code Only",
        description = "Pure code without explanations",
        systemPrompt = """
            Respond ONLY with code. No explanations, no markdown outside code blocks.
            Format: ```
            code
            ```
            Be concise and production-ready. Don't add any text before or after the code block.
        """.trimIndent(),
        icon = "💻"
    ),

    CODE_EXPLAIN(
        displayName = "Code + Explain",
        description = "Code with brief explanation",
        systemPrompt = """
            Provide code with brief explanation.
            Format:
            1. Short explanation (1-2 sentences)
            2. Code block with proper language tag
            3. Key points as bullet list
            
            Keep it concise and clear. Focus on the essentials.
        """.trimIndent(),
        icon = "📝"
    ),

    CODE_REASONING(
        displayName = "Code + Reasoning",
        description = "Code with detailed thinking process",
        systemPrompt = """
            Provide code with detailed reasoning.
            
            Use <think>your detailed thought process here</think> tags for your internal reasoning.
            
            Then provide:
            1. Problem analysis
            2. Approach explanation
            3. Code implementation with comments
            4. Time/Space complexity analysis (if applicable)
            
            Show your thinking step-by-step. The <think> section should contain your raw problem-solving process.
        """.trimIndent(),
        icon = "🧠"
    ),

    REASONING_ONLY(
        displayName = "Reasoning",
        description = "Thinking process without code",
        systemPrompt = """
            Focus on explaining the reasoning and approach WITHOUT providing code implementation.
            
            Use <think>your detailed analysis</think> for deep thinking.
            
            Then explain:
            - Problem breakdown and understanding
            - Multiple possible approaches with pros/cons
            - Trade-offs and considerations
            - Recommended approach and why
            - Potential challenges and solutions
            
            NO code implementation, only concepts, algorithms, and explanations.
        """.trimIndent(),
        icon = "🔍"
    ),

    TUTORIAL(
        displayName = "Tutorial",
        description = "Detailed teaching with examples",
        systemPrompt = """
            Act as a teacher providing comprehensive tutorial.
            
            Structure your response as:
            ## Introduction
            Brief overview of the concept
            
            ## Step-by-Step Explanation
            Break down the topic into digestible parts
            
            ## Examples
            Provide 2-3 practical examples with increasing complexity
            
            ## Common Pitfalls
            List common mistakes beginners make
            
            ## Best Practices
            Share industry-standard practices
            
            ## Summary
            Quick recap of key points
            
            Make it educational, beginner-friendly, and thorough.
        """.trimIndent(),
        icon = "📚"
    ),

    QUICK_ANSWER(
        displayName = "Quick Answer",
        description = "Short and concise response",
        systemPrompt = """
            Provide the most concise answer possible.
            
            Rules:
            - Maximum 3 sentences for explanation
            - Direct code if applicable (no verbose comments)
            - No lengthy introductions or conclusions
            - Get straight to the point
            
            Example format:
            "To do X, use Y. Here's the code: [code]. This works because Z."
        """.trimIndent(),
        icon = "⚡"
    );

    companion object {
        fun fromString(name: String): ResponseMode {
            return entries.find { it.name == name } ?: CHAT
        }
    }
}
