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

    private val adjacency = mutableMapOf<String, MutableSet<String>>()
    private val locations = mutableMapOf<String, Location>()

    override fun beforeCheckRootProject(context: Context) {
        adjacency.clear()
        locations.clear()
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean = true

    override fun getApplicableElements(): Collection<String>? = null

    override fun getApplicableAttributes(): Collection<String>? = null

    override fun visitElement(context: XmlContext, element: Element) {
        val resName = getResourceName(element)
        if (resName != null) {
            locations[resName] = context.getLocation(element)
            adjacency.putIfAbsent(resName, mutableSetOf())
        }
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val owner = attribute.ownerElement ?: return
        val currentRes = getResourceName(owner) ?: return
        val value = attribute.value
        val regex = Regex("[@?]\\+?(?:\\w+:)?(\\w+)/(\\w+)")
        regex.findAll(value).forEach { match ->
            val refType = match.groupValues[1]
            val refName = match.groupValues[2]
            val refRes = "$refType/$refName"
            adjacency.getOrPut(currentRes) { mutableSetOf() }.add(refRes)
        }
    }

    override fun afterCheckRootProject(context: Context) {
        val visited = mutableSetOf<String>()
        val recStack = mutableSetOf<String>()
        val path = mutableListOf<String>()
        val reported = mutableSetOf<String>()

        fun dfs(node: String) {
            if (node in recStack) {
                if (node !in reported) {
                    reported.add(node)
                    val cycleStartIdx = path.indexOf(node)
                    val cycle = path.subList(cycleStartIdx, path.size) + node
                    val loc = locations[node]
                    if (loc != null) {
                        context.report(
                            ISSUE,
                            loc,
                            "Resource cycle detected: ${cycle.joinToString(" -> ")}"
                        )
                    }
                }
                return
            }
            if (node in visited) return
            visited.add(node)
            recStack.add(node)
            path.add(node)
            for (neighbor in adjacency[node] ?: emptySet()) {
                dfs(neighbor)
            }
            path.removeAt(path.lastIndex)
            recStack.remove(node)
        }

        for (node in adjacency.keys) {
            if (node !in visited) {
                dfs(node)
            }
        }
    }

    private fun getResourceName(element: Element): String? {
        val name = element.getAttribute("name")
        if (name.isNullOrEmpty()) return null
        val type = if (element.tagName == "item") element.getAttribute("type") else element.tagName
        if (type.isNullOrEmpty()) return null
        return "$type/$name"
    }

    companion object {
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