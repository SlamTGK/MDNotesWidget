package com.mdnotes.widget

object FolderRules {
    fun isHidden(path: String): Boolean = path.split('/').any { it.startsWith('.') }

    // A leading slash denotes a selected path relative to the root. Unprefixed legacy
    // rules still match a folder name at any depth, preserving existing exclusions.
    fun excludes(path: String, rules: List<String>): Boolean = isHidden(path) || rules.any { rule ->
        if (rule.startsWith('/')) {
            val relative = rule.removePrefix("/").trimEnd('/')
            path == relative || path.startsWith("$relative/")
        } else path.split('/').any { it.equals(rule, ignoreCase = true) }
    }
}
