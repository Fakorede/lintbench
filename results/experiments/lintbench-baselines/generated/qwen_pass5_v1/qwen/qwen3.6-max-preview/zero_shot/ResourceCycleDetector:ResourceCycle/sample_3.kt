package com.android.tools.lint.checks

import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
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
    }

    private val graph = mutableMapOf<String, MutableSet<String>>()
    private val edgeLocations = mutableMapOf<Pair<String, String>, Location>()
    private val reportedCycles = mutableSetOf<String>()

    override fun getApplicableElements(): Collection<String>? = null

    override fun visitElement(context: XmlContext, element: Element) {
        val from = getResourceName(context, element) ?: return

        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as Attr
            val value = attr.value
            if (value.startsWith("@") || value.startsWith("?")) {
                val url = ResourceUrl.parse(value)
                if (url != null && !url.framework && url.type != null && url.name.isNotEmpty() && url.name != "null" && url.name != "empty") {
                    val to = "${url.type.getName()}/${url.name}"
                    graph.getOrPut(from) { mutableSetOf() }.add(to)
                    edgeLocations[from to to] = context.getLocation(attr)
                }
            }
        }
    }

    private fun getResourceName(context: XmlContext, element: Element): String? {
        val type = context.resourceType ?: return null
        return if (type.isValueBased) {
            val name = element.getAttribute("name")
            if (name.isEmpty()) return null
            "${element.localName}/$name"
        } else {
            "${type.getName()}/${context.file.nameWithoutExtension}"
        }
    }

    override fun afterCheckRootProject(context: Context) {
        val state = mutableMapOf<String, Int>()
        val path = mutableListOf<String>()

        for (node in graph.keys) {
            if (state[node] != 2) {
                dfs(node, state, path, context)
            }
        }

        graph.clear()
        edgeLocations.clear()
        reportedCycles.clear()
    }

    private fun dfs(node: String, state: MutableMap<String, Int>, path: MutableList<String>, context: Context) {
        state[node] = 1
        path.add(node)

        for (neighbor in graph[node].orEmpty()) {
            if (state[neighbor] == 1) {
                val cycleStart = path.indexOf(neighbor)
                val cyclePath = path.subList(cycleStart, path.size).toList()
                val minIdx = cyclePath.indices.minByOrNull { cyclePath[it] } ?: 0
                val normalized = (cyclePath.subList(minIdx, cyclePath.size) + cyclePath.subList(0, minIdx)).joinToString(" -> ")
                if (reportedCycles.add(normalized)) {
                    val edge = path.last() to neighbor
                    val location = edgeLocations[edge] ?: Location.create(context.project.dir)
                    context.report(
                        ISSUE,
                        location,
                        "Cycle in resource definitions: $normalized -> $neighbor"
                    )
                }
            } else if (state[neighbor] != 2) {
                dfs(neighbor, state, path, context)
            }
        }

        path.removeAt(path.lastIndex)
        state[node] = 2
    }
}