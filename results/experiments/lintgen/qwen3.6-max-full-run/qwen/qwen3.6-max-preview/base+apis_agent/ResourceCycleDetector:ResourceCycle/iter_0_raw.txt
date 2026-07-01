package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Element
import org.w3c.dom.Node

class ResourceCycleDetector : Detector(), XmlScanner {

    private val graph = mutableMapOf<String, MutableMap<String, Location>>()
    private val refPattern = Regex("[@?](?:\\+)?([a-zA-Z][a-zA-Z0-9]*):?([a-zA-Z0-9_.]+)")

    override fun getApplicableElements(): Collection<String>? = XmlScanner.ALL

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.parentNode != null) return

        val folder = context.file.parentFile?.name ?: return
        val resType = folder.split('-').first()
        val fileName = context.file.name.substringBeforeLast('.')

        if (resType == "values") {
            processValuesFile(context, element)
        } else {
            val source = "$resType/$fileName"
            scanElementReferences(context, element, source)
        }
    }

    private fun processValuesFile(context: XmlContext, root: Element) {
        val children = root.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is Element) {
                val name = node.getAttribute("name")
                if (name.isNotEmpty()) {
                    val type = if (node.tagName == "item") {
                        node.getAttribute("type").takeIf { it.isNotEmpty() } ?: node.tagName
                    } else {
                        node.tagName
                    }
                    val source = "$type/$name"
                    scanElementReferences(context, node, source)
                }
            }
        }
    }

    private fun scanElementReferences(context: XmlContext, element: Element, source: String) {
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as Attr
            extractReferences(context, source, attr.value, attr)
        }
        val children = element.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is org.w3c.dom.Text) {
                extractReferences(context, source, node.textContent, element)
            }
        }
    }

    private fun extractReferences(context: XmlContext, source: String, text: String, locationHolder: Node) {
        refPattern.findAll(text).forEach { match ->
            val type = match.groupValues[1]
            val name = match.groupValues[2]
            if (type == "android" || type == "id") return@forEach

            val target = "$type/$name"
            if (target != source) {
                val location = context.getLocation(locationHolder)
                graph.getOrPut(source) { mutableMapOf() }[target] = location
            }
        }
    }

    override fun afterCheckRootProject(context: Context) {
        val visited = mutableSetOf<String>()
        val recursionStack = mutableSetOf<String>()
        val path = mutableListOf<String>()

        fun dfs(node: String): List<String>? {
            if (node in recursionStack) {
                val cycleStart = path.indexOf(node)
                return if (cycleStart != -1) {
                    path.subList(cycleStart, path.size) + node
                } else null
            }
            if (node in visited) return null

            visited.add(node)
            recursionStack.add(node)
            path.add(node)

            val neighbors = graph[node]?.keys ?: emptySet()
            for (neighbor in neighbors) {
                val cycle = dfs(neighbor)
                if (cycle != null) return cycle
            }

            path.removeAt(path.lastIndex)
            recursionStack.remove(node)
            return null
        }

        for (node in graph.keys.toList()) {
            if (node !in visited) {
                val cycle = dfs(node)
                if (cycle != null && cycle.size > 1) {
                    val last = cycle[cycle.size - 2]
                    val first = cycle[cycle.size - 1]
                    val location = graph[last]?.get(first)
                    if (location != null) {
                        context.report(
                            ISSUE,
                            location,
                            "Cycle in resource definitions: ${cycle.joinToString(" -> ")}"
                        )
                    }
                }
            }
        }
        graph.clear()
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