package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Attr
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.util.EnumSet

class ResourceCycleDetector : Detector(), XmlScanner {

    private val graph = mutableMapOf<String, MutableMap<String, Location>>()
    private val resRefRegex = Regex("@(?:\\+)?(?:[a-zA-Z]+:)?([a-zA-Z]+)/([a-zA-Z0-9_.]+)")

    override fun appliesTo(folderType: ResourceFolderType): Boolean = true

    override fun getApplicableElements(): Collection<String>? = ALL

    override fun visitElement(context: XmlContext, element: Element) {
        val folderType = context.resourceFolderType ?: return
        val fileName = context.file.name.substringBeforeLast('.')

        if (folderType == ResourceFolderType.VALUES) {
            val parent = element.parentNode
            if (parent is Element && parent.tagName == "resources") {
                processValuesElement(context, element)
            }
        } else {
            val currentRes = "${folderType.getName()}/$fileName"
            scanAttributes(context, element, currentRes)
            if (folderType == ResourceFolderType.LAYOUT && element.tagName == "include") {
                val layoutAttr = element.getAttributeNode("layout")
                if (layoutAttr != null) {
                    addEdge(context, currentRes, layoutAttr.value, layoutAttr)
                }
            }
        }
    }

    private fun processValuesElement(context: XmlContext, element: Element) {
        val tagName = element.tagName
        if (tagName == "resources" || tagName == "declare-styleable" || tagName == "attr") return

        val nameAttr = element.getAttributeNode("name") ?: return
        val name = nameAttr.value
        val type = if (tagName == "item") {
            element.getAttribute("type").takeIf { it.isNotEmpty() } ?: "item"
        } else {
            tagName
        }
        val currentRes = "$type/$name"

        if (tagName == "style") {
            val parentAttr = element.getAttributeNode("parent")
            if (parentAttr != null && parentAttr.value.isNotEmpty()) {
                addEdge(context, currentRes, parentAttr.value, parentAttr)
            } else if (name.contains('.')) {
                val impliedParent = name.substringBeforeLast('.')
                addEdge(context, currentRes, "style/$impliedParent", nameAttr)
            }
        }

        scanAttributes(context, element, currentRes)

        val textContent = element.textContent
        if (textContent.contains('@')) {
            addReferencesFromText(context, currentRes, textContent, element)
        }
    }

    private fun scanAttributes(context: XmlContext, element: Element, currentRes: String) {
        val attrs = element.attributes
        for (i in 0 until attrs.length) {
            val attr = attrs.item(i) as Attr
            val value = attr.value
            if (value.contains('@')) {
                addReferencesFromText(context, currentRes, value, attr)
            }
        }
    }

    private fun addReferencesFromText(context: XmlContext, currentRes: String, text: String, locationNode: Node) {
        resRefRegex.findAll(text).forEach { match ->
            val type = match.groupValues[1]
            val name = match.groupValues[2]
            if (type == "android" || type == "id") return@forEach
            val targetRes = "$type/$name"
            addEdge(context, currentRes, targetRes, locationNode)
        }
    }

    private fun addEdge(context: XmlContext, source: String, target: String, node: Node) {
        val normalizedTarget = if ('/' in target) target else {
            val sourceType = source.substringBefore('/')
            "$sourceType/$target"
        }
        if (source != normalizedTarget) {
            graph.getOrPut(source) { mutableMapOf() }[normalizedTarget] = context.getLocation(node)
        }
    }

    override fun afterCheckEachProject(context: Context) {
        detectCycles(context)
        graph.clear()
    }

    private fun detectCycles(context: Context) {
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
                    break
                }
            }
        }
    }

    companion object {
        val ISSUE = Issue.create(
            id = "ResourceCycle",
            briefDescription = "Cycle in resource definitions",
            explanation = "There should be no cycles in resource definitions as this can lead to runtime exceptions.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(ResourceCycleDetector::class.java, Scope.ALL_RESOURCES_SCOPE)
        )
    }
}