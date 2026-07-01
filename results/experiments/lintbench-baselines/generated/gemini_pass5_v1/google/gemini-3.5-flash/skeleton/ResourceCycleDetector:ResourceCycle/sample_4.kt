package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.ResourceXmlDetector
import org.w3c.dom.Attr
import org.w3c.dom.Element

class ResourceCycleDetector : ResourceXmlDetector() {

    private val nodes = mutableMapOf<String, ResourceNode>()

    class ResourceNode(
        val key: String,
        val locations: MutableList<com.android.tools.lint.detector.api.Location> = mutableListOf(),
        val references: MutableSet<String> = mutableSetOf()
    )

    companion object {
        private val IMPLEMENTATION = Implementation(
            ResourceCycleDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        private val RESOURCE_REF_REGEX = Regex("[?@](?:[a-zA-Z0-9_.]+:)?([a-zA-Z0-9_]+)/([a-zA-Z0-9_.]+)")

        @JvmField
        val ISSUE = Issue.create(
            id = "ResourceCycle",
            briefDescription = "Cycle in resource definitions",
            explanation = """
                There should be no cycles in resource definitions as this can lead to \
                runtime exceptions. For example, a style cannot have itself or one of \
                its subclasses as a parent, and a color or dimension resource cannot \
                transitively refer to itself.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.FATAL,
            implementation = IMPLEMENTATION,
        )
    }

    override fun beforeCheckRootProject(context: Context) {
        nodes.clear()
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf(
            "style", "color", "dimen", "string", "integer", "bool", "item",
            "array", "string-array", "integer-array", "plurals", "dimen",
            "fraction", "drawable", "attr"
        )
    }

    override fun getApplicableAttributes(): Collection<String>? = null

    override fun visitElement(context: XmlContext, element: Element) {
        val parent = element.parentNode as? Element
        if (parent != null && parent.tagName == "resources") {
            val tagName = element.tagName
            var type = tagName
            val name = element.getAttribute("name")
            if (name.isNullOrEmpty()) return

            if (tagName == "item") {
                val typeAttr = element.getAttribute("type")
                if (!typeAttr.isNullOrEmpty()) {
                    type = typeAttr
                }
            }

            val key = "$type/$name"
            val location = context.getLocation(element)
            
            val node = nodes.getOrPut(key) { ResourceNode(key) }
            node.locations.add(location)
            
            val isStyle = type == "style"
            extractReferences(element, node.references, isStyle)
        }
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        // No-op
    }

    override fun afterCheckRootProject(context: Context) {
        val visited = mutableSetOf<String>()
        val stack = mutableSetOf<String>()
        val path = mutableListOf<String>()

        fun dfs(current: String): Boolean {
            if (current in stack) {
                val cycleStartIndex = path.indexOf(current)
                if (cycleStartIndex != -1) {
                    val cyclePath = path.subList(cycleStartIndex, path.size) + current
                    reportCycle(context, cyclePath)
                    return true
                }
                return false
            }
            if (current in visited) {
                return false
            }

            visited.add(current)
            stack.add(current)
            path.add(current)

            val node = nodes[current]
            if (node != null) {
                for (ref in node.references) {
                    if (dfs(ref)) {
                        return true
                    }
                }
            }

            path.removeAt(path.size - 1)
            stack.remove(current)
            return false
        }

        for (key in nodes.keys) {
            if (key !in visited) {
                dfs(key)
            }
        }
    }

    private fun extractReferences(element: Element, references: MutableSet<String>, isStyle: Boolean) {
        val name = element.getAttribute("name")
        if (isStyle && !element.hasAttribute("parent") && !name.isNullOrEmpty() && name.contains('.')) {
            val lastDot = name.lastIndexOf('.')
            val parentName = name.substring(0, lastDot)
            if (parentName.isNotEmpty()) {
                references.add("style/$parentName")
            }
        }

        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as Attr
            if (isStyle && attr.name == "parent") {
                val parentVal = attr.value
                if (parentVal.isNotEmpty()) {
                    val refKey = normalizeResourceRef(parentVal, "style")
                    if (refKey.isNotEmpty() && refKey != "style/") {
                        references.add(refKey)
                    }
                }
            } else {
                findResourceRefs(attr.value, references)
            }
        }

        val childNodes = element.childNodes
        for (i in 0 until childNodes.length) {
            val child = childNodes.item(i)
            if (child.nodeType == org.w3c.dom.Node.TEXT_NODE) {
                findResourceRefs(child.nodeValue, references)
            } else if (child.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
                extractReferences(child as Element, references, false)
            }
        }
    }

    private fun normalizeResourceRef(ref: String, defaultType: String): String {
        if (ref.startsWith("@") || ref.startsWith("?")) {
            val match = RESOURCE_REF_REGEX.find(ref)
            if (match != null) {
                val type = match.groups[1]?.value ?: defaultType
                val name = match.groups[2]?.value ?: ""
                return "$type/$name"
            }
        }
        val cleanRef = ref.substringAfter(':')
        if (cleanRef.isEmpty()) return ""
        return "$defaultType/$cleanRef"
    }

    private fun findResourceRefs(text: String?, references: MutableSet<String>) {
        if (text == null) return
        RESOURCE_REF_REGEX.findAll(text).forEach { match ->
            val type = match.groups[1]?.value
            val name = match.groups[2]?.value
            if (type != null && name != null) {
                references.add("$type/$name")
            }
        }
    }

    private fun reportCycle(context: Context, cyclePath: List<String>) {
        val definedNode = cyclePath.firstNotNullOfOrNull { nodes[it] } ?: return
        val location = definedNode.locations.firstOrNull() ?: return
        
        val pathString = cyclePath.joinToString(" -> ")
        val message = "Cycle detected in resource definitions: $pathString"
        
        context.report(
            ISSUE,
            location,
            message
        )
    }
}