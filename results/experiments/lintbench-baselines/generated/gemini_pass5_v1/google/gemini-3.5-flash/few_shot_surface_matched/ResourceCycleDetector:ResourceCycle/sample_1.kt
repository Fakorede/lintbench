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

    private val dependencies = mutableMapOf<String, MutableList<Dependency>>()
    private val locations = mutableMapOf<String, Location>()

    private data class Dependency(val to: String, val location: Location)

    override fun beforeCheckRootProject(context: Context) {
        dependencies.clear()
        locations.clear()
    }

    override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean {
        return true
    }

    override fun getApplicableElements(): Collection<String>? {
        return XmlScanner.ALL
    }

    override fun getApplicableAttributes(): Collection<String>? {
        return XmlScanner.ALL
    }

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        val currentRes = getCurrentResource(context, element) ?: return

        if (currentRes !in locations) {
            locations[currentRes] = context.getLocation(element)
        }

        if (element.tagName == "style" && context.resourceFolderType == com.android.resources.ResourceFolderType.VALUES) {
            val parent = element.getAttribute("parent")
            if (parent.isNotEmpty()) {
                val parentRef = if (parent.startsWith("@")) normalizeResource(parent) else "style/$parent"
                if (parentRef != null) {
                    val attrNode = element.getAttributeNode("parent")
                    val loc = if (attrNode != null) context.getLocation(attrNode) else context.getLocation(element)
                    addDependency(currentRes, parentRef, loc)
                }
            }
        }

        val childNodes = element.childNodes
        if (childNodes.length == 1 && childNodes.item(0).nodeType == org.w3c.dom.Node.TEXT_NODE) {
            val text = childNodes.item(0).nodeValue
            val ref = normalizeResource(text)
            if (ref != null) {
                addDependency(currentRes, ref, context.getLocation(element))
            }
        }
    }

    override fun visitAttribute(context: XmlContext, attribute: org.w3c.dom.Attr) {
        val element = attribute.ownerElement ?: return
        val currentRes = getCurrentResource(context, element) ?: return

        if (currentRes !in locations) {
            locations[currentRes] = context.getLocation(element)
        }

        val value = attribute.value
        val ref = normalizeResource(value)
        if (ref != null) {
            if (element.tagName == "style" && attribute.name == "parent") {
                return
            }
            addDependency(currentRes, ref, context.getLocation(attribute))
        }
    }

    override fun afterCheckRootProject(context: Context) {
        val visited = mutableSetOf<String>()
        val stack = mutableSetOf<String>()
        val path = mutableListOf<String>()

        fun dfs(node: String): Boolean {
            if (node in stack) {
                reportCycle(context, node, path)
                return true
            }
            if (node in visited) return false

            visited.add(node)
            stack.add(node)
            path.add(node)

            val deps = dependencies[node]
            if (deps != null) {
                for (dep in deps) {
                    if (dfs(dep.to)) {
                        return true
                    }
                }
            }

            path.removeAt(path.size - 1)
            stack.remove(node)
            return false
        }

        for (node in dependencies.keys) {
            if (node !in visited) {
                dfs(node)
            }
        }

        dependencies.clear()
        locations.clear()
    }

    private fun getCurrentResource(context: XmlContext, element: org.w3c.dom.Element): String? {
        val folderType = context.resourceFolderType ?: return null
        if (folderType == com.android.resources.ResourceFolderType.VALUES) {
            val tagName = element.tagName
            val name = element.getAttribute("name")
            if (name.isNotEmpty()) {
                val type = if (tagName == "item") element.getAttribute("type") else tagName
                if (type.isNotEmpty()) {
                    return "$type/$name"
                }
            }
            val parentNode = element.parentNode
            if (parentNode is org.w3c.dom.Element && parentNode.tagName == "style") {
                val styleName = parentNode.getAttribute("name")
                if (styleName.isNotEmpty()) {
                    return "style/$styleName"
                }
            }
            return null
        } else {
            val type = folderType.getName()
            val name = context.file.nameWithoutExtension
            return "$type/$name"
        }
    }

    private fun normalizeResource(ref: String): String? {
        val trimmed = ref.trim()
        if (!trimmed.startsWith("@")) return null
        if (trimmed.startsWith("@+id") || trimmed.startsWith("@id") || trimmed.startsWith("@android:")) return null
        val clean = trimmed.substring(1)
        if ('/' !in clean) return null
        return clean
    }

    private fun addDependency(from: String, to: String, location: Location) {
        if (from != to) {
            dependencies.getOrPut(from) { mutableListOf() }.add(Dependency(to, location))
        }
    }

    private fun reportCycle(context: Context, startNode: String, path: List<String>) {
        val cycleStartIndex = path.indexOf(startNode)
        if (cycleStartIndex == -1) return
        val cyclePath = path.subList(cycleStartIndex, path.size) + startNode
        val pathString = cyclePath.joinToString(" -> ") { "@$it" }

        val location = locations[startNode] ?: return
        context.report(
            ISSUE,
            location,
            "Cycle detected in resource definitions: $pathString"
        )
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