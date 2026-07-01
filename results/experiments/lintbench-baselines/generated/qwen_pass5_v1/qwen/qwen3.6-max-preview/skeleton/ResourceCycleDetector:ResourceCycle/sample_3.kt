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

        private val REF_REGEX = Regex("""[@?](\w+:)?(\w+)/(\w+)""")
    }

    private val graph = mutableMapOf<String, MutableSet<String>>()
    private val locations = mutableMapOf<String, Location>()
    private val state = mutableMapOf<String, State>()
    private val path = mutableListOf<String>()
    private val reportedCycles = mutableSetOf<String>()

    private enum class State { UNVISITED, VISITING, VISITED }

    override fun beforeCheckRootProject(context: Context) {
        graph.clear()
        locations.clear()
        state.clear()
        path.clear()
        reportedCycles.clear()
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType != ResourceFolderType.RAW && folderType != ResourceFolderType.FONT
    }

    override fun getApplicableElements(): Collection<String>? = null

    override fun getApplicableAttributes(): Collection<String>? = null

    override fun visitElement(context: XmlContext, element: Element) {
        val resName = getDefiningResource(context, element)
        if (resName != null) {
            locations.putIfAbsent(resName, context.getLocation(element))
            graph.putIfAbsent(resName, mutableSetOf())
        }

        val text = element.textContent
        if (text != null && (text.contains('@') || text.contains('?'))) {
            for (ref in extractReferences(text)) {
                if (resName != null) {
                    addEdge(resName, ref)
                }
            }
        }
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val element = attribute.ownerElement ?: return
        val resName = getDefiningResource(context, element)
        val value = attribute.value
        if (value != null && (value.contains('@') || value.contains('?'))) {
            for (ref in extractReferences(value)) {
                if (resName != null) {
                    addEdge(resName, ref)
                    locations.putIfAbsent(resName, context.getLocation(attribute))
                }
            }
        }
    }

    override fun afterCheckRootProject(context: Context) {
        for (node in graph.keys) {
            state[node] = State.UNVISITED
        }

        for (node in graph.keys.toList()) {
            if (state[node] == State.UNVISITED) {
                dfs(node, context)
            }
        }
    }

    private fun getDefiningResource(context: XmlContext, element: Element): String? {
        val folderType = context.resourceFolderType ?: return null
        val typeName = folderType.getName()
        return if (typeName == "values") {
            val name = element.getAttribute("name")
            if (name.isNotEmpty()) {
                val actualType = element.getAttribute("type").takeIf { it.isNotEmpty() } ?: typeName
                "$actualType/$name"
            } else null
        } else {
            if (element.parentNode == element.ownerDocument) {
                "$typeName/${context.file.nameWithoutExtension}"
            } else null
        }
    }

    private fun extractReferences(text: String): List<String> {
        return REF_REGEX.findAll(text).mapNotNull { match ->
            val pkg = match.groupValues[1]
            if (pkg == "android:") null
            else "${match.groupValues[2]}/${match.groupValues[3]}"
        }.toList()
    }

    private fun addEdge(from: String, to: String) {
        graph.getOrPut(from) { mutableSetOf() }.add(to)
        graph.putIfAbsent(to, mutableSetOf())
    }

    private fun dfs(node: String, context: Context) {
        state[node] = State.VISITING
        path.add(node)

        for (neighbor in graph[node] ?: emptySet()) {
            if (state[neighbor] == State.VISITING) {
                val cycleStart = path.indexOf(neighbor)
                val cycle = path.subList(cycleStart, path.size) + neighbor
                val normalized = normalizeCycle(cycle)
                if (reportedCycles.add(normalized)) {
                    val loc = locations[neighbor] ?: locations[node] ?: continue
                    context.report(ISSUE, loc, "Resource cycle detected: ${cycle.joinToString(" -> ")}")
                }
            } else if (state[neighbor] == State.UNVISITED) {
                dfs(neighbor, context)
            }
        }

        path.removeAt(path.lastIndex)
        state[node] = State.VISITED
    }

    private fun normalizeCycle(cycle: List<String>): String {
        val loop = cycle.dropLast(1)
        val minIdx = loop.indices.minByOrNull { loop[it] } ?: 0
        val rotated = loop.drop(minIdx) + loop.take(minIdx)
        return rotated.joinToString(" -> ")
    }
}