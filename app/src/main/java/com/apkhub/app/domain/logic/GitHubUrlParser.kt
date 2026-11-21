package com.apkhub.app.domain.logic

import com.apkhub.app.domain.model.GitHubRepoRef

object GitHubUrlParser {
    private val validSegmentRegex = Regex("^[A-Za-z0-9._-]+$")
    private val reservedOwners = setOf(
        "about", "explore", "features", "issues", "login", "marketplace",
        "new", "notifications", "organizations", "pricing", "pulls",
        "search", "settings", "signup", "sponsors", "topics", "trending"
    )

    fun parse(rawInput: String): GitHubRepoRef? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null

        val cleaned = trimmed
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .removePrefix("m.")
            .trimEnd('/')

        val pathPart = when {
            cleaned.startsWith("github.com/", ignoreCase = true) ->
                cleaned.substring("github.com/".length)
            cleaned.startsWith("api.github.com/repos/", ignoreCase = true) ->
                cleaned.substring("api.github.com/repos/".length)
            cleaned.contains("://") || cleaned.contains(".") && !cleaned.contains("/") ->
                return null
            cleaned.contains("/") && !cleaned.substringBefore("/").contains(".") ->
                cleaned
            else -> return null
        }

        val withoutQuery = pathPart.substringBefore("?").substringBefore("#")
        val segments = withoutQuery.split("/").filter { it.isNotBlank() }
        if (segments.size < 2) return null

        val owner = segments[0].trim()
        val repo = segments[1].trim().removeSuffix(".git")

        if (owner.isEmpty() || repo.isEmpty()) return null
        if (owner.lowercase() in reservedOwners) return null
        if (!validSegmentRegex.matches(owner) || !validSegmentRegex.matches(repo)) return null

        return GitHubRepoRef(owner = owner, repo = repo)
    }
}
