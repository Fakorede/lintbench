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
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
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

        private val APPLICABLE_ELEMENTS = listOf(
            TAG_STYLE,
            TAG_COLOR,
            TAG_ITEM,
        )

        private val APPLICABLE_ATTRIBUTES = listOf(
            ATTR_PARENT,
            ATTR_NAME,
        )
    }

    // Map from resource type+name -> parent resource type+name
    // e.g. "style:MyStyle" -> "style:ParentStyle"
    private val resourceParents = mutableMapOf<String, String>()

    // Store locations for reporting: resource key -> location
    private val resourceLocations = mutableMapOf<String, Location>()

    override fun beforeCheckRootProject(context: Context) {
        resourceParents.clear()
        resourceLocations.clear()
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType in APPLICABLE_FOLDER_TYPES
    }

    override fun getApplicableElements(): Collection<String> = APPLICABLE_ELEMENTS

    override fun getApplicableAttributes(): Collection<String> = APPLICABLE_ATTRIBUTES

    override fun visitElement(context: XmlContext, element: Element) {
        val tagName = element.tagName ?: return

        when (tagName) {
            TAG_STYLE -> {
                val name = element.getAttribute(ATTR_NAME) ?: return
                if (name.isEmpty()) return

                val parentAttr = element.getAttribute(ATTR_PARENT)
                val parent: String? = when {
                    parentAttr != null && parentAttr.isNotEmpty() -> {
                        // parent attribute specified explicitly
                        val stripped = stripResourcePrefix(parentAttr, "style")
                        stripped
                    }
                    name.contains('.') -> {
                        // Implicit parent via dot notation: "ParentStyle.ChildStyle"
                        val lastDot = name.lastIndexOf('.')
                        name.substring(0, lastDot)
                    }
                    else -> null
                }

                val key = "style:$name"
                val location = context.getLocation(element)
                resourceLocations[key] = location

                if (parent != null && parent.isNotEmpty()) {
                    val parentKey = "style:$parent"
                    resourceParents[key] = parentKey
                }
            }

            TAG_COLOR -> {
                val name = element.getAttribute(ATTR_NAME) ?: return
                if (name.isEmpty()) return

                val textContent = element.textContent?.trim() ?: return
                if (textContent.startsWith("@color/")) {
                    val parentName = textContent.removePrefix("@color/").trim()
                    if (parentName.isNotEmpty()) {
                        val key = "color:$name"
                        val parentKey = "color:$parentName"
                        resourceLocations[key] = context.getLocation(element)
                        resourceParents[key] = parentKey
                    }
                }
            }

            TAG_ITEM -> {
                val type = element.getAttribute(ATTR_TYPE) ?: return
                if (type.isEmpty()) return
                val name = element.getAttribute(ATTR_NAME) ?: return
                if (name.isEmpty()) return

                val textContent = element.textContent?.trim() ?: return
                val referencePrefix = "@$type/"
                if (textContent.startsWith(referencePrefix)) {
                    val parentName = textContent.removePrefix(referencePrefix).trim()
                    if (parentName.isNotEmpty()) {
                        val key = "$type:$name"
                        val parentKey = "$type:$parentName"
                        resourceLocations[key] = context.getLocation(element)
                        resourceParents[key] = parentKey
                    }
                }
            }
        }
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        // Handle style parent attribute specifically if needed
        // Most logic is handled in visitElement; this is a fallback for
        // attributes that might not be caught there.
        val element = attribute.ownerElement ?: return
        val tagName = element.tagName ?: return

        if (tagName == TAG_STYLE && attribute.localName == ATTR_PARENT) {
            // Already handled in visitElement
            return
        }
    }

    override fun afterCheckRootProject(context: Context) {
        // Detect cycles using DFS / visited tracking
        val visited = mutableSetOf<String>()
        val inStack = mutableSetOf<String>()

        for (key in resourceParents.keys) {
            if (key !in visited) {
                detectCycle(context, key, visited, inStack, mutableListOf())
            }
        }
    }

    private fun detectCycle(
        context: Context,
        key: String,
        visited: MutableSet<String>,
        inStack: MutableSet<String>,
        path: MutableList<String>,
    ) {
        visited.add(key)
        inStack.add(key)
        path.add(key)

        val parent = resourceParents[key]
        if (parent != null) {
            if (parent in inStack) {
                // Found a cycle — report it
                val cycleStart = path.indexOf(parent)
                val cycle = if (cycleStart >= 0) {
                    path.subList(cycleStart, path.size) + parent
                } else {
                    path + parent
                }
                reportCycle(context, key, parent, cycle)
            } else if (parent !in visited) {
                detectCycle(context, parent, visited, inStack, path)
            }
        }

        inStack.remove(key)
        path.removeAt(path.size - 1)
    }

    private fun reportCycle(
        context: Context,
        key: String,
        cycleTarget: String,
        cycle: List<String>,
    ) {
        val location = resourceLocations[key] ?: return

        val cycleDescription = cycle.joinToString(" -> ") { resourceDisplayName(it) }
        val message = "Cycle detected: $cycleDescription"

        context.report(
            issue = ISSUE,
            location = location,
            message = message,
        )
    }

    private fun resourceDisplayName(key: String): String {
        val colonIdx = key.indexOf(':')
        return if (colonIdx >= 0) {
            val type = key.substring(0, colonIdx)
            val name = key.substring(colonIdx + 1)
            "@$type/$name"
        } else {
            key
        }
    }

    /**
     * Strips resource reference prefix (e.g. "@style/") from a resource reference string,
     * returning just the name portion.
     */
    private fun stripResourcePrefix(reference: String, type: String): String {
        val prefix = "@$type/"
        return when {
            reference.startsWith(prefix) -> reference.removePrefix(prefix)
            reference.startsWith("@android:$type/") -> reference // keep android references as-is for cross-ref
            else -> reference
        }
    }
}