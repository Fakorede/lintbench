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

        // Map from resource type -> (resource name -> parent name)
        // e.g., "style" -> ("MyStyle" -> "MyStyle.Parent")
        private val APPLICABLE_FOLDER_TYPES = setOf(
            ResourceFolderType.VALUES,
            ResourceFolderType.COLOR,
            ResourceFolderType.DRAWABLE,
        )

        // Tags that can have a "parent" attribute (styles)
        private val TAGS_WITH_PARENT = setOf(TAG_STYLE)

        // Tags that can reference themselves via @type/name in their content
        private val VALUE_TAGS = setOf(TAG_COLOR, TAG_ITEM)
    }

    /**
     * Map from resource type to a map of resource name -> parent resource name.
     * Used to detect style/color inheritance cycles.
     */
    private val parentMap = mutableMapOf<String, MutableMap<String, String>>()

    /**
     * Map from resource type+name to the location where the parent reference is defined.
     */
    private val locationMap = mutableMapOf<String, MutableMap<String, Location>>()

    /**
     * Map from resource type+name to the location of the element itself.
     */
    private val elementLocationMap = mutableMapOf<String, MutableMap<String, Location>>()

    override fun beforeCheckRootProject(context: Context) {
        parentMap.clear()
        locationMap.clear()
        elementLocationMap.clear()
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
        val tag = element.tagName

        when (tag) {
            TAG_STYLE -> {
                val name = element.getAttribute(ATTR_NAME) ?: return
                if (name.isEmpty()) return

                // Check for dot-notation parent (e.g., "MyTheme.Child" implies parent "MyTheme")
                val explicitParent = element.getAttribute(ATTR_PARENT)
                val dotParent = if (explicitParent.isNullOrEmpty() && name.contains('.')) {
                    name.substringBeforeLast('.')
                } else null

                val effectiveParent = when {
                    !explicitParent.isNullOrEmpty() -> normalizeStyleRef(explicitParent)
                    dotParent != null -> dotParent
                    else -> null
                }

                if (effectiveParent != null) {
                    val typeMap = parentMap.getOrPut("style") { mutableMapOf() }
                    typeMap[name] = effectiveParent

                    val locMap = locationMap.getOrPut("style") { mutableMapOf() }
                    locMap[name] = context.getLocation(element)
                }

                val elemLocMap = elementLocationMap.getOrPut("style") { mutableMapOf() }
                elemLocMap[name] = context.getLocation(element)
            }

            TAG_COLOR, TAG_ITEM -> {
                // For <item type="color"> or <color> tags, check if the text content
                // references the same resource (self-reference)
                val resourceType = when (tag) {
                    TAG_COLOR -> "color"
                    TAG_ITEM -> element.getAttribute(ATTR_TYPE) ?: return
                    else -> return
                }

                val name = element.getAttribute(ATTR_NAME) ?: return
                if (name.isEmpty()) return

                val textContent = element.textContent?.trim() ?: return
                if (textContent.isEmpty()) return

                // Check if the text content is a reference like @color/name or @type/name
                val refValue = parseResourceReference(textContent) ?: return
                val (refType, refName) = refValue

                if (refType == resourceType && refName == name) {
                    // Direct self-reference cycle
                    context.report(
                        ISSUE,
                        element,
                        context.getLocation(element),
                        "The `$resourceType` resource `$name` references itself"
                    )
                    return
                }

                if (refType == resourceType) {
                    val typeMap = parentMap.getOrPut(resourceType) { mutableMapOf() }
                    typeMap[name] = refName

                    val locMap = locationMap.getOrPut(resourceType) { mutableMapOf() }
                    locMap[name] = context.getLocation(element)
                }

                val elemLocMap = elementLocationMap.getOrPut(resourceType) { mutableMapOf() }
                elemLocMap[name] = context.getLocation(element)
            }
        }
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        // Handle the "parent" attribute on <style> elements
        val element = attribute.ownerElement ?: return
        if (element.tagName != TAG_STYLE) return

        val name = element.getAttribute(ATTR_NAME) ?: return
        if (name.isEmpty()) return

        val parentValue = attribute.value ?: return
        if (parentValue.isEmpty()) return

        val normalizedParent = normalizeStyleRef(parentValue)

        val typeMap = parentMap.getOrPut("style") { mutableMapOf() }
        typeMap[name] = normalizedParent

        val locMap = locationMap.getOrPut("style") { mutableMapOf() }
        locMap[name] = context.getLocation(attribute)
    }

    override fun afterCheckRootProject(context: Context) {
        // Now detect cycles in all collected parent maps
        for ((resourceType, typeParentMap) in parentMap) {
            val reported = mutableSetOf<String>()

            for (resourceName in typeParentMap.keys) {
                if (resourceName in reported) continue

                val cycle = findCycle(resourceName, typeParentMap)
                if (cycle != null) {
                    // Report the cycle
                    val cycleStart = cycle.first()
                    val location = locationMap[resourceType]?.get(cycleStart)
                        ?: elementLocationMap[resourceType]?.get(cycleStart)
                        ?: continue

                    val cycleDescription = cycle.joinToString(" -> ") + " -> " + cycleStart
                    context.report(
                        ISSUE,
                        location,
                        "Cycle detected in resource definitions: $cycleDescription"
                    )

                    reported.addAll(cycle)
                }
            }
        }
    }

    /**
     * Finds a cycle starting from [start] in the [parentMap].
     * Returns the list of nodes forming the cycle, or null if no cycle exists.
     */
    private fun findCycle(
        start: String,
        parentMap: Map<String, String>
    ): List<String>? {
        val visited = mutableListOf<String>()
        var current: String? = start

        while (current != null) {
            if (current in visited) {
                // Found a cycle - return the cycle portion
                val cycleStart = visited.indexOf(current)
                return visited.subList(cycleStart, visited.size)
            }
            visited.add(current)
            current = parentMap[current]
        }

        return null
    }

    /**
     * Normalizes a style reference by stripping the @style/ prefix if present.
     */
    private fun normalizeStyleRef(ref: String): String {
        return when {
            ref.startsWith("@style/") -> ref.substring("@style/".length)
            ref.startsWith("@android:style/") -> ref.substring("@android:style/".length)
            else -> ref
        }
    }

    /**
     * Parses a resource reference like @color/name or @drawable/name.
     * Returns a pair of (type, name) or null if not a valid reference.
     */
    private fun parseResourceReference(value: String): Pair<String, String>? {
        if (!value.startsWith("@")) return null
        val withoutAt = value.substring(1)
        // Handle @android:type/name
        val withoutNamespace = if (withoutAt.contains(':')) {
            withoutAt.substringAfter(':')
        } else {
            withoutAt
        }
        val slashIndex = withoutNamespace.indexOf('/')
        if (slashIndex == -1) return null
        val type = withoutNamespace.substring(0, slashIndex)
        val name = withoutNamespace.substring(slashIndex + 1)
        if (type.isEmpty() || name.isEmpty()) return null
        return Pair(type, name)
    }
}