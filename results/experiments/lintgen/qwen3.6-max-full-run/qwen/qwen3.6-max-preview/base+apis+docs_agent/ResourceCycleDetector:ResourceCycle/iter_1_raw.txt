package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.util.regex.Pattern

class ResourceCycleDetector : Detector(), XmlScanner {

    private val graph = mutableMapOf<String, MutableSet<String>>()
    private val locations = mutableMapOf<String, Location>()
    private val edgeLocations = mutableMapOf<Pair<String, String>, Location>()

    private val refPattern = Pattern.compile("@\\+?([\\w.]+)/([\\w.]+)")

    override fun getApplicableElements(): Collection<String>? = null

    override fun visitElement(context: XmlContext, element: Element) {
        val folderType = context.resourceFolderType ?: return
        val currentRes = getCurrentResource(context, folderType, element)

        if (currentRes != null) {
            locations.putIfAbsent(currentRes, context.getLocation(element))
        }

        val effectiveRes = currentRes ?: run {
            if (folderType != ResourceFolderType.VALUES) {
                val fileName = context.file.name.substringBeforeLast('.')
                "${folderType.getName()}/$fileName"
            } else {
                null
            }
        } ?: return

        if (element.tagName == "style") {
            var parent = element.getAttribute("parent")
            val loc = context.getLocation(element)
            if (parent.isEmpty()) {
                val name = element.getAttribute("name")
                val dotIndex = name.lastIndexOf('.')
                if (dotIndex != -1) {
                    parent = name.substring(0, dotIndex)
                    addEdge(effectiveRes, "style/$parent", loc)
                }
            } else {
                if (parent.startsWith("@")) {
                    val matcher = refPattern.matcher(parent)
                    if (matcher.find()) {
                        addEdge(effectiveRes, "${matcher.group(1)}/${matcher.group(2)}", loc)
                    }
                } else if (!parent.startsWith("?")) {
                    addEdge(effectiveRes, "style/$parent", loc)
                }
            }
        }

        if (element.tagName == "include") {
            val layout = element.getAttribute("layout")
            if (layout.startsWith("@")) {
                val matcher = refPattern.matcher(layout)
                if (matcher.find()) {
                    addEdge(effectiveRes, "${matcher.group(1)}/${matcher.group(2)}", context.getLocation(element))
                }
            }
        }

        if (folderType == ResourceFolderType.VALUES && element.tagName == "item") {
            val type = element.getAttribute("type")
            val name = element.getAttribute("name")
            if (type.isNotEmpty() && name.isNotEmpty()) {
                val aliasRes = "$type/$name"
                locations.putIfAbsent(aliasRes, context.getLocation(element))
                val text = element.textContent?.trim() ?: ""
                val matcher = refPattern.matcher(text)
                if (matcher.find()) {
                    addEdge(aliasRes, "${matcher.group(1)}/${matcher.group(2)}", context.getLocation(element))
                }
            }
        }

        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i)
            val value = attr.nodeValue ?: continue
            extractRefs(value, effectiveRes, context.getLocation(attr))
        }

        var child = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.TEXT_NODE || child.nodeType == Node.CDATA_SECTION_NODE) {
                extractRefs(child.nodeValue, effectiveRes, context.getLocation(child))
            }
            child = child.nextSibling
        }
    }

    private fun getCurrentResource(context: XmlContext, folderType: ResourceFolderType, element: Element): String? {
        return if (folderType == ResourceFolderType.VALUES) {
            val name = element.getAttribute("name")
            if (name.isNotEmpty()) {
                val type = if (element.tagName == "item") element.getAttribute("type") else element.tagName
                if (type.isNotEmpty()) "$type/$name" else null
            } else {
                null
            }
        } else {
            null
        }
    }

    private fun extractRefs(text: String, currentRes: String, location: Location) {
        val matcher = refPattern.matcher(text)
        while (matcher.find()) {
            val type = matcher.group(1)
            val name = matcher.group(2)
            val refRes = "$type/$name"
            if (refRes != currentRes) {
                addEdge(currentRes, refRes, location)
            }
        }
    }

    private fun addEdge(from: String, to: String, location: Location) {
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
                            val loc = edgeLocations[node to neighbor] ?: locations[node] ?: Location.create(context.project.dir)
                            context.report(ISSUE, loc, "Resource cycle detected: $cycleStr")
                        }
                    }
                } else if (state[neighbor] == 0 || state[neighbor] == null) {
                    dfs(neighbor)
                }
            }
            path.removeAt(path.lastIndex)
            state[node] = 2
        }

        for (node in graph.keys.toList()) {
            if (state[node] == 0 || state[node] == null) {
                dfs(node)
            }
        }

        graph.clear()
        locations.clear()
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