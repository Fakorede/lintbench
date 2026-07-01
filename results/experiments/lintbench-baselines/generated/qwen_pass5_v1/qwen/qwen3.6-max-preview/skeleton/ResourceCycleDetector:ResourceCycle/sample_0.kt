package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
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

        private val REF_REGEX = Regex("[@?]\\+?([\\w.]+?)/(\\w+)")
    }

    private val graph = mutableMapOf<String, MutableSet<String>>()
    private val locations = mutableMapOf<String, Pair<XmlContext, Element>>()
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
        val defName = getDefinedResourceName(element)
        if (defName != null) {
            locations[defName] = context to element
            graph.putIfAbsent(defName, mutableSetOf())

            val text = element.textContent
            if (!text.isNullOrBlank()) {
                for (ref in extractReferences(text)) {
                    graph.getOrPut(defName) { mutableSetOf() }.add(ref)
                }
            }
        }
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val owner = attribute.ownerElement
        val defName = getDefinedResourceName(owner)
        if (defName != null) {
            val value = attribute.value
            if (value.isNotEmpty()) {
                for (ref in extractReferences(value)) {
                    graph.getOrPut(defName) { mutableSetOf() }.add(ref)
                }
            }
        }
    }

    override fun afterCheckRootProject(context: Context) {
        val state = mutableMapOf<String, Int>() // 0: unvisited, 1: visiting, 2: visited
        val path = mutableListOf<String>()

        fun dfs(node: String) {
            state[node] = 1
            path.add(node)
            for (neighbor in graph[node].orEmpty()) {
                if (state[neighbor] == 1) {
                    val cycleStart = path.indexOf(neighbor)
                    if (cycleStart != -1) {
                        val cycle = path.subList(cycleStart, path.size).toList() + neighbor
                        reportCycle(cycle)
                    }
                } else if (state[neighbor] != 2) {
                    dfs(neighbor)
                }
            }
            path.removeAt(path.lastIndex)
            state[node] = 2
        }

        for (node in graph.keys) {
            if (state[node] != 2) {
                dfs(node)
            }
        }
    }

    private fun getDefinedResourceName(element: Element): String? {
        val name = element.getAttribute("name")
        if (name.isEmpty()) return null
        val type = if (element.tagName == "item") element.getAttribute("type") else element.tagName
        return if (type.isNotEmpty()) "$type/$name" else null
    }

    private fun extractReferences(value: String): List<String> {
        return REF_REGEX.findAll(value).mapNotNull { match ->
            val type = match.groupValues[1].substringAfter(':')
            val name = match.groupValues[2]
            if (type == "android") null else "$type/$name"
        }.toList()
    }

    private fun reportCycle(cycle: List<String>) {
        val cycleKey = cycle.toSet().joinToString("|")
        if (!reportedCycles.add(cycleKey)) return

        val firstRes = cycle[0]
        val (ctx, element) = locations[firstRes] ?: return
        val cycleStr = cycle.joinToString(" -> ")
        ctx.report(ISSUE, element, ctx.getLocation(element), "Resource cycle detected: $cycleStr")
    }
}