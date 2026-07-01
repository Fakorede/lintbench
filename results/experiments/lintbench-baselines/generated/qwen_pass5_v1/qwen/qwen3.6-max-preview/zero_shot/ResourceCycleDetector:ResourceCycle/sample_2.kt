package com.android.tools.lint.checks

import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_TYPE
import com.android.SdkConstants.TAG_ITEM
import com.android.SdkConstants.TAG_RESOURCES
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
import java.util.regex.Pattern

class ResourceCycleDetector : ResourceXmlDetector() {

    private val definitions = mutableMapOf<String, Location>()
    private val graph = mutableMapOf<String, MutableSet<String>>()

    companion object {
        private val REF_PATTERN = Pattern.compile("@\\+?(?:[a-zA-Z]+:)?([a-zA-Z]+)/([a-zA-Z0-9_.]+)")

        @JvmField
        val ISSUE_RESOURCE_CYCLE: Issue = Issue.create(
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

    override fun appliesTo(folderType: ResourceFolderType): Boolean = folderType == ResourceFolderType.VALUES

    override fun getApplicableElements(): Collection<String>? = null

    override fun visitElement(context: XmlContext, element: Element) {
        if (element.tagName == TAG_RESOURCES) return

        val parent = element.parentNode
        if (parent == null || parent.nodeName != TAG_RESOURCES) return

        val type: String
        val name: String?

        if (element.tagName == TAG_ITEM) {
            type = element.getAttribute(ATTR_TYPE) ?: return
            name = element.getAttribute(ATTR_NAME)
        } else {
            type = element.tagName
            name = element.getAttribute(ATTR_NAME)
        }

        if (name.isNullOrEmpty()) return

        val key = "$type/$name"
        definitions[key] = context.getLocation(element)
        graph.putIfAbsent(key, mutableSetOf())

        val text = element.textContent ?: return
        val matcher = REF_PATTERN.matcher(text)
        while (matcher.find()) {
            val refType = matcher.group(1)
            val refName = matcher.group(2)
            if (refType != null && refName != null) {
                val refKey = "$refType/$refName"
                graph[key]!!.add(refKey)
            }
        }
    }

    override fun afterCheckEachProject(context: Context) {
        findAndReportCycles(context)
        definitions.clear()
        graph.clear()
    }

    private fun findAndReportCycles(context: Context) {
        val visited = mutableSetOf<String>()
        val recStack = mutableSetOf<String>()
        val reportedCycles = mutableSetOf<Set<String>>()

        for (node in graph.keys) {
            if (!visited.contains(node)) {
                val path = mutableListOf<String>()
                dfs(node, visited, recStack, path, reportedCycles, context)
            }
        }
    }

    private fun dfs(
        node: String,
        visited: MutableSet<String>,
        recStack: MutableSet<String>,
        path: MutableList<String>,
        reportedCycles: MutableSet<Set<String>>,
        context: Context
    ) {
        visited.add(node)
        recStack.add(node)
        path.add(node)

        for (neighbor in graph[node] ?: emptySet()) {
            if (!visited.contains(neighbor)) {
                dfs(neighbor, visited, recStack, path, reportedCycles, context)
            } else if (recStack.contains(neighbor)) {
                val cycleStartIndex = path.indexOf(neighbor)
                if (cycleStartIndex != -1) {
                    val cycleNodes = path.subList(cycleStartIndex, path.size).toSet()
                    if (reportedCycles.add(cycleNodes)) {
                        val cyclePath = (path.subList(cycleStartIndex, path.size) + neighbor).joinToString(" -> ")
                        val reportNode = cycleNodes.firstOrNull { definitions.containsKey(it) } ?: node
                        val location = definitions[reportNode]
                        if (location != null) {
                            context.report(
                                ISSUE_RESOURCE_CYCLE,
                                location,
                                "Resource cycle: $cyclePath"
                            )
                        }
                    }
                }
            }
        }

        path.removeAt(path.size - 1)
        recStack.remove(node)
    }
}