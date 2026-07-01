package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
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
import com.android.tools.lint.detector.api.XmlScannerConstants
import org.w3c.dom.Attr
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.util.Locale

class ResourceCycleDetector : Detector(), XmlScanner {

    private data class NodeKey(val type: ResourceType, val name: String)

    private data class Edge(val to: NodeKey, val location: Location)

    private val graph = mutableMapOf<NodeKey, MutableList<Edge>>()
    private val definitions = mutableMapOf<NodeKey, Location>()
    private val stack = mutableListOf<NodeKey>()

    override fun getApplicableElements(): Collection<String>? = XmlScannerConstants.ALL

    override fun beforeCheckProject(context: Context) {
        graph.clear()
        definitions.clear()
        stack.clear()
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val node = getNode(context, element)
        if (node != null) {
            definitions[node] = context.getLocation(element)
            stack.add(node)
        }

        val current = stack.lastOrNull() ?: return

        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as Attr
            val ns = attr.namespaceURI
            if (ns == SdkConstants.TOOLS_URI || ns == SdkConstants.XMLNS_URI) {
                continue
            }
            addAttributeReference(context, current, attr)
        }

        if (context.resourceFolderType == ResourceFolderType.VALUES) {
            val children = element.childNodes
            for (i in 0 until children.length) {
                val child = children.item(i)
                if (child.nodeType == Node.TEXT_NODE || child.nodeType == Node.CDATA_SECTION_NODE) {
                    val text = child.nodeValue?.trim() ?: continue
                    if (text.isNotEmpty()) {
                        addReferenceValue(context, current, text, context.getLocation(element))
                    }
                }
            }
        }
    }

    override fun visitElementAfter(context: XmlContext, element: Element) {
        val node = getNode(context, element)
        if (node != null && stack.isNotEmpty() && stack.last() == node) {
            stack.removeAt(stack.size - 1)
        }
    }

    override fun afterCheckProject(context: Context) {
        detectCycles(context)
    }

    private fun getNode(context: XmlContext, element: Element): NodeKey? {
        val folderType = context.resourceFolderType ?: return null
        return when (folderType) {
            ResourceFolderType.VALUES -> getValueNode(element)
            else -> {
                if (element.parentNode?.nodeType == Node.DOCUMENT_NODE) {
                    val type = ResourceType.getEnum(folderType.name) ?: return null
                    val name = context.file.nameWithoutExtension
                    NodeKey(type, name)
                } else {
                    null
                }
            }
        }
    }

    private fun getValueNode(element: Element): NodeKey? {
        val name = element.getAttributeNS(null, SdkConstants.ATTR_NAME).takeIf { it.isNotEmpty() } ?: return null
        val tag = element.tagName
        val type = when (tag) {
            "string" -> ResourceType.STRING
            "plurals" -> ResourceType.PLURALS
            "string-array", "integer-array", "array" -> ResourceType.ARRAY
            "integer" -> ResourceType.INTEGER
            "bool" -> ResourceType.BOOL
            "color" -> ResourceType.COLOR
            "dimen" -> ResourceType.DIMEN
            "fraction" -> ResourceType.FRACTION
            "style" -> ResourceType.STYLE
            "attr" -> ResourceType.ATTR
            "declare-styleable" -> ResourceType.STYLEABLE
            "drawable" -> ResourceType.DRAWABLE
            "id" -> ResourceType.ID
            "item" -> {
                val typeAttr = element.getAttributeNS(null, "type").takeIf { it.isNotEmpty() } ?: return null
                ResourceType.getEnum(typeAttr.uppercase(Locale.ROOT)) ?: return null
            }
            else -> return null
        }
        if (type == ResourceType.ID) {
            return null
        }
        return NodeKey(type, name)
    }

    private fun addAttributeReference(context: XmlContext, from: NodeKey, attr: Attr) {
        val value = attr.value ?: return
        val attrName = attr.name

        if (from.type == ResourceType.STYLE &&
            (attrName == "parent" || attrName.endsWith(":parent")) &&
            value.isNotEmpty() &&
            !value.startsWith("@") &&
            !value.startsWith("?") &&
            !value.contains(":")
        ) {
            addEdge(from, NodeKey(ResourceType.STYLE, value), context.getLocation(attr))
            return
        }

        addReferenceValue(context, from, value, context.getLocation(attr))
    }

    private fun addReferenceValue(context: XmlContext, from: NodeKey, value: String, location: Location) {
        if (!value.startsWith("@")) {
            return
        }
        val url = ResourceUrl.parse(value) ?: return
        if (url.packageName != null) {
            return
        }
        val type = url.type ?: return
        val name = url.name ?: return
        if (type == ResourceType.ID) {
            return
        }
        addEdge(from, NodeKey(type, name), location)
    }

    private fun addEdge(from: NodeKey, to: NodeKey, location: Location) {
        graph.getOrPut(from) { mutableListOf() }.add(Edge(to, location))
    }

    private fun detectCycles(context: Context) {
        val allNodes = definitions.keys.toMutableSet().apply { addAll(graph.keys) }
        val gray = mutableSetOf<NodeKey>()
        val black = mutableSetOf<NodeKey>()
        val path = mutableListOf<NodeKey>()

        fun dfs(node: NodeKey) {
            gray.add(node)
            path.add(node)
            val edges = graph[node]
            if (edges != null) {
                for (edge in edges) {
                    val next = edge.to
                    if (next in gray) {
                        val start = path.indexOf(next)
                        val cycle = path.subList(start, path.size) + next
                        reportCycle(context, edge, cycle)
                    } else if (next !in black) {
                        dfs(next)
                    }
                }
            }
            path.removeAt(path.size - 1)
            gray.remove(node)
            black.add(node)
        }

        for (node in allNodes) {
            if (node !in black && node !in gray) {
                dfs(node)
            }
        }
    }

    private fun reportCycle(context: Context, edge: Edge, cycle: List<NodeKey>) {
        val path = cycle.joinToString(" -> ") { "@${it.type.getName()}/${it.name}" }
        val message = "Cycle in resource definitions: $path"
        context.report(ISSUE, edge.location, message)
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            "ResourceCycle",
            "Cycle in resource definitions",
            "There should be no cycles in resource definitions as this can lead to runtime exceptions.",
            Category.CORRECTNESS,
            8,
            Severity.ERROR,
            Implementation(ResourceCycleDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
        )
    }
}