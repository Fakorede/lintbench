package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Attr
import org.w3c.dom.Element

class ResourceCycleDetector : ResourceXmlDetector(), XmlScanner {

    private val graph = mutableMapOf<String, MutableSet<String>>()
    private val locations = mutableMapOf<String, Location>()
    private var currentResource: String? = null

    override fun beforeCheckRootProject(context: Context) {
        graph.clear()
        locations.clear()
        currentResource = null
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean = true

    override fun getApplicableElements(): Collection<String>? = null

    override fun getApplicableAttributes(): Collection<String>? = null

    override fun visitElement(context: XmlContext, element: Element) {
        val name = element.getAttribute("name")
        if (name.isNotEmpty()) {
            val type = context.resourceFolderType?.getName() ?: "unknown"
            currentResource = "@$type/$name"
            locations[currentResource!!] = context.getLocation(element)
            graph.putIfAbsent(currentResource!!, mutableSetOf())
        }
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val def = currentResource ?: run {
            val type = context.resourceFolderType?.getName() ?: "unknown"
            val fileName = context.file.name.substringBeforeLast('.', context.file.name)
            "@$type/$fileName"
        }

        locations.putIfAbsent(def) { context.getLocation(attribute.ownerElement ?: attribute) }
        graph.putIfAbsent(def, mutableSetOf())

        val value = attribute.value
        if (value.isEmpty()) return

        REF_REGEX.findAll(value).forEach { matchResult ->
            graph[def]!!.add(matchResult.value)
        }
    }

    override fun afterCheckRootProject(context: Context) {
        val visited = mutableSetOf<String>()
        val recursionStack = mutableSetOf<String>()
        val path = mutableListOf<String>()

        fun dfs(node: String): Boolean {
            if (node in recursionStack) {
                val cycleStart = path.indexOf(node)
                if (cycleStart != -1) {
                    val cycle = path.subList(cycleStart, path.size) + node
                    val cycleStr = cycle.joinToString(" -> ")
                    val loc = locations[node] ?: return false
                    context.report(ISSUE, loc, "Cycle in resource definitions: $cycleStr")
                    return true
                }
            }
            if (node in visited) return false

            visited.add(node)
            recursionStack.add(node)
            path.add(node)

            for (neighbor in graph[node] ?: emptySet()) {
                if (dfs(neighbor)) return true
            }

            path.removeAt(path.lastIndex)
            recursionStack.remove(node)
            return false
        }

        for (node in graph.keys.toList()) {
            if (node !in visited) {
                dfs(node)
            }
        }
    }

    companion object {
        private val REF_REGEX = Regex("@\\+?[a-zA-Z]+/[a-zA-Z0-9_.]+")

        @JvmField
        val ISSUE = Issue.create(
            id = "ResourceCycle",
            briefDescription = "Cycle in resource definitions",
            explanation = "There should be no cycles in resource definitions as this can lead to runtime exceptions.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.FATAL,
            implementation = Implementation(ResourceCycleDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }
}