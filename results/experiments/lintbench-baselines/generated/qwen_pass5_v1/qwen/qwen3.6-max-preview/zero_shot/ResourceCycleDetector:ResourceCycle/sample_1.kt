package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.util.regex.Pattern

class ResourceCycleDetector : Detector(), Detector.XmlScanner {

    private val graph = mutableMapOf<String, MutableSet<String>>()
    private val locations = mutableMapOf<String, Location>()
    private val reportedCycles = mutableSetOf<String>()
    private val resourceRefPattern = Pattern.compile("@\\+?(?:[a-zA-Z0-9_]+:)?([a-z]+)/([a-zA-Z0-9_.]+)")

    override fun getApplicableElements(): Collection<String>? = ALL

    override fun visitElement(context: XmlContext, element: Element) {
        val currentRes = getCurrentResource(context, element) ?: return
        locations.putIfAbsent(currentRes, context.getLocation(element))
        graph.putIfAbsent(currentRes, mutableSetOf())

        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val value = attributes.item(i).nodeValue ?: continue
            val matcher = resourceRefPattern.matcher(value)
            while (matcher.find()) {
                val type = matcher.group(1)
                val name = matcher.group(2)
                val refRes = "$type/$name"
                graph.getOrPut(currentRes) { mutableSetOf() }.add(refRes)
            }
        }
    }

    private fun getCurrentResource(context: XmlContext, element: Element): String? {
        val parent = element.parentNode
        if (parent != null && parent.nodeType == Node.ELEMENT_NODE && (parent as Element).tagName == "resources") {
            val name = element.getAttribute("name")
            if (name.isNotEmpty()) {
                return "${element.tagName}/$name"
            }
        } else if (parent == null || parent.nodeType == Node.DOCUMENT_NODE) {
            val type = context.resourceType?.getName()
            val name = context.resourceName
            if (type != null && name != null) {
                return "$type/$name"
            }
        }
        return null
    }

    override fun afterCheckRootProject(context: Context) {
        val visited = mutableSetOf<String>()
        val recursionStack = mutableSetOf<String>()
        val path = mutableListOf<String>()

        for (node in graph.keys.toList()) {
            if (!visited.contains(node)) {
                dfs(node, visited, recursionStack, path, context)
            }
        }

        graph.clear()
        locations.clear()
        reportedCycles.clear()
    }

    private fun dfs(node: String, visited: MutableSet<String>, recursionStack: MutableSet<String>, path: MutableList<String>, context: Context) {
        visited.add(node)
        recursionStack.add(node)
        path.add(node)

        for (neighbor in graph[node] ?: emptySet()) {
            if (!visited.contains(neighbor)) {
                dfs(neighbor, visited, recursionStack, path, context)
            } else if (recursionStack.contains(neighbor)) {
                val cycleStart = path.indexOf(neighbor)
                if (cycleStart != -1) {
                    val cycle = path.subList(cycleStart, path.size) + neighbor
                    val cycleKey = cycle.sorted().joinToString("|")
                    if (reportedCycles.add(cycleKey)) {
                        val cycleStr = cycle.joinToString(" -> ")
                        val loc = locations[neighbor] ?: locations[node] ?: Location.create(context.project.dir)
                        context.report(ISSUE, loc, "Resource cycle detected: $cycleStr")
                    }
                }
            }
        }

        path.removeAt(path.lastIndex)
        recursionStack.remove(node)
    }

    companion object {
        val ISSUE = Issue.create(
            id = "ResourceCycle",
            briefDescription = "Cycle in resource definitions",
            explanation = "There should be no cycles in resource definitions as this can lead to runtime exceptions.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(ResourceCycleDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }
}