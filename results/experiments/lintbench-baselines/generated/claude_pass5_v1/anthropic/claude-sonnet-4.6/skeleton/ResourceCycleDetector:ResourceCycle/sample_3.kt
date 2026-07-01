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

        // Resource folder types that can have cycles
        private val APPLICABLE_FOLDER_TYPES = setOf(
            ResourceFolderType.VALUES,
            ResourceFolderType.COLOR,
            ResourceFolderType.DRAWABLE,
        )

        // Tags that can reference other resources and create cycles
        private val APPLICABLE_ELEMENTS = listOf(
            TAG_STYLE,
            TAG_COLOR,
            TAG_ITEM,
        )

        // Attributes that can contain resource references
        private val APPLICABLE_ATTRIBUTES = listOf(
            ATTR_PARENT,
            ATTR_NAME,
        )
    }

    /**
     * Map from resource name to the resource it references (parent or value).
     * Key: "type/name", Value: list of (referenced "type/name", location)
     */
    private val resourceReferences = mutableMapOf<String, MutableList<Pair<String, Location>>>()

    /**
     * Map from resource key to location for error reporting
     */
    private val resourceLocations = mutableMapOf<String, Location>()

    override fun beforeCheckRootProject(context: Context) {
        resourceReferences.clear()
        resourceLocations.clear()
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType in APPLICABLE_FOLDER_TYPES

    override fun getApplicableElements(): Collection<String> = APPLICABLE_ELEMENTS

    override fun getApplicableAttributes(): Collection<String> = APPLICABLE_ATTRIBUTES

    override fun visitElement(context: XmlContext, element: Element) {
        val tag = element.tagName

        when (tag) {
            TAG_STYLE -> {
                val name = element.getAttribute(ATTR_NAME) ?: return
                if (name.isBlank()) return

                val styleName = normalizeStyleName(name)
                val resourceKey = "style/$styleName"

                val location = context.getLocation(element)
                resourceLocations[resourceKey] = location

                // Check for parent attribute
                val parentAttr = element.getAttribute(ATTR_PARENT)
                if (!parentAttr.isNullOrBlank()) {
                    val parentKey = normalizeStyleReference(parentAttr)
                    if (parentKey != null) {
                        resourceReferences.getOrPut(resourceKey) { mutableListOf() }
                            .add(Pair(parentKey, location))
                    }
                } else {
                    // Check for implicit parent via dot notation
                    val dotIndex = styleName.lastIndexOf('.')
                    if (dotIndex > 0) {
                        val implicitParent = styleName.substring(0, dotIndex)
                        val parentKey = "style/$implicitParent"
                        resourceReferences.getOrPut(resourceKey) { mutableListOf() }
                            .add(Pair(parentKey, location))
                    }
                }
            }

            TAG_COLOR, TAG_ITEM -> {
                val name = element.getAttribute(ATTR_NAME) ?: return
                if (name.isBlank()) return

                val type = if (tag == TAG_COLOR) {
                    "color"
                } else {
                    element.getAttribute(ATTR_TYPE).takeIf { !it.isNullOrBlank() } ?: return
                }

                val resourceKey = "$type/$name"
                val location = context.getLocation(element)
                resourceLocations[resourceKey] = location

                // Check text content for resource reference
                val textContent = element.textContent?.trim() ?: return
                if (textContent.startsWith("@")) {
                    val refKey = parseResourceReference(textContent)
                    if (refKey != null) {
                        resourceReferences.getOrPut(resourceKey) { mutableListOf() }
                            .add(Pair(refKey, location))
                    }
                }
            }
        }
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val attrName = attribute.localName ?: attribute.name ?: return
        val element = attribute.ownerElement ?: return
        val tag = element.tagName

        if (attrName == ATTR_PARENT && tag == TAG_STYLE) {
            val name = element.getAttribute(ATTR_NAME) ?: return
            if (name.isBlank()) return

            val styleName = normalizeStyleName(name)
            val resourceKey = "style/$styleName"

            val parentValue = attribute.value ?: return
            if (parentValue.isBlank()) return

            val parentKey = normalizeStyleReference(parentValue) ?: return
            val location = context.getLocation(attribute)

            resourceReferences.getOrPut(resourceKey) { mutableListOf() }
                .add(Pair(parentKey, location))
        }
    }

    override fun afterCheckRootProject(context: Context) {
        // Detect cycles using DFS
        val visited = mutableSetOf<String>()
        val inStack = mutableSetOf<String>()

        for (resource in resourceReferences.keys) {
            if (resource !in visited) {
                detectCycle(resource, visited, inStack, mutableListOf(), context)
            }
        }
    }

    private fun detectCycle(
        resource: String,
        visited: MutableSet<String>,
        inStack: MutableSet<String>,
        path: MutableList<String>,
        context: Context
    ) {
        visited.add(resource)
        inStack.add(resource)
        path.add(resource)

        val references = resourceReferences[resource] ?: emptyList()
        for ((referenced, location) in references) {
            if (referenced !in visited) {
                detectCycle(referenced, visited, inStack, path, context)
            } else if (referenced in inStack) {
                // Found a cycle - report it
                val cycleStart = path.indexOf(referenced)
                val cycle = if (cycleStart >= 0) {
                    path.subList(cycleStart, path.size) + referenced
                } else {
                    path + referenced
                }
                reportCycle(context, cycle, location)
            }
        }

        path.removeLastOrNull()
        inStack.remove(resource)
    }

    private fun reportCycle(context: Context, cycle: List<String>, location: Location) {
        val cycleDescription = cycle.joinToString(" => ") { it.substringAfter('/') }
        val message = "Cycle detected in resource definitions: $cycleDescription"

        // Find the best location to report
        val reportLocation = cycle.firstOrNull()?.let { resourceLocations[it] } ?: location

        context.report(
            issue = ISSUE,
            location = reportLocation,
            message = message,
        )
    }

    /**
     * Normalize a style name by removing any leading "@style/" or "style/" prefix
     * and replacing colons used in theme references.
     */
    private fun normalizeStyleName(name: String): String {
        return name.trim()
            .removePrefix("@style/")
            .removePrefix("@android:style/")
            .removePrefix("style/")
    }

    /**
     * Normalize a style reference (from parent attribute) to a resource key like "style/Name".
     */
    private fun normalizeStyleReference(reference: String): String? {
        val ref = reference.trim()
        return when {
            ref.startsWith("@style/") -> "style/${ref.removePrefix("@style/")}"
            ref.startsWith("@android:style/") -> "style/${ref.removePrefix("@android:style/")}"
            ref.startsWith("@*android:style/") -> "style/${ref.removePrefix("@*android:style/")}"
            ref.startsWith("?") -> null // Theme attribute reference, skip
            ref.startsWith("@") -> parseResourceReference(ref)
            ref.isNotBlank() && !ref.startsWith("@android:") -> "style/$ref"
            else -> null
        }
    }

    /**
     * Parse a resource reference like "@color/foo" or "@style/Bar" into "color/foo" or "style/Bar".
     */
    private fun parseResourceReference(reference: String): String? {
        val ref = reference.trim()
        if (!ref.startsWith("@")) return null

        val withoutAt = ref.removePrefix("@").removePrefix("+")
        // Remove android: namespace
        val withoutNamespace = withoutAt
            .removePrefix("android:")
            .removePrefix("*android:")

        val slashIndex = withoutNamespace.indexOf('/')
        if (slashIndex < 0) return null

        val type = withoutNamespace.substring(0, slashIndex)
        val name = withoutNamespace.substring(slashIndex + 1)

        if (type.isBlank() || name.isBlank()) return null

        return "$type/$name"
    }
}