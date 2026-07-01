package com.android.tools.lint.checks

import com.android.SdkConstants.ATTR_NAME
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
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Attr
import org.w3c.dom.Element

class ResourceCycleDetector : ResourceXmlDetector(), XmlScanner {

    private val graph = mutableMapOf<String, MutableSet<String>>()
    private val edgeLocations = mutableMapOf<Pair<String, String>, Location>()
    private val referencePattern = Regex("""[@?]\+?(?:\w+:)?(\w+)/([\w.]+)""")

    override fun beforeCheckRootProject(context: Context) {
        graph.clear()
        edgeLocations.clear()
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES
    }

    override fun getApplicableElements(): Collection<String>? = null

    override fun getApplicableAttributes(): Collection<String>? = null

    override fun visitElement(context: XmlContext, element: Element) {
        // Resource definition tracking is handled in visitAttribute
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val owner = attribute.ownerElement ?: return
        val name = owner.getAttribute(ATTR_NAME)
        if (name.isNullOrEmpty()) return

        val type = owner.tagName
        val definingRes = "$type/$name"
        val value = attribute.value

        referencePattern.findAll(value).forEach { match ->
            val refType = match.groupValues[1]
            val refName = match.groupValues[2]
            val refRes = "$refType/$refName"

            graph.getOrPut(definingRes) { mutableSetOf() }.add(refRes)
            edgeLocations[definingRes to refRes] = context.getLocation(attribute)
        }
    }

    override fun afterCheckRootProject(context: Context) {
        val visited = mutableSetOf<String>()
        val recursionStack = mutableSetOf<String>()
        val path = mutableListOf<String>()
        val reportedCycles = mutableSetOf<Set<String>>()

        fun dfs(node: String) {
            visited.add(node)
            recursionStack.add(node)
            path.add(node)

            for (neighbor in graph[node] ?: emptySet()) {
                if (neighbor !in visited) {
                    dfs(neighbor)
                } else if (neighbor in recursionStack) {
                    val cycleStartIndex = path.indexOf(neighbor)
                    if (cycleStartIndex != -1) {
                        val cycleNodes = path.subList(cycleStartIndex, path.size).toSet()
                        if (reportedCycles.add(cycleNodes)) {
                            val cyclePath = path.subList(cycleStartIndex, path.size) + neighbor
                            val location = edgeLocations[path.last() to neighbor]
                            if (location != null) {
                                val cycleStr = cyclePath.joinToString(" -> ")
                                context.report(ISSUE, location, "Cycle in resource definitions: $cycleStr")
                            }
                        }
                    }
                }
            }

            path.removeAt(path.size - 1)
            recursionStack.remove(node)
        }

        for (node in graph.keys) {
            if (node !in visited) {
                dfs(node)
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ResourceCycle",
            briefDescription = "Cycle in resource definitions",
            explanation = "There should be no cycles in resource definitions as this can lead to runtime exceptions.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.FATAL,
            implementation = Implementation(ResourceCycleDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }
}