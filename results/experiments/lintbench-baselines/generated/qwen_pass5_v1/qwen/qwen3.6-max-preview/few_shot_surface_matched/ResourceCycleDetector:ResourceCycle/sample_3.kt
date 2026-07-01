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

    private val definitions = mutableMapOf<String, Location>()
    private val dependencies = mutableMapOf<String, MutableSet<String>>()
    private val referenceRegex = Regex("[@?]\\+?(\\w+)/(\\w+)")

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("*")
    }

    override fun getApplicableAttributes(): Collection<String>? {
        return listOf("*")
    }

    override fun beforeCheckRootProject(context: Context) {
        definitions.clear()
        dependencies.clear()
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val nameAttr = element.getAttributeNode(ATTR_NAME) ?: return
        val name = nameAttr.value
        val type = if (element.tagName == "item") {
            element.getAttribute("type")
        } else {
            element.tagName
        }
        if (type.isNotEmpty() && name.isNotEmpty()) {
            val key = "$type/$name"
            definitions[key] = context.getLocation(nameAttr)
            dependencies.getOrPut(key) { mutableSetOf() }
        }
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value
        if (value.isEmpty()) return

        val ownerElement = attribute.ownerElement ?: return
        val ownerName = ownerElement.getAttribute(ATTR_NAME)
        val ownerType = if (ownerElement.tagName == "item") {
            ownerElement.getAttribute("type")
        } else {
            ownerElement.tagName
        }
        if (ownerType.isEmpty() || ownerName.isEmpty()) return

        val ownerKey = "$ownerType/$ownerName"

        referenceRegex.findAll(value).forEach { matchResult ->
            val refType = matchResult.groupValues[1]
            val refName = matchResult.groupValues[2]
            val refKey = "$refType/$refName"
            dependencies.getOrPut(ownerKey) { mutableSetOf() }.add(refKey)
        }
    }

    override fun afterCheckRootProject(context: Context) {
        val visited = mutableSetOf<String>()
        val recStack = mutableSetOf<String>()
        val reported = mutableSetOf<String>()

        fun dfs(node: String, path: MutableList<String>) {
            if (node in recStack) {
                val cycleStart = path.indexOf(node)
                if (cycleStart != -1) {
                    val cycle = path.subList(cycleStart, path.size).toList()
                    val cycleSet = cycle.toSet()
                    if (reported.addAll(cycleSet)) {
                        val cycleStr = cycle.joinToString(" -> ") + " -> $node"
                        for (res in cycle) {
                            definitions[res]?.let { loc ->
                                context.report(ISSUE, loc, "Cycle in resource definitions: $cycleStr")
                            }
                        }
                    }
                }
                return
            }
            if (node in visited) return

            visited.add(node)
            recStack.add(node)
            path.add(node)

            for (ref in dependencies[node].orEmpty()) {
                dfs(ref, path)
            }

            path.removeAt(path.size - 1)
            recStack.remove(node)
        }

        for (node in dependencies.keys.toList()) {
            if (node !in visited) {
                dfs(node, mutableListOf())
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