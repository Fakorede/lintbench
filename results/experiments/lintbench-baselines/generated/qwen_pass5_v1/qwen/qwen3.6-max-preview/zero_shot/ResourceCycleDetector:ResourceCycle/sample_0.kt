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
import org.w3c.dom.Element

class ResourceCycleDetector : ResourceXmlDetector() {

    private val graph = mutableMapOf<String, MutableSet<String>>()
    private val edgeLocations = mutableMapOf<Pair<String, String>, Location>()
    private val resourceLocations = mutableMapOf<String, Location>()

    companion object {
        val ISSUE = Issue.create(
            id = "ResourceCycle",
            briefDescription = "Cycle in resource definitions",
            explanation = "There should be no cycles in resource definitions as this can lead to runtime exceptions.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                ResourceCycleDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private val REF_PATTERN = Regex("[@?](?:[\\w.]+:)?([\\w.]+)/([\\w.]+)")
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean = folderType == ResourceFolderType.VALUES

    override fun getApplicableElements(): Collection<String>? = null

    override fun visitElement(context: XmlContext, element: Element) {
        val name = element.getAttribute("name")
        if (name.isEmpty()) return

        val tag = element.tagName
        val type = if (tag == "item") {
            element.getAttribute("type").takeIf { it.isNotEmpty() } ?: tag
        } else {
            tag
        }
        val source = "$type/$name"

        resourceLocations[source] = context.getNameLocation(element)

        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i)
            val attrName = attr.nodeName
            if (attrName == "name" || attrName == "type") continue
            scanForReferences(context, element, source, attr.nodeValue)
        }

        val text = element.textContent
        if (!text.isNullOrBlank()) {
            scanForReferences(context, element, source, text)
        }

        if (tag == "style" && element.hasAttribute("parent")) {
            val parent = element.getAttribute("parent")
            if (parent.isNotEmpty() && !parent.contains('/') && !parent.startsWith('@') && !parent.startsWith('?')) {
                val target = "style/$parent"
                graph.getOrPut(source) { mutableSetOf() }.add(target)
                edgeLocations[source to target] = context.getLocation(element)
            }
        }
    }

    private fun scanForReferences(context: XmlContext, element: Element, source: String, text: String) {
        REF_PATTERN.findAll(text).forEach { match ->
            val refType = match.groupValues[1]
            val refName = match.groupValues[2]
            val target = "$refType/$refName"
            graph.getOrPut(source) { mutableSetOf() }.add(target)
            edgeLocations[source to target] = context.getLocation(element)
        }
    }

    override fun afterCheckRootProject(context: Context) {
        detectCycles(context)
        graph.clear()
        edgeLocations.clear()
        resourceLocations.clear()
    }

    private fun detectCycles(context: Context) {
        val visited = mutableSetOf<String>()
        val recursionStack = mutableSetOf<String>()
        val path = mutableListOf<String>()
        val reported = mutableSetOf<String>()

        fun dfs(node: String) {
            if (node in recursionStack) {
                val startIdx = path.indexOf(node)
                if (startIdx == -1) return
                val cycle = path.subList(startIdx, path.size).toMutableList()
                cycle.add(node)

                val minNode = cycle.minOrNull() ?: return
                val minIdx = cycle.indexOf(minNode)
                val normalized = (cycle.subList(minIdx, cycle.size - 1) + cycle.subList(0, minIdx)).joinToString(" -> ")
                if (reported.add(normalized)) {
                    val loc = edgeLocations[cycle[cycle.size - 2] to cycle.last()]
                        ?: resourceLocations[cycle.first()]
                        ?: return
                    context.report(ISSUE, loc, "Resource cycle detected: $normalized")
                }
                return
            }
            if (node in visited) return
            visited.add(node)
            recursionStack.add(node)
            path.add(node)

            for (neighbor in graph[node] ?: emptySet()) {
                dfs(neighbor)
            }

            path.removeAt(path.lastIndex)
            recursionStack.remove(node)
        }

        for (node in graph.keys.toList()) {
            if (node !in visited) {
                dfs(node)
            }
        }
    }
}