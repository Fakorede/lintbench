package com.android.tools.lint.checks

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
import org.w3c.dom.Attr
import org.w3c.dom.Element
import org.w3c.dom.Node

class ResourceCycleDetector : ResourceXmlDetector() {

    companion object {
        private val IMPLEMENTATION = Implementation(
            ResourceCycleDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ResourceCycle",
            briefDescription = "Cycle in resource definitions",
            explanation = "Resources that reference each other in a cycle can cause runtime exceptions when the system tries to resolve them. Make sure resource definitions do not form circular dependencies.",
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.FATAL,
            implementation = IMPLEMENTATION,
        )
    }

    private data class ResourceRef(val type: String, val name: String)
    private data class ResourceEdge(val target: ResourceRef, val location: Location)

    private val graph = mutableMapOf<ResourceRef, MutableList<ResourceEdge>>()

    private val resourceRefRegex =
        """@(?:([A-Za-z][\w.]*)\:)?([A-Za-z][\w.]*)/([A-Za-z][\w.]+)""".toRegex()

    override fun beforeCheckRootProject(context: Context) {
        graph.clear()
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType != ResourceFolderType.RAW

    override fun getApplicableElements(): Collection<String>? = null

    override fun getApplicableAttributes(): Collection<String>? = null

    override fun visitElement(context: XmlContext, element: Element) {
        val folderType = context.resourceFolderType
        val ref = getResourceRefForElement(element, folderType, context)
        val currentRef = ref ?: findCurrentDefinition(element, folderType, context) ?: return

        handleStyleParent(context, element, currentRef, folderType)

        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.TEXT_NODE) {
                val text = child.nodeValue ?: continue
                if (text.isBlank()) continue
                addReferences(context, currentRef, text, context.getLocation(element))
            }
        }
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val folderType = context.resourceFolderType
        val owner = attribute.ownerElement ?: return
        val currentRef = findCurrentDefinition(owner, folderType, context) ?: return
        val value = attribute.value ?: return
        if (value.isBlank()) return
        addReferences(context, currentRef, value, context.getLocation(attribute))
    }

    override fun afterCheckRootProject(context: Context) {
        val sccs = findSccs()
        for (scc in sccs) {
            if (scc.size > 1 || hasSelfLoop(scc.first())) {
                reportCycle(context, scc)
            }
        }
        graph.clear()
    }

    private fun getResourceRefForElement(
        element: Element,
        folderType: ResourceFolderType,
        context: XmlContext
    ): ResourceRef? {
        if (folderType == ResourceFolderType.VALUES) {
            val name = element.getAttribute("name")
            if (name.isNullOrBlank()) return null
            val type = getValuesResourceType(element) ?: return null
            return ResourceRef(type, name)
        }

        if (element != element.ownerDocument?.documentElement) return null
        val type = getFolderResourceType(folderType) ?: return null
        val name = getNameFromFileName(context.file.name)
        return ResourceRef(type, name)
    }

    private fun findCurrentDefinition(
        element: Element,
        folderType: ResourceFolderType,
        context: XmlContext
    ): ResourceRef? {
        return if (folderType == ResourceFolderType.VALUES) {
            var node: Node? = element
            while (node != null) {
                if (node is Element) {
                    val ref = getResourceRefForElement(node, folderType, context)
                    if (ref != null) return ref
                }
                node = node.parentNode
            }
            null
        } else {
            element.ownerDocument?.documentElement?.let {
                getResourceRefForElement(it, folderType, context)
            }
        }
    }

    private fun handleStyleParent(
        context: XmlContext,
        element: Element,
        currentRef: ResourceRef,
        folderType: ResourceFolderType
    ) {
        if (folderType != ResourceFolderType.VALUES || element.tagName != "style") return

        val parent = element.getAttribute("parent")
        if (parent.isNotBlank()) {
            if (!parent.startsWith("@")) {
                addReference(currentRef, ResourceRef("style", parent), context.getLocation(element))
            }
            return
        }

        val name = element.getAttribute("name")
        val dot = name.lastIndexOf('.')
        if (dot > 0) {
            addReference(
                currentRef,
                ResourceRef("style", name.substring(0, dot)),
                context.getLocation(element)
            )
        }
    }

    private fun addReferences(
        context: XmlContext,
        source: ResourceRef,
        value: String,
        location: Location
    ) {
        for (match in resourceRefRegex.findAll(value)) {
            val pkg = match.groupValues[1]
            if (pkg.isNotEmpty()) continue
            val type = match.groupValues[2]
            val name = match.groupValues[3]
            if (type.isBlank() || name.isBlank()) continue
            addReference(source, ResourceRef(type, name), location)
        }
    }

    private fun addReference(source: ResourceRef, target: ResourceRef, location: Location) {
        graph.getOrPut(source) { mutableListOf() }.add(ResourceEdge(target, location))
        graph.getOrPut(target) { mutableListOf() }
    }

    private fun getValuesResourceType(element: Element): String? {
        return when (val tag = element.tagName) {
            "item" -> element.getAttribute("type").takeIf { it.isNotBlank() } ?: "item"
            "resources" -> null
            else -> tag
        }
    }

    private fun getFolderResourceType(folderType: ResourceFolderType): String? = when (folderType) {
        ResourceFolderType.ANIM -> "anim"
        ResourceFolderType.ANIMATOR -> "animator"
        ResourceFolderType.COLOR -> "color"
        ResourceFolderType.DRAWABLE -> "drawable"
        ResourceFolderType.FONT -> "font"
        ResourceFolderType.INTERPOLATOR -> "interpolator"
        ResourceFolderType.LAYOUT -> "layout"
        ResourceFolderType.MENU -> "menu"
        ResourceFolderType.MIPMAP -> "mipmap"
        ResourceFolderType.NAVIGATION -> "navigation"
        ResourceFolderType.TRANSITION -> "transition"
        ResourceFolderType.XML -> "xml"
        else -> null
    }

    private fun getNameFromFileName(fileName: String): String {
        val dot = fileName.lastIndexOf('.')
        return if (dot > 0) fileName.substring(0, dot) else fileName
    }

    private fun findSccs(): List<List<ResourceRef>> {
        val index = mutableMapOf<ResourceRef, Int>()
        val lowlink = mutableMapOf<ResourceRef, Int>()
        val stack = ArrayDeque<ResourceRef>()
        val onStack = mutableSetOf<ResourceRef>()
        val sccs = mutableListOf<List<ResourceRef>>()
        var current = 0

        fun strongconnect(node: ResourceRef) {
            index[node] = current
            lowlink[node] = current
            current++
            stack.addLast(node)
            onStack.add(node)

            for (edge in graph[node].orEmpty()) {
                val w = edge.target
                when {
                    w !in index -> {
                        strongconnect(w)
                        lowlink[node] = minOf(lowlink.getValue(node), lowlink.getValue(w))
                    }
                    w in onStack -> {
                        lowlink[node] = minOf(lowlink.getValue(node), index.getValue(w))
                    }
                }
            }

            if (lowlink.getValue(node) == index.getValue(node)) {
                val component = mutableListOf<ResourceRef>()
                do {
                    val w = stack.removeLast()
                    onStack.remove(w)
                    component.add(w)
                } while (w != node)
                sccs.add(component)
            }
        }

        for (node in graph.keys) {
            if (node !in index) {
                strongconnect(node)
            }
        }
        return sccs
    }

    private fun hasSelfLoop(ref: ResourceRef): Boolean =
        graph[ref]?.any { it.target == ref } == true

    private fun reportCycle(context: Context, scc: List<ResourceRef>) {
        val nodes = scc.toSet()
        val edges = graph.entries.flatMap { (source, list) ->
            if (source in nodes) list.filter { it.target in nodes } else emptyList()
        }
        if (edges.isEmpty()) return

        val locations = edges.map { it.location }.distinct()
        val primary = locations.first()
        var tail = primary
        for (i in 1 until locations.size) {
            tail.secondary = locations[i]
            tail = locations[i]
        }

        val message = "Resource cycle detected among: " +
                scc.joinToString(", ") { "@${it.type}/${it.name}" }

        context.report(ISSUE, primary, message)
    }
}