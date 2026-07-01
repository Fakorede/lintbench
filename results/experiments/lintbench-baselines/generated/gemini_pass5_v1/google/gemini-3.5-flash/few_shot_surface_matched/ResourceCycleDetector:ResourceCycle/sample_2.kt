package com.android.tools.lint.checks

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

class ResourceCycleDetector : ResourceXmlDetector(), XmlScanner {

    private val dependencies = mutableMapOf<String, MutableSet<String>>()
    private val edgeLocations = mutableMapOf<Pair<String, String>, Location>()
    private val resourceRegex = Regex("@(?:[\\w.]+?:)?([\\w_]+)/([\\w_.]+)")

    override fun beforeCheckRootProject(context: Context) {
        dependencies.clear()
        edgeLocations.clear()
    }

    override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean {
        return true
    }

    override fun getApplicableElements(): Collection<String>? {
        return null
    }

    override fun getApplicableAttributes(): Collection<String>? {
        return null
    }

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        if (element.tagName == "style") {
            val from = getDefinedResource(context, element) ?: return
            val parentAttr = element.getAttribute("parent")
            if (parentAttr.isNotEmpty()) {
                val to = if (parentAttr.startsWith("@")) parentAttr else "@style/$parentAttr"
                if (!to.startsWith("@android:")) {
                    val attributeNode = element.getAttributeNode("parent")
                    if (attributeNode != null) {
                        addEdge(from, to, context.getValueLocation(attributeNode))
                    }
                }
            } else {
                val name = element.getAttribute("name")
                if (name.contains('.')) {
                    val parentName = name.substringBeforeLast('.')
                    val to = "@style/$parentName"
                    addEdge(from, to, context.getNameLocation(element))
                }
            }
        }

        val firstChild = element.firstChild
        if (firstChild != null && firstChild.nodeType == org.w3c.dom.Node.TEXT_NODE) {
            val text = firstChild.nodeValue.trim()
            if (text.startsWith("@") && !text.startsWith("@android:") && !text.startsWith("@+")) {
                val from = getDefinedResource(context, element) ?: return
                addEdge(from, text, context.getLocation(element))
            }
        }
    }

    override fun visitAttribute(context: XmlContext, attribute: org.w3c.dom.Attr) {
        val value = attribute.value
        if (value.startsWith("@") && !value.startsWith("@android:") && !value.startsWith("@+")) {
            val from = getDefinedResource(context, attribute) ?: return
            addEdge(from, value, context.getValueLocation(attribute))
        }
    }

    override fun afterCheckRootProject(context: Context) {
        val state = mutableMapOf<String, Int>()
        val path = mutableListOf<String>()
        val cyclesReported = mutableSetOf<Set<String>>()

        fun dfs(node: String) {
            state[node] = 1
            path.add(node)

            val neighbors = dependencies[node]
            if (neighbors != null) {
                for (neighbor in neighbors) {
                    val neighborState = state[neighbor] ?: 0
                    if (neighborState == 1) {
                        val cycleStartIndex = path.indexOf(neighbor)
                        if (cycleStartIndex != -1) {
                            val cycle = path.subList(cycleStartIndex, path.size).toList()
                            val cycleSet = cycle.toSet()
                            if (cyclesReported.add(cycleSet)) {
                                reportCycle(cycle, context)
                            }
                        }
                    } else if (neighborState == 0) {
                        dfs(neighbor)
                    }
                }
            }

            path.removeAt(path.size - 1)
            state[node] = 2
        }

        for (node in dependencies.keys) {
            if ((state[node] ?: 0) == 0) {
                dfs(node)
            }
        }
    }

    private fun addEdge(from: String, to: String, location: Location) {
        val cleanFrom = cleanResourceUri(from)
        val cleanTo = cleanResourceUri(to)
        if (cleanFrom != null && cleanTo != null && cleanFrom != cleanTo) {
            dependencies.getOrPut(cleanFrom) { mutableSetOf() }.add(cleanTo)
            edgeLocations[Pair(cleanFrom, cleanTo)] = location
        }
    }

    private fun cleanResourceUri(uri: String): String? {
        val match = resourceRegex.find(uri) ?: return null
        val type = match.groups[1]?.value ?: return null
        val name = match.groups[2]?.value ?: return null
        return "@$type/$name"
    }

    private fun getDefinedResource(context: XmlContext, node: org.w3c.dom.Node): String? {
        val folderType = context.resourceFolderType ?: return null
        if (folderType == com.android.resources.ResourceFolderType.VALUES) {
            var curr: org.w3c.dom.Node? = node
            while (curr != null) {
                val parent = curr.parentNode
                if (parent != null && parent.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
                    val parentEl = parent as org.w3c.dom.Element
                    if (parentEl.tagName == "resources") {
                        val currElement = curr as? org.w3c.dom.Element ?: return null
                        val name = currElement.getAttribute("name")
                        if (name.isNotEmpty()) {
                            val type = currElement.tagName
                            return "@$type/$name"
                        }
                    }
                }
                curr = parent
            }
        } else {
            val name = context.file.name.substringBefore('.')
            return "@${folderType.getName()}/$name"
        }
        return null
    }

    private fun reportCycle(cycle: List<String>, context: Context) {
        val cycleStr = (cycle + cycle.first()).joinToString(" -> ")
        val message = "Cycle in resource definitions: $cycleStr"
        var location: Location? = null
        for (i in cycle.indices) {
            val from = cycle[i]
            val to = cycle[(i + 1) % cycle.size]
            location = edgeLocations[Pair(from, to)]
            if (location != null) break
        }
        if (location != null) {
            context.report(ISSUE, location, message)
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ResourceCycle",
            briefDescription = "Cycle in resource definitions",
            explanation = "There should be no cycles in resource definitions as this can lead to runtime exceptions.",
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.FATAL,
            implementation = Implementation(ResourceCycleDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }
}