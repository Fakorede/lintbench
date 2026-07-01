package com.android.tools.lint.checks

import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_PARENT
import com.android.SdkConstants.ATTR_TYPE
import com.android.SdkConstants.TAG_COLOR
import com.android.SdkConstants.TAG_ITEM
import com.android.SdkConstants.TAG_STYLE
import com.android.resources.ResourceType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element
import org.w3c.dom.Node

class ResourceCycleDetector : Detector(), XmlScanner {

    // Maps resource type -> (resource name -> list of (parent/reference name, location))
    private val resourceParents = mutableMapOf<ResourceType, MutableMap<String, MutableList<Pair<String, Location>>>>()

    // Track locations for resource definitions: type -> name -> location
    private val resourceLocations = mutableMapOf<ResourceType, MutableMap<String, Location>>()

    companion object {
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
            severity = Severity.ERROR,
            implementation = Implementation(
                ResourceCycleDetector::class.java,
                Scope.ALL_RESOURCES_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(
            TAG_STYLE,
            TAG_COLOR,
            TAG_ITEM,
            "drawable",
            "layout",
            "dimen",
            "string",
            "integer",
            "bool",
            "array",
            "string-array",
            "integer-array",
            "attr",
            "declare-styleable",
            "fraction",
            "plurals",
            "raw",
            "menu",
            "anim",
            "animator",
            "interpolator",
            "transition",
            "font"
        )
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val tagName = element.tagName

        when (tagName) {
            TAG_STYLE -> handleStyle(context, element)
            else -> handleGenericResource(context, element)
        }
    }

    private fun handleStyle(context: XmlContext, element: Element) {
        val name = element.getAttribute(ATTR_NAME).takeIf { it.isNotEmpty() } ?: return
        val parent = element.getAttribute(ATTR_PARENT)

        val location = context.getLocation(element)
        val type = ResourceType.STYLE

        recordResourceLocation(type, name, location)

        // Explicit parent attribute
        if (parent.isNotEmpty()) {
            val parentName = stripResourcePrefix(parent, type)
            if (parentName.isNotEmpty()) {
                addParentReference(type, name, parentName, context.getLocation(element))
            }
        } else {
            // Implicit parent via dot notation (e.g., "Parent.Child")
            val dotIndex = name.lastIndexOf('.')
            if (dotIndex > 0) {
                val implicitParent = name.substring(0, dotIndex)
                addParentReference(type, name, implicitParent, context.getLocation(element))
            }
        }
    }

    private fun handleGenericResource(context: XmlContext, element: Element) {
        val tagName = element.tagName
        val name = element.getAttribute(ATTR_NAME).takeIf { it.isNotEmpty() } ?: return

        // Determine resource type
        val type = when (tagName) {
            TAG_ITEM -> {
                val typeAttr = element.getAttribute(ATTR_TYPE).takeIf { it.isNotEmpty() } ?: return
                ResourceType.fromXmlValue(typeAttr) ?: return
            }
            TAG_COLOR -> ResourceType.COLOR
            "drawable" -> ResourceType.DRAWABLE
            "layout" -> ResourceType.LAYOUT
            "dimen" -> ResourceType.DIMEN
            "string" -> ResourceType.STRING
            "integer" -> ResourceType.INTEGER
            "bool" -> ResourceType.BOOL
            "fraction" -> ResourceType.FRACTION
            "plurals" -> ResourceType.PLURALS
            "attr" -> ResourceType.ATTR
            else -> return
        }

        val location = context.getLocation(element)
        recordResourceLocation(type, name, location)

        // Check text content for a reference
        val textContent = getTextContent(element).trim()
        if (textContent.startsWith("@")) {
            val referencedName = parseResourceReference(textContent, type)
            if (referencedName != null) {
                addParentReference(type, name, referencedName, location)
            }
        }
    }

    private fun getTextContent(element: Element): String {
        val sb = StringBuilder()
        var child: Node? = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.TEXT_NODE) {
                sb.append(child.nodeValue)
            }
            child = child.nextSibling
        }
        return sb.toString()
    }

    private fun parseResourceReference(reference: String, expectedType: ResourceType): String? {
        // Format: @[+][package:]type/name or @type/name
        val ref = reference.trimStart('@', '+').trim()
        val slashIndex = ref.indexOf('/')
        if (slashIndex < 0) return null

        val typePart = ref.substring(0, slashIndex).let {
            // Remove package prefix if present
            val colonIndex = it.lastIndexOf(':')
            if (colonIndex >= 0) it.substring(colonIndex + 1) else it
        }
        val namePart = ref.substring(slashIndex + 1)

        val refType = ResourceType.fromXmlValue(typePart) ?: return null
        if (refType != expectedType) return null

        return namePart.takeIf { it.isNotEmpty() }
    }

    private fun stripResourcePrefix(value: String, type: ResourceType): String {
        // Handle @style/Name, @color/Name, etc.
        if (value.startsWith("@")) {
            val slashIndex = value.indexOf('/')
            if (slashIndex >= 0) {
                return value.substring(slashIndex + 1)
            }
        }
        return value
    }

    private fun recordResourceLocation(type: ResourceType, name: String, location: Location) {
        resourceLocations.getOrPut(type) { mutableMapOf() }[name] = location
    }

    private fun addParentReference(
        type: ResourceType,
        name: String,
        parentName: String,
        location: Location
    ) {
        resourceParents
            .getOrPut(type) { mutableMapOf() }
            .getOrPut(name) { mutableListOf() }
            .add(Pair(parentName, location))
    }

    override fun afterCheckRootProject(context: Context) {
        // Now detect cycles in each resource type graph
        for ((type, parentMap) in resourceParents) {
            detectCycles(context, type, parentMap)
        }
    }

    private fun detectCycles(
        context: Context,
        type: ResourceType,
        parentMap: Map<String, List<Pair<String, Location>>>
    ) {
        // We'll do DFS cycle detection
        // States: 0 = unvisited, 1 = in progress, 2 = done
        val state = mutableMapOf<String, Int>()
        val reported = mutableSetOf<String>()

        fun dfs(node: String, path: MutableList<String>): Boolean {
            val s = state[node] ?: 0
            if (s == 2) return false
            if (s == 1) {
                // Found a cycle - find where the cycle starts
                val cycleStart = path.indexOf(node)
                if (cycleStart >= 0) {
                    val cycleNodes = path.subList(cycleStart, path.size)
                    val cycleKey = cycleNodes.sorted().joinToString(",")
                    if (!reported.contains(cycleKey)) {
                        reported.add(cycleKey)
                        // Report at the location of the first node in the cycle
                        val firstNode = cycleNodes[0]
                        val location = resourceLocations[type]?.get(firstNode)
                            ?: resourceLocations[type]?.get(node)
                        if (location != null) {
                            val cycleDescription = (cycleNodes + node).joinToString(" -> ")
                            context.report(
                                ISSUE,
                                location,
                                "Cycle detected in resource definitions of type `${type.getName()}`: $cycleDescription"
                            )
                        }
                    }
                }
                return true
            }

            state[node] = 1
            path.add(node)

            val parents = parentMap[node]
            if (parents != null) {
                for ((parentName, _) in parents) {
                    dfs(parentName, path)
                }
            }

            path.removeAt(path.size - 1)
            state[node] = 2
            return false
        }

        for (node in parentMap.keys) {
            if ((state[node] ?: 0) == 0) {
                dfs(node, mutableListOf())
            }
        }
    }
}