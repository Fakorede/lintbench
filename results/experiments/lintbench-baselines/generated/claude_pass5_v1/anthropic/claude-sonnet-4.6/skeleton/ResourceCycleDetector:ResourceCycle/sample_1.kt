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
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr
import org.w3c.dom.Element

class ResourceCycleDetector : ResourceXmlDetector() {

    /**
     * Map from resource type to a map of resource name to the resource it references.
     * e.g. "style" -> { "MyStyle" -> "ParentStyle" }
     */
    private val referenceMap: MutableMap<String, MutableMap<String, String>> = mutableMapOf()

    /**
     * Map from resource type to a map of resource name to the location of its definition.
     */
    private val locationMap: MutableMap<String, MutableMap<String, Location>> = mutableMapOf()

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
        )
    }

    override fun beforeCheckRootProject(context: Context) {
        referenceMap.clear()
        locationMap.clear()
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType in APPLICABLE_FOLDER_TYPES
    }

    override fun getApplicableElements(): Collection<String> = APPLICABLE_ELEMENTS

    override fun getApplicableAttributes(): Collection<String> = APPLICABLE_ATTRIBUTES

    override fun visitElement(context: XmlContext, element: Element) {
        val tagName = element.tagName

        when (tagName) {
            TAG_STYLE -> {
                val name = element.getAttribute(ATTR_NAME) ?: return
                if (name.isEmpty()) return

                // Check for parent attribute on the style element itself
                val parent = element.getAttribute(ATTR_PARENT)
                if (!parent.isNullOrEmpty()) {
                    // Normalize the parent name (strip resource type prefix if present)
                    val normalizedParent = normalizeStyleName(parent)
                    val normalizedName = normalizeStyleName(name)
                    if (normalizedParent.isNotEmpty() && normalizedName != normalizedParent) {
                        addReference("style", normalizedName, normalizedParent, context.getLocation(element))
                    }
                } else {
                    // Check for implicit parent via dot notation
                    val dotIndex = name.lastIndexOf('.')
                    if (dotIndex > 0) {
                        val implicitParent = name.substring(0, dotIndex)
                        val normalizedName = normalizeStyleName(name)
                        val normalizedParent = normalizeStyleName(implicitParent)
                        if (normalizedParent.isNotEmpty() && normalizedName != normalizedParent) {
                            addReference("style", normalizedName, normalizedParent, context.getLocation(element))
                        }
                    }
                }
            }

            TAG_COLOR -> {
                val name = element.getAttribute(ATTR_NAME) ?: return
                if (name.isEmpty()) return

                val text = element.textContent?.trim() ?: return
                if (text.startsWith("@color/")) {
                    val referencedColor = text.substring("@color/".length)
                    if (referencedColor.isNotEmpty() && referencedColor != name) {
                        addReference("color", name, referencedColor, context.getLocation(element))
                    }
                }
            }

            TAG_ITEM -> {
                val type = element.getAttribute(ATTR_TYPE) ?: return
                if (type.isEmpty()) return

                val name = element.getAttribute(ATTR_NAME) ?: return
                if (name.isEmpty()) return

                val text = element.textContent?.trim() ?: return
                val prefix = "@$type/"
                if (text.startsWith(prefix)) {
                    val referencedName = text.substring(prefix.length)
                    if (referencedName.isNotEmpty() && referencedName != name) {
                        addReference(type, name, referencedName, context.getLocation(element))
                    }
                }
            }
        }
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        if (attribute.localName == ATTR_PARENT) {
            val element = attribute.ownerElement ?: return
            if (element.tagName != TAG_STYLE) return

            val name = element.getAttribute(ATTR_NAME) ?: return
            if (name.isEmpty()) return

            val parent = attribute.value ?: return
            if (parent.isEmpty()) return

            val normalizedName = normalizeStyleName(name)
            val normalizedParent = normalizeStyleName(parent)

            if (normalizedParent.isNotEmpty() && normalizedName != normalizedParent) {
                // Already handled in visitElement, but update location to attribute location
                addReference("style", normalizedName, normalizedParent, context.getLocation(attribute))
            }
        }
    }

    override fun afterCheckRootProject(context: Context) {
        // For each resource type, detect cycles
        for ((type, references) in referenceMap) {
            val visited = mutableSetOf<String>()
            val inStack = mutableSetOf<String>()

            for (name in references.keys) {
                if (name !in visited) {
                    detectCycle(context, type, name, references, visited, inStack, mutableListOf())
                }
            }
        }
    }

    private fun detectCycle(
        context: Context,
        type: String,
        name: String,
        references: Map<String, String>,
        visited: MutableSet<String>,
        inStack: MutableSet<String>,
        path: MutableList<String>,
    ) {
        visited.add(name)
        inStack.add(name)
        path.add(name)

        val referenced = references[name]
        if (referenced != null) {
            if (referenced in inStack) {
                // Found a cycle - report it
                val cycleStart = path.indexOf(referenced)
                val cycle = if (cycleStart >= 0) {
                    path.subList(cycleStart, path.size) + referenced
                } else {
                    path + referenced
                }
                reportCycle(context, type, cycle)
            } else if (referenced !in visited) {
                detectCycle(context, type, referenced, references, visited, inStack, path)
            }
        }

        inStack.remove(name)
        path.removeAt(path.size - 1)
    }

    private fun reportCycle(context: Context, type: String, cycle: List<String>) {
        if (cycle.isEmpty()) return

        val firstName = cycle.first()
        val locations = locationMap[type]
        val location = locations?.get(firstName) ?: return

        val cycleDescription = cycle.joinToString(" -> ")
        val message = "Cycle detected: $cycleDescription"

        context.report(ISSUE, location, message)
    }

    private fun addReference(type: String, name: String, referencedName: String, location: Location) {
        val typeMap = referenceMap.getOrPut(type) { mutableMapOf() }
        typeMap[name] = referencedName

        val locMap = locationMap.getOrPut(type) { mutableMapOf() }
        if (!locMap.containsKey(name)) {
            locMap[name] = location
        }
    }

    private fun normalizeStyleName(name: String): String {
        // Remove resource type prefix like "@style/"
        var result = name
        if (result.startsWith("@")) {
            val slashIndex = result.indexOf('/')
            if (slashIndex >= 0) {
                result = result.substring(slashIndex + 1)
            }
        }
        // Remove any trailing whitespace
        return result.trim()
    }
}