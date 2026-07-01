package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*

private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
private const val ATTR_NAME = "name"
private const val ATTR_TYPE = "type"
private const val ATTR_PARENT = "parent"

private val RESOURCE_REFERENCE_REGEX =
    "(?<!\\\\)[@?](?:([a-zA-Z0-9_.-]+):)?([a-zA-Z0-9_.-]+)/([a-zA-Z0-9_.-]+)".toRegex()

class ResourceCycleDetector : ResourceXmlDetector(), XmlScanner {

    private val graph = mutableMapOf<Resource, MutableList<Resource>>()
    private val locations = mutableMapOf<Resource, Location>()
    private val reported = mutableSetOf<Resource>()

    override fun getApplicableElements(): Collection<String>? = listOf("resources")
    override fun getApplicableAttributes(): Collection<String>? = emptyList()

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.VALUES

    override fun beforeCheckRootProject(context: Context) {
        graph.clear()
        locations.clear()
        reported.clear()
    }

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        if (element.tagName != "resources") return
        processElement(context, element, null)
    }

    override fun visitAttribute(context: XmlContext, attribute: org.w3c.dom.Attr) {
        // References are gathered during element traversal.
    }

    override fun afterCheckRootProject(context: Context) {
        for (node in findCycleNodes()) {
            if (reported.add(node)) {
                val location = locations[node] ?: continue
                context.report(
                    ISSUE,
                    location,
                    "Cycle in resource definitions involving `@${node.type}/${node.name}`"
                )
            }
        }
    }

    private fun processElement(
        context: XmlContext,
        element: org.w3c.dom.Element,
        current: Resource?
    ) {
        val resource = getResource(element) ?: current
        if (resource != null && resource != current) {
            locations[resource] = context.getLocation(element)
        }

        collectReferences(element, resource)

        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
                processElement(context, child as org.w3c.dom.Element, resource)
            }
        }
    }

    private fun collectReferences(element: org.w3c.dom.Element, current: Resource?) {
        if (current == null) return

        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as? org.w3c.dom.Attr ?: continue
            val localName = attr.localName ?: continue
            if (localName == ATTR_NAME) continue
            if (localName == ATTR_TYPE && attr.namespaceURI.isNullOrEmpty()) continue
            val value = attr.value ?: continue
            addReferences(current, value)
        }

        if (element.tagName == "style") {
            val parent = element.getAttribute(ATTR_PARENT)
            if (parent.isNotEmpty() && !parent.startsWith("@") && !parent.startsWith("?")) {
                addEdge(current, Resource("style", parent))
            }
        }

        val text = element.textContent ?: ""
        if (text.isNotEmpty()) {
            addReferences(current, text)
        }
    }

    private fun getResource(element: org.w3c.dom.Element): Resource? {
        if (!isResourceDeclaration(element)) return null
        val name = element.getAttribute(ATTR_NAME).takeIf { it.isNotEmpty() } ?: return null
        val type = if (element.tagName == "item") {
            element.getAttribute(ATTR_TYPE).takeIf { it.isNotEmpty() } ?: return null
        } else {
            canonicalType(element.tagName)
        }
        return Resource(type, name)
    }

    private fun isResourceDeclaration(element: org.w3c.dom.Element): Boolean {
        if (element.getAttribute(ATTR_NAME).isEmpty()) return false
        val tag = element.tagName
        return tag != "item" || element.hasAttribute(ATTR_TYPE)
    }

    private fun canonicalType(tag: String): String = when (tag) {
        "declare-styleable" -> "styleable"
        "string-array", "integer-array" -> "array"
        else -> tag
    }

    private fun addReferences(current: Resource, value: String) {
        for (match in RESOURCE_REFERENCE_REGEX.findAll(value)) {
            val type = match.groupValues[2]
            val name = match.groupValues[3]
            addEdge(current, Resource(type, name))
        }
    }

    private fun addEdge(from: Resource, to: Resource) {
        graph.getOrPut(from) { mutableListOf() }.add(to)
    }

    private fun findCycleNodes(): Set<Resource> {
        var index = 0
        val stack = mutableListOf<Resource>()
        val onStack = mutableSetOf<Resource>()
        val indices = mutableMapOf<Resource, Int>()
        val lowlinks = mutableMapOf<Resource, Int>()
        val cycleNodes = mutableSetOf<Resource>()

        fun strongconnect(v: Resource) {
            indices[v] = index
            lowlinks[v] = index
            index++
            stack.add(v)
            onStack.add(v)

            for (w in graph[v] ?: emptyList()) {
                if (w !in indices) {
                    strongconnect(w)
                    lowlinks[v] = minOf(lowlinks.getValue(v), lowlinks.getValue(w))
                } else if (w in onStack) {
                    lowlinks[v] = minOf(lowlinks.getValue(v), indices.getValue(w))
                }
            }

            if (lowlinks.getValue(v) == indices.getValue(v)) {
                val component = mutableSetOf<Resource>()
                while (true) {
                    val w = stack.removeAt(stack.lastIndex)
                    onStack.remove(w)
                    component.add(w)
                    if (w == v) break
                }
                if (component.size > 1 || graph[v]?.contains(v) == true) {
                    cycleNodes.addAll(component)
                }
            }
        }

        for (v in graph.keys) {
            if (v !in indices) strongconnect(v)
        }

        return cycleNodes
    }

    private data class Resource(val type: String, val name: String)

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ResourceCycle",
            briefDescription = "Cycle in resource definitions",
            explanation = "There should be no cycles in resource definitions as this can lead to " +
                "runtime exceptions.",
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.FATAL,
            implementation = Implementation(
                ResourceCycleDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}