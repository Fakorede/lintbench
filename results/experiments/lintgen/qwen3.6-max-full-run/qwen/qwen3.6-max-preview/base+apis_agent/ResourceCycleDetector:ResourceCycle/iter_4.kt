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
    private val resRefRegex = Regex("[@?]\\+?(?:[\\w.]+:)?(\\w+)/([\\w.]+)")

    override fun appliesTo(folderType: ResourceFolderType): Boolean = true

    override fun getApplicableElements(): Collection<String>? = null

    override fun beforeCheckProject(context: Context) {
        graph.clear()
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val folderType = context.resourceFolderType ?: return
        val fileName = context.file.name.substringBeforeLast('.')

        val currentRes: String
        if (folderType == ResourceFolderType.VALUES) {
            val nameAttr = element.getAttributeNode("name") ?: return
            val name = nameAttr.value
            val tag = element.tagName
            if (tag == "resources" || tag == "declare-styleable" || tag == "attr" || tag == "skip" || tag == "java-symbol") return

            val type = if (tag == "item") {
                element.getAttribute("type").takeIf { it.isNotEmpty() } ?: "item"
            } else {
                tag
            }
            currentRes = "$type/$name"

            if (tag == "style") {
                val parentAttr = element.getAttributeNode("parent")
                if (parentAttr != null && parentAttr.value.isNotEmpty()) {
                    addEdge(context, currentRes, parentAttr.value, parentAttr)
                } else if (name.contains('.')) {
                    val impliedParent = name.substringBeforeLast('.')
                    addEdge(context, currentRes, "style/$impliedParent", nameAttr)
                }
            }
        } else {
            currentRes = "${folderType.getName()}/$fileName"
            if (element.tagName == "include") {
                val layoutAttr = element.getAttributeNode("layout")
                if (layoutAttr != null) {
                    addEdge(context, currentRes, layoutAttr.value, layoutAttr)
                }
            }
        }

        scanReferences(context, element, currentRes)
    }

    private fun scanReferences(context: XmlContext, element: Element, currentRes: String) {
        val attrs = element.attributes
        for (i in 0 until attrs.length) {
            val attr = attrs.item(i) as Attr
            val attrName = attr.name
            if (attrName == "parent" && element.tagName == "style") continue
            if (attrName == "layout" && element.tagName == "include") continue
            if (attrName == "name" || attrName == "type") continue

            val value = attr.value
            if (value.contains('@') || value.contains('?')) {
                extractAndAddEdges(context, currentRes, value, attr)
            }
        }

        val text = element.textContent
        if (text.contains('@') || text.contains('?')) {
            extractAndAddEdges(context, currentRes, text, element)
        }
    }

    private fun extractAndAddEdges(context: XmlContext, currentRes: String, text: String, node: Node) {
        resRefRegex.findAll(text).forEach { match ->
            val type = match.groupValues[1]
            val name = match.groupValues[2]
            if (type == "id" || type == "android") return@forEach
            val targetRes = "$type/$name"
            addEdge(context, currentRes, targetRes, node)
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

    override fun afterCheckProject(context: Context) {
        detectCycles(context)
        graph.clear()
    }

    private fun detectCycles(context: Context) {
        val visited = mutableSetOf<String>()
        val recursionStack = mutableSetOf<String>()
        val path = mutableListOf<String>()

        fun dfs(node: String) {
            if (node in recursionStack) {
                val cycleStart = path.indexOf(node)
                if (cycleStart != -1) {
                    val cycle = path.subList(cycleStart, path.size) + node
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
                return
            }
            if (node in visited) return

            visited.add(node)
            recursionStack.add(node)
            path.add(node)

            for (neighbor in graph[node]?.keys ?: emptySet()) {
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