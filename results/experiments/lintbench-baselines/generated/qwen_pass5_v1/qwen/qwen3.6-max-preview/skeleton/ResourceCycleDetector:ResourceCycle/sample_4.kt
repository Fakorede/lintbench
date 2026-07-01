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
import org.w3c.dom.Attr
import org.w3c.dom.Element

class ResourceCycleDetector : ResourceXmlDetector() {

    private val dependencies = mutableMapOf<String, MutableSet<String>>()
    private val locations = mutableMapOf<String, Location>()

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
    }

    override fun beforeCheckRootProject(context: Context) {
        dependencies.clear()
        locations.clear()
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean = true

    override fun getApplicableElements(): Collection<String>? = null

    override fun getApplicableAttributes(): Collection<String>? = null

    private fun getResourceName(element: Element): String? {
        val nameAttr = element.getAttributeNode("name") ?: return null
        val name = nameAttr.value.trim()
        if (name.isEmpty()) return null
        val type = element.getAttribute("type").takeIf { it.isNotEmpty() }?.trim() ?: element.tagName
        return "$type/$name"
    }

    private fun extractReferences(value: String): List<String> {
        val refs = mutableListOf<String>()
        val regex = Regex("@\\+?(?:[a-zA-Z.]+:)?([a-zA-Z]+)/([a-zA-Z0-9_.]+)")
        regex.findAll(value).forEach { match ->
            val type = match.groupValues[1]
            val name = match.groupValues[2]
            if (type != "id" && type != "null" && type != "empty") {
                refs.add("$type/$name")
            }
        }
        return refs
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val resName = getResourceName(element)
        if (resName != null) {
            locations[resName] = context.getLocation(element)
            val text = element.textContent ?: ""
            if (text.contains('@')) {
                extractReferences(text).forEach { ref ->
                    dependencies.getOrPut(resName) { mutableSetOf() }.add(ref)
                }
            }
        }
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value
        if (!value.contains('@')) return

        val owner = attribute.ownerElement as? Element ?: return
        val resName = getResourceName(owner) ?: return

        extractReferences(value).forEach { ref ->
            dependencies.getOrPut(resName) { mutableSetOf() }.add(ref)
        }
    }

    override fun afterCheckRootProject(context: Context) {
        val visited = mutableSetOf<String>()
        val path = mutableSetOf<String>()
        val pathList = mutableListOf<String>()

        for (node in dependencies.keys.toList()) {
            if (node !in visited) {
                findCycles(node, visited, path, pathList, context)
            }
        }
    }

    private fun findCycles(
        node: String,
        visited: MutableSet<String>,
        path: MutableSet<String>,
        pathList: MutableList<String>,
        context: Context
    ) {
        if (node in path) {
            val cycleStart = pathList.indexOf(node)
            if (cycleStart != -1) {
                val cycle = pathList.subList(cycleStart, pathList.size) + node
                val message = "Cycle in resource definitions: ${cycle.joinToString(" -> ")}"
                val location = locations[node]
                if (location != null) {
                    context.report(ISSUE, location, message)
                }
            }
            return
        }
        if (node in visited) return

        visited.add(node)
        path.add(node)
        pathList.add(node)

        for (neighbor in dependencies[node].orEmpty()) {
            findCycles(neighbor, visited, path, pathList, context)
        }

        path.remove(node)
        pathList.removeAt(pathList.size - 1)
    }
}