package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.util.ArrayDeque

class ResourceCycleDetector : Detector(), XmlScanner {

    private val references = mutableMapOf<ResourceRef, MutableSet<ResourceRef>>()
    private val resourceLocations = mutableMapOf<ResourceRef, Location>()

    override fun visitDocument(context: XmlContext, document: Document) {
        val folderType = ResourceFolderType.getFolderType(context.file.parentFile?.name) ?: return
        val root = document.documentElement ?: return

        if (folderType == ResourceFolderType.VALUES) {
            var child = root.firstChild
            while (child != null) {
                if (child.nodeType == Node.ELEMENT_NODE) {
                    val element = child as Element
                    val owner = getValueResourceRef(element)
                    if (owner != null) {
                        resourceLocations[owner] = context.getLocation(element)
                        collectReferences(element, owner)
                    }
                }
                child = child.nextSibling
            }
        } else {
            val type = ResourceType.fromString(folderType.getName()) ?: return
            val name = context.file.name.substringBeforeLast(".")
            val owner = ResourceRef(type, name)
            resourceLocations[owner] = Location.create(context.file)
            collectReferences(root, owner)
        }
    }

    override fun beforeCheckEachProject(context: Context) {
        references.clear()
        resourceLocations.clear()
    }

    override fun afterCheckEachProject(context: Context) {
        reportCycles(context)
    }

    private fun getValueResourceRef(element: Element): ResourceRef? {
        val tag = element.tagName
        val type: ResourceType = when {
            tag == SdkConstants.TAG_ITEM -> {
                val typeAttr = element.getAttribute(SdkConstants.ATTR_TYPE)
                if (typeAttr.isEmpty()) return null
                ResourceType.fromString(typeAttr) ?: return null
            }
            tag.endsWith("array") -> ResourceType.ARRAY
            tag == "declare-styleable" -> ResourceType.STYLEABLE
            else -> ResourceType.fromString(tag) ?: return null
        }

        val name = element.getAttribute(SdkConstants.ATTR_NAME)
        if (name.isEmpty()) return null
        return ResourceRef(type, name)
    }

    private fun collectReferences(element: Element, owner: ResourceRef) {
        if (owner.type == ResourceType.STYLE && element.tagName == SdkConstants.TAG_STYLE) {
            collectStyleParent(element, owner)
        }

        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as? Attr ?: continue
            addStringReferences(owner, attr.value)
        }

        val text = element.textContent
        if (text.isNotBlank()) {
            addStringReferences(owner, text)
        }

        var child = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                collectReferences(child as Element, owner)
            }
            child = child.nextSibling
        }
    }

    private fun collectStyleParent(element: Element, owner: ResourceRef) {
        val parent = element.getAttribute(SdkConstants.ATTR_PARENT)
        if (parent.isNotEmpty() && !parent.startsWith("@") && !parent.startsWith("?")) {
            addEdge(owner, ResourceRef(ResourceType.STYLE, parent))
        } else if (parent.isEmpty()) {
            val dot = owner.name.lastIndexOf('.')
            if (dot > 0) {
                addEdge(owner, ResourceRef(ResourceType.STYLE, owner.name.substring(0, dot)))
            }
        }
    }

    private fun addStringReferences(owner: ResourceRef, text: String) {
        for (match in REFERENCE_REGEX.findAll(text)) {
            val prefix = match.groupValues[1]
            val packageName = match.groupValues[2]
            val typeName = match.groupValues[3]
            val name = match.groupValues[4]

            if (name.isEmpty()) continue
            if (packageName == "android" || packageName.startsWith("*android")) continue
            if (prefix == "@" && typeName.isEmpty()) continue

            val type = if (typeName.isNotEmpty()) {
                ResourceType.fromString(typeName) ?: continue
            } else {
                ResourceType.ATTR
            }

            addEdge(owner, ResourceRef(type, name))
        }
    }

    private fun addEdge(from: ResourceRef, to: ResourceRef) {
        references.getOrPut(from) { mutableSetOf() }.add(to)
    }

    private fun reportCycles(context: Context) {
        val nodes = references.keys.toMutableSet()
        references.values.forEach { nodes.addAll(it) }

        val index = mutableMapOf<ResourceRef, Int>()
        val lowlink = mutableMapOf<ResourceRef, Int>()
        val onStack = mutableSetOf<ResourceRef>()
        val stack = ArrayDeque<ResourceRef>()
        var currentIndex = 0
        val components = mutableListOf<List<ResourceRef>>()

        fun strongconnect(v: ResourceRef) {
            index[v] = currentIndex
            lowlink[v] = currentIndex
            currentIndex++
            stack.addLast(v)
            onStack.add(v)

            for (w in references[v].orEmpty()) {
                if (w !in index) {
                    strongconnect(w)
                    lowlink[v] = minOf(lowlink[v]!!, lowlink[w]!!)
                } else if (w in onStack) {
                    lowlink[v] = minOf(lowlink[v]!!, lowlink[w]!!)
                }
            }

            if (lowlink[v] == index[v]) {
                val component = mutableListOf<ResourceRef>()
                while (true) {
                    val w = stack.removeLast()
                    onStack.remove(w)
                    component.add(w)
                    if (w == v) break
                }
                components.add(component)
            }
        }

        for (v in nodes) {
            if (v !in index) strongconnect(v)
        }

        for (component in components) {
            val isCycle = component.size > 1 ||
                    (component.size == 1 && references[component[0]]?.contains(component[0]) == true)
            if (!isCycle) continue

            val path = buildString {
                append(component.joinToString(" -> ") { "${it.type.getName()}/${it.name}" })
                append(" -> ${component[0].type.getName()}/${component[0].name}")
            }
            val message = "Cycle in resource definitions: $path"

            for (ref in component) {
                val location = resourceLocations[ref] ?: continue
                context.report(ISSUE, location, message)
            }
        }
    }

    private data class ResourceRef(val type: ResourceType, val name: String)

    companion object {
        val ISSUE = Issue.create(
            id = "ResourceCycle",
            briefDescription = "Cycle in resource definitions",
            explanation = """
                There should be no cycles in resource definitions as this can lead to \
                runtime exceptions when the resources are loaded.
            """,
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.FATAL,
            implementation = Implementation(
                ResourceCycleDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private val REFERENCE_REGEX =
            Regex("""([@?])[+*]?(?:([^:/\\s]+):)?(?:([^/\\s]+)/)?([a-zA-Z0-9_.]+)""")
    }
}