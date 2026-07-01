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
import java.util.EnumSet

class ResourceCycleDetector : Detector(), XmlScanner {

    private val graph = mutableMapOf<String, MutableSet<String>>()
    private val nodeLocations = mutableMapOf<String, Location>()
    private val edgeLocations = mutableMapOf<Pair<String, String>, Location>()

    override fun getApplicableElements(): Collection<String>? = XmlScanner.ALL
    override fun getApplicableAttributes(): Collection<String>? = XmlScanner.ALL_ATTRIBUTES

    override fun visitElement(context: XmlContext, element: Element) {
        val folderType = context.resourceFolderType ?: return
        val currentRes = getCurrentResource(context, folderType, element)
        if (currentRes != null) {
            nodeLocations.putIfAbsent(currentRes, context.getLocation(element))
        }

        val text = element.textContent
        if (text != null && text.contains('@') && currentRes != null) {
            extractRefs(text, currentRes, context.getLocation(element))
        }
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value
        if (value.isEmpty() || !value.contains('@')) return

        val folderType = context.resourceFolderType ?: return
        val owner = attribute.ownerElement ?: return
        val currentRes = getCurrentResource(context, folderType, owner)
            ?: findEnclosingResource(context, folderType, owner)
            ?: return

        if (folderType == ResourceFolderType.VALUES && owner.tagName == "style" && attribute.name == "parent") {
            handleStyleParent(context, attribute, currentRes)
            return
        }

        extractRefs(value, currentRes, context.getLocation(attribute))
    }

    private fun getCurrentResource(context: XmlContext, folderType: ResourceFolderType, element: Element): String? {
        if (folderType == ResourceFolderType.VALUES) {
            val tagName = element.tagName
            if (tagName == "resources") return null
            val name = element.getAttribute("name")
            if (name.isEmpty()) return null
            val type = if (tagName == "item") element.getAttribute("type") else tagName
            if (type.isEmpty()) return null
            return "$type/$name"
        } else {
            val fileName = context.file.name.substringBeforeLast('.')
            return "${folderType.getName()}/$fileName"
        }
    }

    private fun findEnclosingResource(context: XmlContext, folderType: ResourceFolderType, element: Element): String? {
        var current: Element? = element
        while (current != null) {
            val res = getCurrentResource(context, folderType, current)
            if (res != null) return res
            val parent = current.parentNode
            current = if (parent is Element) parent else null
        }
        return null
    }

    private fun handleStyleParent(context: XmlContext, attribute: Attr, currentRes: String) {
        val parentVal = attribute.value
        val loc = context.getLocation(attribute)
        if (parentVal.startsWith("@")) {
            extractRefs(parentVal, currentRes, loc)
        } else if (parentVal.isNotEmpty() && !parentVal.startsWith("?") && !parentVal.startsWith("android:")) {
            addEdge(currentRes, "style/$parentVal", loc)
        }
    }

    private fun extractRefs(text: String, currentRes: String, location: Location) {
        var i = 0
        while (i < text.length) {
            val atIdx = text.indexOf('@', i)
            if (atIdx == -1) break
            if (atIdx > 0 && text[atIdx - 1] == '\\') {
                i = atIdx + 1
                continue
            }
            var start = atIdx + 1
            if (start < text.length && text[start] == '+') start++

            val slashIdx = text.indexOf('/', start)
            if (slashIdx == -1) {
                i = start
                continue
            }

            val type = text.substring(start, slashIdx)
            var end = slashIdx + 1
            while (end < text.length && (text[end].isLetterOrDigit() || text[end] == '_' || text[end] == '.')) {
                end++
            }
            val name = text.substring(slashIdx + 1, end)

            if (type.isNotEmpty() && name.isNotEmpty() && type != "id" && type != "android") {
                addEdge(currentRes, "$type/$name", location)
            }
            i = end
        }
    }

    private fun addEdge(from: String, to: String, location: Location) {
        if (from == to) return
        graph.getOrPut(from) { mutableSetOf() }.add(to)
        edgeLocations.putIfAbsent(from to to, location)
    }

    override fun afterCheckProject(context: Context) {
        val state = mutableMapOf<String, Int>()
        val path = mutableListOf<String>()
        val reportedCycles = mutableSetOf<String>()

        fun dfs(node: String) {
            state[node] = 1
            path.add(node)
            for (neighbor in graph[node] ?: emptySet()) {
                if (state[neighbor] == 1) {
                    val cycleStartIdx = path.indexOf(neighbor)
                    if (cycleStartIdx != -1) {
                        val cycle = path.subList(cycleStartIdx, path.size) + neighbor
                        val cycleKey = cycle.sorted().joinToString("|")
                        if (reportedCycles.add(cycleKey)) {
                            val cycleStr = cycle.joinToString(" -> ")
                            val loc = edgeLocations[node to neighbor] ?: nodeLocations[node] ?: Location.create(context.project.dir)
                            context.report(ISSUE, loc, "Resource cycle detected: $cycleStr")
                        }
                    }
                } else if (state[neighbor] != 2) {
                    dfs(neighbor)
                }
            }
            path.removeAt(path.lastIndex)
            state[node] = 2
        }

        val allNodes = graph.keys.toMutableSet()
        graph.values.forEach { allNodes.addAll(it) }

        for (node in allNodes) {
            if (state[node] != 2) {
                dfs(node)
            }
        }

        graph.clear()
        nodeLocations.clear()
        edgeLocations.clear()
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