package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.util.regex.Pattern

class ResourceCycleDetector : Detector(), XmlScanner {

    private val references = mutableMapOf<ResourceRef, MutableList<ResourceRef>>()
    private val locations = mutableMapOf<ResourceRef, Location>()

    override fun getApplicableElements(): Collection<String> = XmlScanner.ALL

    override fun beforeCheckEachProject(context: Context) {
        references.clear()
        locations.clear()
    }

    override fun afterCheckEachProject(context: Context) {
        checkCycles(context)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val folderType = context.resourceFolderType ?: return
        val owner = if (folderType == ResourceFolderType.VALUES) {
            val parent = element.parentNode ?: return
            if (parent.nodeType != Node.ELEMENT_NODE || parent.nodeName != SdkConstants.TAG_RESOURCES) {
                return
            }
            getResourceRef(element) ?: return
        } else {
            if (element.parentNode?.nodeType != Node.DOCUMENT_NODE) {
                return
            }
            val type = ResourceType.getEnum(folderType.getName()) ?: return
            val name = Lint.getBaseName(context.file.name)
            ResourceRef(type, name)
        }

        locations[owner] = context.getLocation(element)

        if (owner.type == ResourceType.STYLE && element.tagName == SdkConstants.TAG_STYLE) {
            val parent = element.getAttribute(SdkConstants.ATTR_PARENT)
            if (parent.isEmpty()) {
                val dot = owner.name.lastIndexOf('.')
                if (dot > 0) {
                    addEdge(owner, ResourceRef(ResourceType.STYLE, owner.name.substring(0, dot)))
                }
            } else if (!parent.startsWith(SdkConstants.PREFIX_RESOURCE_REF) && !parent.startsWith(SdkConstants.PREFIX_THEME_REF)) {
                addEdge(owner, ResourceRef(ResourceType.STYLE, parent))
            }
        }

        collectReferences(context, element, owner)
    }

    private fun getResourceRef(element: Element): ResourceRef? {
        val typeName = if (element.tagName == SdkConstants.TAG_ITEM) {
            element.getAttribute(SdkConstants.ATTR_TYPE)
        } else {
            element.tagName
        }
        val type = ResourceType.getEnum(typeName) ?: return null
        val name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
            .ifEmpty { element.getAttribute(SdkConstants.ATTR_NAME) }
        if (name.isEmpty()) return null
        return ResourceRef(type, name)
    }

    private fun collectReferences(context: XmlContext, element: Element, owner: ResourceRef) {
        val attrs = element.attributes
        for (i in 0 until attrs.length) {
            val attr = attrs.item(i) as? Attr ?: continue
            findReferences(attr.value, owner)
        }

        val children = element.childNodes
        for (i in 0 until children.length) {
            when (val child = children.item(i)) {
                is Element -> collectReferences(context, child, owner)
                else -> if (child.nodeType == Node.TEXT_NODE || child.nodeType == Node.CDATA_SECTION_NODE) {
                    findReferences(child.nodeValue, owner)
                }
            }
        }
    }

    private fun findReferences(value: String, owner: ResourceRef) {
        var matcher = REFERENCE_PATTERN.matcher(value)
        while (matcher.find()) {
            val typeName = matcher.group(1) ?: continue
            val name = matcher.group(2) ?: continue
            val type = ResourceType.getEnum(typeName) ?: continue
            addEdge(owner, ResourceRef(type, name))
        }
        matcher = THEME_REF_PATTERN.matcher(value)
        while (matcher.find()) {
            val typeName = matcher.group(1) ?: continue
            val name = matcher.group(2) ?: continue
            val type = ResourceType.getEnum(typeName) ?: continue
            addEdge(owner, ResourceRef(type, name))
        }
    }

    private fun addEdge(from: ResourceRef, to: ResourceRef) {
        references.getOrPut(from) { mutableListOf() }.add(to)
    }

    private fun checkCycles(context: Context) {
        val visited = mutableSetOf<ResourceRef>()
        val onStack = mutableSetOf<ResourceRef>()
        val path = mutableListOf<ResourceRef>()

        fun dfs(node: ResourceRef) {
            visited.add(node)
            onStack.add(node)
            path.add(node)

            for (neighbor in references[node].orEmpty()) {
                if (!visited.contains(neighbor)) {
                    dfs(neighbor)
                } else if (onStack.contains(neighbor)) {
                    val start = path.indexOf(neighbor)
                    if (start != -1) {
                        reportCycle(context, path.subList(start, path.size))
                    }
                }
            }

            onStack.remove(node)
            path.removeAt(path.size - 1)
        }

        for (node in references.keys) {
            if (!visited.contains(node)) {
                dfs(node)
            }
        }
    }

    private fun reportCycle(context: Context, cycle: List<ResourceRef>) {
        val chain = buildString {
            for ((index, ref) in cycle.withIndex()) {
                if (index > 0) append(" -> ")
                append("@${ref.type.getName()}/${ref.name}")
            }
            append(" -> @${cycle[0].type.getName()}/${cycle[0].name}")
        }
        val message = "Cycle in resource definitions: $chain"

        var head: Location? = null
        var tail: Location? = null
        for (ref in cycle) {
            val location = locations[ref] ?: continue
            if (head == null) {
                head = location
                tail = location
            } else {
                tail!!.secondary = location
                tail = location
            }
        }
        if (head != null) {
            context.report(ISSUE, head, message)
        }
    }

    private data class ResourceRef(val type: ResourceType, val name: String)

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ResourceCycle",
            briefDescription = "Cycle in resource definitions",
            explanation = "There should be no cycles in resource definitions as this can lead to runtime exceptions.",
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.FATAL,
            implementation = Implementation(
                ResourceCycleDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        private val REFERENCE_PATTERN = Pattern.compile("""@(?:\w+:)?(\w+)/(\w+)""")
        private val THEME_REF_PATTERN = Pattern.compile("""\?(?:\w+:)?(\w+)/(\w+)""")
    }
}