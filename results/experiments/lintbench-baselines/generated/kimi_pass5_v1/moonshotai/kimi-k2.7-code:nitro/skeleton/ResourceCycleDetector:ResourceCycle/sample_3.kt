package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
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

    private val definitions = mutableMapOf<ResourceKey, Location>()
    private val references = mutableMapOf<ResourceKey, MutableList<Pair<ResourceKey, Location>>>()

    companion object {
        private val IMPLEMENTATION = Implementation(
            ResourceCycleDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "ResourceCycle",
            briefDescription = "Cycle in resource definitions",
            explanation = "Resource definitions must not reference each other in a cycle. " +
                "Cyclic references can cause infinite loops or crashes when the resources " +
                "are resolved at runtime.",
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.FATAL,
            implementation = IMPLEMENTATION,
        )
    }

    override fun beforeCheckRootProject(context: Context) {
        definitions.clear()
        references.clear()
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.VALUES

    override fun getApplicableElements(): Collection<String>? = null

    override fun getApplicableAttributes(): Collection<String>? = listOf("parent")

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            "style" -> visitStyle(context, element)
            "array", "string-array", "integer-array", "plurals" -> visitArrayLike(context, element)
            else -> visitValueElement(context, element)
        }
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        if (attribute.name != "parent") return
        val element = attribute.ownerElement ?: return
        if (element.tagName != "style") return

        val name = element.getAttribute("name").takeIf { it.isNotEmpty() } ?: return
        val from = ResourceKey("style", name)
        addDefinition(from, context.getLocation(element))

        val parentValue = attribute.value ?: return
        val to = when {
            parentValue.startsWith("?") -> null
            parentValue.startsWith("@") -> parseResource(parentValue)
            else -> ResourceKey("style", parentValue)
        }
        if (to != null) {
            addReference(from, to, context.getLocation(attribute))
        }
    }

    override fun afterCheckRootProject(context: Context) {
        val visited = mutableSetOf<ResourceKey>()
        val stack = mutableSetOf<ResourceKey>()
        val path = mutableListOf<ResourceKey>()
        val inCycle = mutableSetOf<ResourceKey>()

        fun dfs(node: ResourceKey) {
            visited.add(node)
            stack.add(node)
            path.add(node)

            references[node]?.forEach { (to, _) ->
                if (to !in definitions) return@forEach
                when {
                    to in stack -> {
                        val start = path.indexOf(to)
                        inCycle.addAll(path.subList(start, path.size))
                    }
                    to !in visited -> dfs(to)
                }
            }

            stack.remove(node)
            path.removeAt(path.size - 1)
        }

        for (node in definitions.keys) {
            if (node !in visited) {
                dfs(node)
            }
        }

        for (node in inCycle) {
            val location = definitions[node] ?: continue
            context.report(
                ISSUE,
                location,
                "Resource `${node.name}` of type `${node.type}` participates in a cycle in resource definitions."
            )
        }
    }

    private fun visitStyle(context: XmlContext, element: Element) {
        val name = element.getAttribute("name").takeIf { it.isNotEmpty() } ?: return
        val key = ResourceKey("style", name)
        addDefinition(key, context.getLocation(element))

        if (!element.hasAttribute("parent")) {
            val dot = name.lastIndexOf('.')
            if (dot > 0 && dot < name.length - 1) {
                addReference(key, ResourceKey("style", name.substring(0, dot)), context.getLocation(element))
            }
        }
    }

    private fun visitArrayLike(context: XmlContext, element: Element) {
        val name = element.getAttribute("name").takeIf { it.isNotEmpty() } ?: return
        val type = if (element.tagName == "plurals") "plurals" else "array"
        val key = ResourceKey(type, name)
        addDefinition(key, context.getLocation(element))

        val items = element.getElementsByTagName("item")
        for (i in 0 until items.length) {
            val item = items.item(i) as? Element ?: continue
            for (ref in findReferences(item.textContent)) {
                addReference(key, ref, context.getLocation(item))
            }
        }
    }

    private fun visitValueElement(context: XmlContext, element: Element) {
        val name = element.getAttribute("name").takeIf { it.isNotEmpty() } ?: return
        val type = when (element.tagName) {
            "item" -> element.getAttribute("type").takeIf { it.isNotEmpty() } ?: return
            else -> element.tagName
        }
        if (type.isEmpty()) return

        val key = ResourceKey(type, name)
        addDefinition(key, context.getLocation(element))
        for (ref in findReferences(element.textContent)) {
            addReference(key, ref, context.getLocation(element))
        }
    }

    private fun addDefinition(key: ResourceKey, location: Location) {
        definitions[key] = location
    }

    private fun addReference(from: ResourceKey, to: ResourceKey, location: Location) {
        references.getOrPut(from) { mutableListOf() }.add(to to location)
    }

    private fun findReferences(text: String): List<ResourceKey> =
        REFERENCE_REGEX.findAll(text).mapNotNull { match ->
            val pkg = match.groupValues[1]
            if (pkg == "android" || pkg == "*android") null
            else ResourceKey(match.groupValues[2], match.groupValues[3])
        }.toList()

    private fun parseResource(value: String): ResourceKey? =
        REFERENCE_REGEX.find(value)?.let { match ->
            val pkg = match.groupValues[1]
            if (pkg == "android" || pkg == "*android") null
            else ResourceKey(match.groupValues[2], match.groupValues[3])
        }

    private data class ResourceKey(val type: String, val name: String)

    private companion object {
        private val REFERENCE_REGEX = Regex("@\\+?(?:([\\w_*]+):)?([\\w_]+)/([\\w_.]+)")
    }
}