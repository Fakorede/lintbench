package com.android.tools.lint.checks

import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_PARENT
import com.android.SdkConstants.ATTR_TYPE
import com.android.SdkConstants.TAG_COLOR
import com.android.SdkConstants.TAG_ITEM
import com.android.SdkConstants.TAG_STYLE
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintFix
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
                There should be no cycles in resource definitions as this can lead to \
                runtime exceptions.
                """,
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.FATAL,
            implementation = IMPLEMENTATION,
        )

        private val APPLICABLE_FOLDER_TYPES = setOf(
            ResourceFolderType.VALUES,
            ResourceFolderType.COLOR,
            ResourceFolderType.DRAWABLE,
        )
    }

    // Maps resource type -> (resource name -> list of (referenced name, location))
    private val styleParents = mutableMapOf<String, Location>()
    // type+name -> list of references
    private val graph = mutableMapOf<String, MutableList<String>>()
    // Store locations for each node key
    private val locations = mutableMapOf<String, Location>()

    override fun beforeCheckRootProject(context: Context) {
        styleParents.clear()
        graph.clear()
        locations.clear()
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType in APPLICABLE_FOLDER_TYPES
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_STYLE, TAG_COLOR, TAG_ITEM)
    }

    override fun getApplicableAttributes(): Collection<String> {
        return listOf(ATTR_PARENT)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val tagName = element.tagName
        when (tagName) {
            TAG_STYLE -> {
                val name = element.getAttribute(ATTR_NAME) ?: return
                if (name.isEmpty()) return
                val normalizedName = name.replace('.', '_')
                val key = "style:$normalizedName"
                locations[key] = context.getNameLocation(element)

                // Check for implicit parent via dot notation
                if (name.contains('.')) {
                    val dotIndex = name.lastIndexOf('.')
                    val implicitParent = name.substring(0, dotIndex).replace('.', '_')
                    val parentKey = "style:$implicitParent"
                    graph.getOrPut(key) { mutableListOf() }.add(parentKey)
                }

                // Explicit parent is handled in visitAttribute
            }
            TAG_COLOR -> {
                val name = element.getAttribute(ATTR_NAME) ?: return
                if (name.isEmpty()) return
                val key = "color:$name"
                locations[key] = context.getNameLocation(element)

                val text = element.textContent?.trim() ?: return
                if (text.startsWith("@color/")) {
                    val refName = text.substring("@color/".length).trim()
                    if (refName.isNotEmpty()) {
                        val refKey = "color:$refName"
                        graph.getOrPut(key) { mutableListOf() }.add(refKey)
                    }
                }
            }
            TAG_ITEM -> {
                val type = element.getAttribute(ATTR_TYPE)
                val name = element.getAttribute(ATTR_NAME) ?: return
                if (name.isEmpty() || type.isEmpty()) return
                val key = "$type:$name"
                locations[key] = context.getNameLocation(element)

                val text = element.textContent?.trim() ?: return
                if (text.startsWith("@$type/")) {
                    val refName = text.substring("@$type/".length).trim()
                    if (refName.isNotEmpty()) {
                        val refKey = "$type:$refName"
                        graph.getOrPut(key) { mutableListOf() }.add(refKey)
                    }
                } else if (text.startsWith("@color/") && type == "color") {
                    val refName = text.substring("@color/".length).trim()
                    if (refName.isNotEmpty()) {
                        val refKey = "color:$refName"
                        graph.getOrPut(key) { mutableListOf() }.add(refKey)
                    }
                }
            }
        }
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val element = attribute.ownerElement ?: return
        if (element.tagName != TAG_STYLE) return
        if (attribute.localName != ATTR_PARENT) return

        val parentValue = attribute.value ?: return
        if (parentValue.isEmpty()) return

        val name = element.getAttribute(ATTR_NAME) ?: return
        if (name.isEmpty()) return
        val normalizedName = name.replace('.', '_')
        val key = "style:$normalizedName"
        locations[key] = context.getNameLocation(element)

        // Parse parent reference
        val parentName = when {
            parentValue.startsWith("@style/") -> parentValue.substring("@style/".length)
            parentValue.startsWith("@android:style/") -> null // skip framework parents
            parentValue.startsWith("@*android:style/") -> null
            !parentValue.startsWith("@") -> parentValue // bare name
            else -> null
        } ?: return

        val normalizedParent = parentName.replace('.', '_')
        val parentKey = "style:$normalizedParent"
        val refs = graph.getOrPut(key) { mutableListOf() }
        // Remove any implicit parent added by dot notation since explicit overrides it
        refs.removeAll { it.startsWith("style:") }
        refs.add(parentKey)
    }

    override fun afterCheckRootProject(context: Context) {
        // Detect cycles using DFS
        val visited = mutableSetOf<String>()
        val reported = mutableSetOf<String>()

        for (node in graph.keys) {
            if (node !in visited) {
                val path = mutableListOf<String>()
                detectCycle(node, path, visited, reported, context)
            }
        }
    }

    private fun detectCycle(
        node: String,
        path: MutableList<String>,
        visited: MutableSet<String>,
        reported: MutableSet<String>,
        context: Context,
    ) {
        if (node in reported) return
        val cycleStart = path.indexOf(node)
        if (cycleStart != -1) {
            // Found a cycle
            val cycle = path.subList(cycleStart, path.size)
            val cycleKey = cycle.sorted().joinToString(",")
            if (cycleKey !in reported) {
                reported.add(cycleKey)
                // Report on the first node in the cycle that has a location
                val reportNode = cycle.firstOrNull { it in locations } ?: return
                val location = locations[reportNode] ?: return
                val cycleDescription = (cycle + cycle.first()).joinToString(" -> ") { it.substringAfter(':') }
                context.report(
                    ISSUE,
                    location,
                    "Cycle detected in resource definitions: $cycleDescription",
                )
            }
            return
        }

        if (node in visited) return
        path.add(node)

        val refs = graph[node] ?: emptyList()
        for (ref in refs) {
            detectCycle(ref, path, visited, reported, context)
        }

        path.removeAt(path.size - 1)
        visited.add(node)
    }
}