package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.resources.ResourceUrl
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScannerConstants
import org.w3c.dom.Attr
import org.w3c.dom.Element
import org.w3c.dom.Node

class ResourceCycleDetector : ResourceXmlDetector() {

    override fun appliesTo(folderType: ResourceFolderType): Boolean = true

    override fun getApplicableElements(): Collection<String>? = XmlScannerConstants.ALL

    override fun visitElement(context: XmlContext, element: Element) {
        val from = getDefiningResource(context, element) ?: return
        val node = graph.getOrPut(from) { Node() }
        if (node.location == null) {
            node.location = context.getLocation(element)
        }

        if (element.tagName == "style") {
            handleStyle(context, element, from)
        }

        for (i in 0 until element.attributes.length) {
            val attr = element.attributes.item(i) as Attr
            if (attr.namespaceURI == TOOLS_NS || attr.namespaceURI == XMLNS_NS) continue
            val value = attr.value
            val location = context.getLocation(attr)
            addReference(from, value, location)
        }

        val hasElementChildren = element.childNodes.let { children ->
            (0 until children.length).any { children.item(it).nodeType == Node.ELEMENT_NODE }
        }
        if (!hasElementChildren) {
            val text = element.textContent
            if (text.isNotBlank()) {
                addReference(from, text, context.getLocation(element))
            }
        }
    }

    override fun beforeCheckProject(context: Context) {
        graph.clear()
        indices.clear()
        lowlinks.clear()
        sccStack.clear()
        onSccStack.clear()
        reportedCycles.clear()
        index = 0
        this.context = context
    }

    override fun afterCheckProject(context: Context) {
        for (v in graph.keys) {
            if (v !in indices) {
                strongconnect(v)
            }
        }
    }

    private fun handleStyle(context: XmlContext, element: Element, from: Resource) {
        val name = element.getAttribute("name")
        val location = context.getLocation(element)

        if (name.contains(".") && !element.hasAttribute("parent")) {
            val parentName = name.substringBeforeLast(".")
            addEdge(from, Resource("style", parentName), location)
        }

        val parentValue = element.getAttribute("parent")
        if (parentValue.isNotBlank() && !parentValue.startsWith("@") && !parentValue.startsWith("?")) {
            addEdge(from, Resource("style", parentValue), location)
        }
    }

    private fun addReference(from: Resource, value: String, location: Location) {
        val url = ResourceUrl.parse(value) ?: return
        if (!url.isReference || url.isFramework) return
        val type = url.type
        val name = url.name
        if (type.isBlank() || name.isBlank()) return
        addEdge(from, Resource(type, name), location)
    }

    private fun addEdge(from: Resource, to: Resource, location: Location) {
        graph.getOrPut(from) { Node() }.outgoing.add(Reference(to, location))
    }

    private fun getDefiningResource(context: XmlContext, element: Element): Resource? {
        getResourceForElement(context, element)?.let { return it }
        var parent = element.parentNode
        while (parent is Element) {
            getResourceForElement(context, parent)?.let { return it }
            parent = parent.parentNode
        }
        return null
    }

    private fun getResourceForElement(context: XmlContext, element: Element): Resource? {
        val folderType = ResourceFolderType.getFolderType(context.file.parentFile) ?: return null
        return if (folderType == ResourceFolderType.VALUES) {
            getValueResource(element)
        } else if (element === element.ownerDocument.documentElement) {
            val type = folderType.name.lowercase()
            val name = context.file.nameWithoutExtension
            Resource(type, name)
        } else {
            null
        }
    }

    private fun getValueResource(element: Element): Resource? {
        val name = element.getAttribute("name").takeIf { it.isNotBlank() } ?: return null
        val tag = element.tagName
        if (tag == "item") {
            val type = element.getAttribute("type").takeIf { it.isNotBlank() } ?: return null
            return Resource(type, name)
        }
        return when (tag) {
            "string", "integer", "bool", "color", "dimen", "drawable",
            "fraction", "style", "attr",
            "array", "string-array", "integer-array", "plurals",
            "id", "layout", "raw", "xml", "menu", "mipmap",
            "transition", "anim", "animator", "interpolator", "font",
            "navigation" -> Resource(tag, name)
            else -> null
        }
    }

    private fun strongconnect(v: Resource) {
        indices[v] = index
        lowlinks[v] = index
        index++
        sccStack.add(v)
        onSccStack.add(v)

        for (ref in graph[v]?.outgoing.orEmpty()) {
            val w = ref.target
            if (w !in indices) {
                strongconnect(w)
                lowlinks[v] = minOf(lowlinks[v]!!, lowlinks[w]!!)
            } else if (w in onSccStack) {
                lowlinks[v] = minOf(lowlinks[v]!!, indices[w]!!)
            }
        }

        if (lowlinks[v] == indices[v]) {
            val scc = mutableListOf<Resource>()
            var w: Resource
            do {
                w = sccStack.removeAt(sccStack.lastIndex)
                onSccStack.remove(w)
                scc.add(w)
            } while (w != v)

            if (scc.size > 1 || hasSelfLoop(v)) {
                reportScc(scc)
            }
        }
    }

    private fun hasSelfLoop(v: Resource): Boolean =
        graph[v]?.outgoing?.any { it.target == v } == true

    private fun reportScc(scc: List<Resource>) {
        val key = scc.map { "${it.type}/${it.name}" }.toSortedSet()
        if (key in reportedCycles) return
        reportedCycles.add(key)

        val message = "Resource cycle detected among: " +
                scc.joinToString(", ") { "@${it.type}/${it.name}" }

        val primary = scc.firstOrNull { graph[it]?.location != null } ?: scc.first()
        val primaryLocation = graph[primary]?.location ?: Location.create(context.file)

        val secondaries = scc.mapNotNull { res ->
            if (res == primary) null else graph[res]?.location
        }
        if (secondaries.isNotEmpty()) {
            primaryLocation.secondary = secondaries
        }

        context.report(ISSUE, primaryLocation, message)
    }

    private data class Resource(val type: String, val name: String)

    private inner class Node {
        val outgoing = mutableListOf<Reference>()
        var location: Location? = null
    }

    private data class Reference(val target: Resource, val location: Location)

    private val graph = mutableMapOf<Resource, Node>()
    private val indices = mutableMapOf<Resource, Int>()
    private val lowlinks = mutableMapOf<Resource, Int>()
    private val sccStack = mutableListOf<Resource>()
    private val onSccStack = mutableSetOf<Resource>()
    private val reportedCycles = mutableSetOf<Set<String>>()
    private var index = 0
    private lateinit var context: Context

    companion object {
        private const val TOOLS_NS = "http://schemas.android.com/tools"
        private const val XMLNS_NS = "http://www.w3.org/2000/xmlns/"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "ResourceCycle",
            briefDescription = "Cycle in resource definitions",
            explanation = """
                There should be no cycles in resource definitions as this can lead to \
                runtime exceptions. For example, a drawable that references another \
                drawable which in turn references the first creates a cycle that cannot \
                be resolved at runtime.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.FATAL,
            implementation = Implementation(
                ResourceCycleDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}