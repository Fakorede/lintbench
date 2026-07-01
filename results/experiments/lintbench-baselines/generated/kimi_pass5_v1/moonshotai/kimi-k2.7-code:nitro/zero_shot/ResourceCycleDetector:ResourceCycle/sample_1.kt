package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.ResourceUrl
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.util.regex.Pattern

class ResourceCycleDetector : ResourceXmlDetector() {

    private val graph = mutableMapOf<ResourceNode, MutableSet<ResourceNode>>()
    private val locations = mutableMapOf<ResourceNode, Location>()
    private val reportedCycles = mutableSetOf<List<String>>()

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType != ResourceFolderType.RAW
    }

    override fun beforeCheckProject(context: Context) {
        graph.clear()
        locations.clear()
        reportedCycles.clear()
    }

    override fun visitDocument(context: XmlContext) {
        val folderType = context.resourceFolderType ?: return
        if (folderType == ResourceFolderType.RAW) return

        if (folderType == ResourceFolderType.VALUES) {
            visitValuesDocument(context)
        } else {
            visitFileResourceDocument(context, folderType)
        }
    }

    private fun visitValuesDocument(context: XmlContext) {
        val root = context.document.documentElement ?: return
        var child = root.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                val element = child as Element
                val type = getValueResourceType(element) ?: run {
                    child = child.nextSibling
                    continue
                }
                val name = element.getAttribute("name")
                if (name.isBlank()) {
                    child = child.nextSibling
                    continue
                }
                val node = ResourceNode(type, name)
                val nameAttr = element.getAttributeNode("name")
                locations[node] = if (nameAttr != null) {
                    context.getLocation(nameAttr)
                } else {
                    context.getLocation(element)
                }
                scanElementForReferences(element, node)
                addStyleParentEdges(element, node)
            }
            child = child.nextSibling
        }
    }

    private fun visitFileResourceDocument(context: XmlContext, folderType: ResourceFolderType) {
        val type = folderType.getResourceType() ?: return
        val name = context.file.nameWithoutExtension
        if (name.isBlank()) return
        val node = ResourceNode(type, name)
        locations[node] = Location.create(context.file)
        val root = context.document.documentElement ?: return
        scanElementForReferences(root, node)
    }

    private fun getValueResourceType(element: Element): ResourceType? {
        val tag = element.tagName
        if (tag == "item") {
            val typeAttr = element.getAttribute("type")
            if (typeAttr.isNotBlank()) {
                return ResourceType.fromXmlTagName(typeAttr) ?: ResourceType.getEnum(typeAttr)
            }
        }
        return ResourceType.fromXmlTagName(tag) ?: ResourceType.getEnum(tag)
    }

    private fun scanElementForReferences(element: Element, source: ResourceNode) {
        val attrs = element.attributes
        for (i in 0 until attrs.length) {
            val attr = attrs.item(i)
            addReferences(attr.nodeValue, source)
        }

        val text = element.textContent
        if (text.isNotBlank()) {
            addReferences(text, source)
        }

        var child = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                scanElementForReferences(child as Element, source)
            }
            child = child.nextSibling
        }
    }

    private fun addStyleParentEdges(element: Element, source: ResourceNode) {
        if (element.tagName != "style") return
        val parentValue = element.getAttribute("parent")
        if (parentValue.isNotBlank() && !parentValue.startsWith("@") && !parentValue.startsWith("?")) {
            graph.getOrPut(source) { mutableSetOf() }.add(ResourceNode(ResourceType.STYLE, parentValue))
        } else if (parentValue.isBlank()) {
            val name = element.getAttribute("name")
            if (name.contains(".")) {
                val parentName = name.substringBeforeLast(".")
                if (parentName.isNotBlank()) {
                    graph.getOrPut(source) { mutableSetOf() }.add(ResourceNode(ResourceType.STYLE, parentName))
                }
            }
        }
    }

    private fun addReferences(value: String, source: ResourceNode) {
        val matcher = REFERENCE_PATTERN.matcher(value)
        while (matcher.find()) {
            val candidate = matcher.group()
            val url = ResourceUrl.parse(candidate) ?: continue
            if (url.isFramework) continue
            val target = ResourceNode(url.type, url.name)
            graph.getOrPut(source) { mutableSetOf() }.add(target)
        }
    }

    override fun afterCheckProject(context: Context) {
        val visited = mutableSetOf<ResourceNode>()
        val stack = mutableSetOf<ResourceNode>()
        val stackList = mutableListOf<ResourceNode>()

        fun visit(node: ResourceNode) {
            visited += node
            stack += node
            stackList += node

            for (next in graph[node].orEmpty()) {
                if (next !in visited) {
                    visit(next)
                } else if (next in stack) {
                    val startIndex = stackList.indexOf(next)
                    val cycle = stackList.subList(startIndex, stackList.size)
                    reportCycle(context, cycle)
                }
            }

            stack -= node
            stackList.removeAt(stackList.lastIndex)
        }

        for (node in graph.keys) {
            if (node !in visited) {
                visit(node)
            }
        }
    }

    private fun reportCycle(context: Context, cycle: List<ResourceNode>) {
        val signature = cycle.map { it.toString() }.sorted()
        if (signature in reportedCycles) return
        reportedCycles.add(signature)

        val message = buildString {
            append("Resource cycle detected: ")
            append(cycle.joinToString(" -> ") { "${it.type.getName()}/${it.name}" })
            append(" -> ")
            append("${cycle.first().type.getName()}/${cycle.first().name}")
        }

        var primary: Location? = null
        var current: Location? = null
        for (node in cycle) {
            val location = locations[node] ?: continue
            if (primary == null) {
                primary = location
                current = location
            } else {
                current!!.next = location
                current = location
            }
        }

        if (primary != null) {
            context.report(ISSUE, primary, message)
        }
    }

    private data class ResourceNode(val type: ResourceType, val name: String) {
        override fun toString(): String = "${type.getName()}/$name"
    }

    companion object {
        private val REFERENCE_PATTERN = Pattern.compile(
            "(?:@(?:\\*android:|android:)?(\\+?)([a-zA-Z0-9_]+)/([a-zA-Z0-9_.]+)" +
                    "|\\?(?:android:)?([a-zA-Z0-9_]+)/([a-zA-Z0-9_.]+))"
        )

        val ISSUE = Issue.create(
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
}