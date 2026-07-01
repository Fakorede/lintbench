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
import org.w3c.dom.Attr
import org.w3c.dom.Element

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
            explanation = """
                Resource definitions can refer to other resources (for example, a drawable alias
                that points to another drawable, or a style that inherits from a parent style).
                If these references form a cycle, the Android resource manager can fail at runtime
                when trying to resolve the resource. Make sure resource definitions do not
                transitively depend on themselves.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.FATAL,
            implementation = IMPLEMENTATION,
        )
    }

    private data class ResourceKey(val type: String, val name: String)

    private val references = mutableMapOf<ResourceKey, MutableList<ResourceKey>>()
    private val locations = mutableMapOf<ResourceKey, Location>()

    override fun beforeCheckRootProject(context: Context) {
        references.clear()
        locations.clear()
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean = true

    override fun getApplicableElements(): Collection<String>? = null

    override fun getApplicableAttributes(): Collection<String>? = listOf("parent", "layout")

    override fun visitElement(context: XmlContext, element: Element) {
        when (context.resourceFolderType) {
            ResourceFolderType.VALUES -> visitValueElement(context, element)
            ResourceFolderType.LAYOUT -> {
                if (element === element.ownerDocument.documentElement) {
                    val name = context.file.name.removeSuffix(".xml")
                    locations[ResourceKey("layout", name)] = context.getLocation(element)
                }
            }
            else -> { /* not needed for basic resource-cycle detection */ }
        }
    }

    private fun visitValueElement(context: XmlContext, element: Element) {
        val tag = element.tagName
        if (tag == "resources") {
            return
        }

        val name = element.getAttribute("name").takeIf { it.isNotBlank() } ?: return
        val type = if (tag == "item") {
            element.getAttribute("type").takeIf { it.isNotBlank() } ?: return
        } else {
            tag
        }

        val key = ResourceKey(type, name)
        locations[key] = context.getLocation(element)

        if (type == "style") {
            val parent = element.getAttribute("parent").takeIf { it.isNotBlank() }
            if (parent == null) {
                val dot = name.lastIndexOf('.')
                if (dot > 0) {
                    addEdge(key, ResourceKey("style", name.substring(0, dot)))
                }
            }
            return
        }

        val text = element.textContent?.trim().orEmpty()
        if (text.isNotEmpty()) {
            addReference(key, text)
        }
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value?.trim() ?: return
        if (value.isEmpty()) {
            return
        }

        val owner = attribute.ownerElement ?: return
        when (attribute.name) {
            "parent" -> {
                if (owner.tagName != "style") {
                    return
                }
                val styleName = owner.getAttribute("name").takeIf { it.isNotBlank() } ?: return
                val from = ResourceKey("style", styleName)
                locations[from] = context.getLocation(owner)

                if (value.startsWith("@") || value.startsWith("?")) {
                    val url = ResourceUrl.parse(value) ?: return
                    if (url.framework || url.type != "style") {
                        return
                    }
                    addEdge(from, ResourceKey(url.type, url.name))
                } else {
                    addEdge(from, ResourceKey("style", stripPackage(value)))
                }
            }
            "layout" -> {
                if (owner.tagName != "include") {
                    return
                }
                val fileName = context.file.name.removeSuffix(".xml")
                val from = ResourceKey("layout", fileName)

                val root = context.document.documentElement
                if (root != null && from !in locations) {
                    locations[from] = context.getLocation(root)
                }

                val url = ResourceUrl.parse(value) ?: return
                if (url.framework || url.type != "layout") {
                    return
                }
                addEdge(from, ResourceKey(url.type, url.name))
            }
        }
    }

    override fun afterCheckRootProject(context: Context) {
        val graph = buildDeclaredGraph()
        val sccs = findSccs(graph)

        for (scc in sccs) {
            if (!isCyclic(scc, graph)) {
                continue
            }

            val first = scc.first()
            val primary = locations[first] ?: continue
            val secondaries = scc.drop(1).mapNotNull { locations[it] }

            val cycle = scc.joinToString(" -> ") { "${it.type}/${it.name}" }
            val message = "Cycle in resource definitions: $cycle -> ${first.type}/${first.name}"

            val location = if (secondaries.isNotEmpty()) {
                primary.withSecondary(secondaries)
            } else {
                primary
            }

            context.report(ISSUE, location, message)
        }
    }

    private fun buildDeclaredGraph(): Map<ResourceKey, List<ResourceKey>> {
        val declared = locations.keys
        return references
            .mapValues { (_, targets) -> targets.filter { it in declared } }
            .filter { (source, _) -> source in declared }
    }

    private fun findSccs(graph: Map<ResourceKey, List<ResourceKey>>): List<List<ResourceKey>> {
        val index = mutableMapOf<ResourceKey, Int>()
        val lowlink = mutableMapOf<ResourceKey, Int>()
        val onStack = mutableSetOf<ResourceKey>()
        val stack = ArrayDeque<ResourceKey>()
        var nextIndex = 0
        val sccs = mutableListOf<List<ResourceKey>>()

        fun strongconnect(v: ResourceKey) {
            index[v] = nextIndex
            lowlink[v] = nextIndex
            nextIndex++
            stack.addLast(v)
            onStack.add(v)

            for (w in graph[v].orEmpty()) {
                if (w !in index) {
                    strongconnect(w)
                    lowlink[v] = minOf(lowlink[v]!!, lowlink[w]!!)
                } else if (w in onStack) {
                    lowlink[v] = minOf(lowlink[v]!!, index[w]!!)
                }
            }

            if (lowlink[v] == index[v]) {
                val component = mutableListOf<ResourceKey>()
                do {
                    val w = stack.removeLast()
                    onStack.remove(w)
                    component.add(w)
                } while (w != v)
                sccs.add(component)
            }
        }

        for (v in graph.keys) {
            if (v !in index) {
                strongconnect(v)
            }
        }

        return sccs
    }

    private fun isCyclic(scc: List<ResourceKey>, graph: Map<ResourceKey, List<ResourceKey>>): Boolean {
        if (scc.size > 1) {
            return true
        }
        val node = scc.single()
        return graph[node]?.contains(node) == true
    }

    private fun addReference(from: ResourceKey, value: String) {
        val url = ResourceUrl.parse(value) ?: return
        if (url.framework) {
            return
        }
        if (url.type != from.type) {
            return
        }
        addEdge(from, ResourceKey(url.type, url.name))
    }

    private fun addEdge(from: ResourceKey, to: ResourceKey) {
        references.getOrPut(from) { mutableListOf() }.add(to)
    }

    private fun stripPackage(value: String): String {
        val colon = value.indexOf(':')
        return if (colon != -1) value.substring(colon + 1) else value
    }
}