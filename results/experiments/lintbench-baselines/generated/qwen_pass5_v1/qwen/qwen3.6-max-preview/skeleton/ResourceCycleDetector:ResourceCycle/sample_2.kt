package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr
import org.w3c.dom.Element

class ResourceCycleDetector : ResourceXmlDetector() {

    companion object {
        private val IMPLEMENTATION = Implementation(
            ResourceCycleDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ResourceCycle",
            briefDescription = "Cycle in resource definitions",
            explanation = "There should be no cycles in resource definitions as this can lead to runtime exceptions.",
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.FATAL,
            implementation = IMPLEMENTATION,
        )

        private val REFERENCE_REGEX = Regex("@(\\w+)/(\\w+)")
    }

    private val graph = mutableMapOf<String, MutableSet<String>>()
    private val locations = mutableMapOf<String, Location>()
    private val reportedCycles = mutableSetOf<String>()

    override fun beforeCheckRootProject(context: Context) {
        graph.clear()
        locations.clear()
        reportedCycles.clear()
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.VALUES

    override fun getApplicableElements(): Collection<String>? = null

    override fun getApplicableAttributes(): Collection<String>? = null

    override fun visitElement(context: XmlContext, element: Element) {
        val qualifier = getResourceQualifier(element)
        if (qualifier != null) {
            locations[qualifier] = context.getLocation(element)
            graph.getOrPut(qualifier) { mutableSetOf() }
            val text = element.textContent
            if (!text.isNullOrBlank()) {
                addReferences(qualifier, text)
            }
        }
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val owner = attribute.ownerElement as? Element ?: return
        val qualifier = getResourceQualifier(owner) ?: return
        val value = attribute.value
        if (!value.isNullOrBlank()) {
            addReferences(qualifier, value)
        }
    }

    override fun afterCheckRootProject(context: Context) {
        val state = mutableMapOf<String, Int>()
        val path = mutableListOf<String>()

        for (node in graph.keys) {
            if (state[node] != 0) continue
            dfs(node, state, path, context)
        }

        graph.clear()
        locations.clear()
        reportedCycles.clear()
    }

    private fun getResourceQualifier(element: Element): String? {
        val name = element.getAttribute("name")
        if (name.isEmpty()) return null
        val type = if (element.tagName == "item") {
            element.getAttribute("type").takeIf { it.isNotEmpty() } ?: return null
        } else {
            element.tagName
        }
        return "$type/$name"
    }

    private fun addReferences(from: String, text: String) {
        REFERENCE_REGEX.findAll(text).forEach { match ->
            val type = match.groupValues[1]
            val name = match.groupValues[2]
            if (type != "android" && type != "id") {
                val to = "$type/$name"
                graph.getOrPut(from) { mutableSetOf() }.add(to)
            }
        }
    }

    private fun dfs(
        node: String,
        state: MutableMap<String, Int>,
        path: MutableList<String>,
        context: Context
    ) {
        state[node] = 1
        path.add(node)

        for (neighbor in graph[node] ?: emptySet()) {
            when (state[neighbor] ?: 0) {
                1 -> {
                    val cycleStartIndex = path.indexOf(neighbor)
                    if (cycleStartIndex != -1) {
                        val cycle = path.subList(cycleStartIndex, path.size)
                        reportCycle(cycle, context)
                    }
                }
                0 -> dfs(neighbor, state, path, context)
            }
        }

        path.removeAt(path.lastIndex)
        state[node] = 2
    }

    private fun reportCycle(cycle: List<String>, context: Context) {
        val normalized = cycle.sorted().joinToString("->")
        if (!reportedCycles.add(normalized)) return

        val firstRes = cycle.first()
        val location = locations[firstRes] ?: return
        val cycleStr = cycle.joinToString(" -> ") + " -> " + cycle.first()
        context.report(ISSUE, location, "Cycle in resource definitions: $cycleStr")
    }
}