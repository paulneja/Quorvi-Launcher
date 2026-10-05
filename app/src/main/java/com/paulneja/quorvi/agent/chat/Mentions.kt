package com.paulneja.quorvi.agent.chat

import com.paulneja.quorvi.agent.orchestra.Specialist

data class Mentions(val meta: Boolean = false, val ultra: Boolean = false, val specialists: List<Specialist> = emptyList(), val research: Boolean = false, val plan: Boolean = false) {
    companion object {
        val aliases: Map<String, Specialist> = mapOf(
            "imagen" to Specialist.IMAGE, "image" to Specialist.IMAGE,
            "pensar" to Specialist.THINK, "think" to Specialist.THINK,
            "plan" to Specialist.PLAN,
            "codigo" to Specialist.CODE, "código" to Specialist.CODE, "code" to Specialist.CODE,
            "escribir" to Specialist.WRITE, "write" to Specialist.WRITE,
            "revisar" to Specialist.REVIEW, "review" to Specialist.REVIEW,
        )

        private val word = Regex("(?<![\\w@])@([\\p{L}]+)")

        fun command(text: String): String? = Regex("^\\s*/([\\p{L}]+)").find(text)?.groupValues?.get(1)?.lowercase()

        fun parse(text: String): Mentions {
            val words = word.findAll(text).map { it.groupValues[1].lowercase() }.toList()
            return Mentions(
                meta = "meta" in words || command(text) == "meta",
                ultra = "ultra" in words,
                specialists = words.mapNotNull { aliases[it] }.distinct(),
                research = "investigar" in words || "research" in words || command(text) == "investigar",
                plan = command(text) == "plan",
            )
        }
    }
}
