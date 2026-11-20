package ru.workweek.planner.domain

import java.text.Normalizer
import java.util.Locale

object SearchRules {
    fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).replace('ё', 'е')

    fun words(query: String): List<String> = normalize(query)
        .split(Regex("[\\s\\p{Z}]+"))
        .filter { it.isNotEmpty() }

    fun matches(title: String, body: String, query: String): Boolean {
        val words = words(query)
        val normalizedTitle = normalize(title)
        val normalizedBody = normalize(body)
        return words.isNotEmpty() && words.all { it in normalizedTitle || it in normalizedBody }
    }
}
