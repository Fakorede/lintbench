package com.android.tools.lint.checks

import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_PARENT
import com.android.SdkConstants.ATTR_TYPE
import com.android.SdkConstants.TAG_ITEM
import com.android.SdkConstants.TAG_STYLE
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.util.EnumSet

class ResourceCycleDetector : Detector(), XmlScanner {

    // Maps "type" -> (resource name -> list of (referenced name, location))
    private val resourceReferences = mutableMapOf<String, MutableMap<String, MutableList<Pair<String, Location>>>>()

    // Track locations for resource definitions: "type:name" -> location
    private val resourceLocations = mutableMapOf<String, Location>()

    // Track which type each resource name belongs to (for non-values resources)
    private val resourceTypes = mutableMapOf<String, ResourceType>()

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

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES ||
                folderType == ResourceFolderType.DRAWABLE ||
                folderType == ResourceFolderType.LAYOUT ||
                folderType == ResourceFolderType.COLOR ||
                folderType == ResourceFolderType.ANIM ||
                folderType == ResourceFolderType.ANIMATOR ||
                folderType == ResourceFolderType.FONT ||
                folderType == ResourceFolderType.TRANSITION ||
                folderType == ResourceFolderType.MENU ||
                folderType == ResourceFolderType.MIPMAP ||
                folderType == ResourceFolderType.XML
    }

    override fun getApplicableElements(): Collection<String>? = null

    override fun visitDocument(context: XmlContext, document: Document) {
        val folderType = context.resourceFolderType ?: return
        when (folderType) {
            ResourceFolderType.VALUES -> processValuesDocument(context, document)
            else -> {
                val type = folderTypeToResourceType(folderType) ?: return
                val resourceName = getResourceNameFromFile(context) ?: return
                val root = document.documentElement ?: return
                val typeKey = type.getName()
                val location = context.getLocation(root)
                recordLocation(typeKey, resourceName, location)
                resourceTypes[resourceName] = type
                scanElementForReferences(context, root, type, resourceName)
            }
        }
    }

    private fun folderTypeToResourceType(folderType: ResourceFolderType): ResourceType? {
        return when (folderType) {
            ResourceFolderType.DRAWABLE -> ResourceType.DRAWABLE
            ResourceFolderType.LAYOUT -> ResourceType.LAYOUT
            ResourceFolderType.COLOR -> ResourceType.COLOR
            ResourceFolderType.ANIM -> ResourceType.ANIM
            ResourceFolderType.ANIMATOR -> ResourceType.ANIMATOR
            ResourceFolderType.FONT -> ResourceType.FONT
            ResourceFolderType.TRANSITION -> ResourceType.TRANSITION
            ResourceFolderType.MENU -> ResourceType.MENU
            ResourceFolderType.MIPMAP -> ResourceType.MIPMAP
            ResourceFolderType.XML -> ResourceType.XML
            else -> null
        }
    }

    private fun getResourceNameFromFile(context: XmlContext): String? {
        val file = context.file
        val name = file.name
        val dotIndex = name.lastIndexOf('.')
        return if (dotIndex > 0) name.substring(0, dotIndex) else name
    }

    private fun scanElementForReferences(
        context: XmlContext,
        element: Element,
        type: ResourceType,
        fromName: String
    ) {
        val attrs = element.attributes
        for (i in 0 until attrs.length) {
            val attr = attrs.item(i) as? Attr ?: continue
            val value = attr.value
            if (value.startsWith("@")) {
                val ref = parseReference(value, type)
                if (ref != null) {
                    val typeKey = type.getName()
                    val location = context.getLocation(attr)
                    addReference(typeKey, fromName, ref, location)
                }
            }
        }

        val textContent = getDirectTextContent(element).trim()
        if (textContent.startsWith("@")) {
            val ref = parseReference(textContent, type)
            if (ref != null) {
                val typeKey = type.getName()
                addReference(typeKey, fromName, ref, context.getLocation(element))
            }
        }

        var child = element.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                scanElementForReferences(context, child as Element, type, fromName)
            }
            child = child.nextSibling
        }
    }

    private fun processValuesDocument(context: XmlContext, document: Document) {
        val root = document.documentElement ?: return
        var child = root.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                processValueElement(context, child as Element)
            }
            child = child.nextSibling
        }
    }

    private fun processValueElement(context: XmlContext, element: Element) {
        val tagName = element.tagName

        when (tagName) {
            TAG_STYLE -> processStyle(context, element)
            "color" -> processSimpleValue(context, element, ResourceType.COLOR)
            "drawable" -> processSimpleValue(context, element, ResourceType.DRAWABLE)
            "dimen" -> processSimpleValue(context, element, ResourceType.DIMEN)
            "string" -> processSimpleValue(context, element, ResourceType.STRING)
            "integer" -> processSimpleValue(context, element, ResourceType.INTEGER)
            "bool" -> processSimpleValue(context, element, ResourceType.BOOL)
            "fraction" -> processSimpleValue(context, element, ResourceType.FRACTION)
            "plurals" -> processSimpleValue(context, element, ResourceType.PLURALS)
            "attr" -> processSimpleValue(context, element, ResourceType.ATTR)
            TAG_ITEM -> processItem(context, element)
            else -> {}
        }
    }

    private fun processStyle(context: XmlContext, element: Element) {
        val name = element.getAttribute(ATTR_NAME).takeIf { it.isNotEmpty() } ?: return
        val typeKey = ResourceType.STYLE.getName()
        val location = context.getLocation(element)
        recordLocation(typeKey, name, location)

        val parentAttr = element.getAttribute(ATTR_PARENT)
        if (parentAttr.isNotEmpty()) {
            // Explicit parent attribute
            val parentName = resolveStyleParentName(parentAttr)
            if (parentName != null) {
                val attrNode = element.getAttributeNode(ATTR_PARENT)
                val refLocation = if (attrNode != null) context.getLocation(attrNode) else location
                addReference(typeKey, name, parentName, refLocation)
            }
        } else {
            // Implicit parent via dot notation (e.g., "Foo.Bar" inherits from "Foo")
            val dotIndex = name.lastIndexOf('.')
            if (dotIndex > 0) {
                val implicitParent = name.substring(0, dotIndex)
                addReference(typeKey, name, implicitParent, location)
            }
        }
    }

    private fun resolveStyleParentName(parent: String): String? {
        // Handle @style/Name or @android:style/Name or just Name
        if (parent.startsWith("@")) {
            val slashIndex = parent.indexOf('/')
            if (slashIndex < 0) return null
            val typePartRaw = parent.substring(1, slashIndex)
            val namePart = parent.substring(slashIndex + 1)

            // Skip android framework references
            if (typePartRaw.contains("android")) return null
            if (namePart.contains(":")) return null

            // Check it's a style reference
            val colonIndex = typePartRaw.lastIndexOf(':')
            val actualType = if (colonIndex >= 0) typePartRaw.substring(colonIndex + 1) else typePartRaw
            if (actualType != "style") return null

            return namePart.takeIf { it.isNotEmpty() }
        } else {
            // Plain name reference (no @ prefix) - skip android: prefixed ones
            if (parent.contains(':')) return null
            return parent.takeIf { it.isNotEmpty() }
        }
    }

    private fun processSimpleValue(context: XmlContext, element: Element, type: ResourceType) {
        val name = element.getAttribute(ATTR_NAME).takeIf { it.isNotEmpty() } ?: return
        val typeKey = type.getName()
        val location = context.getLocation(element)
        recordLocation(typeKey, name, location)

        val textContent = getDirectTextContent(element).trim()
        if (textContent.startsWith("@")) {
            val ref = parseReference(textContent, type)
            if (ref != null) {
                addReference(typeKey, name, ref, location)
            }
        }
    }

    private fun processItem(context: XmlContext, element: Element) {
        val typeAttr = element.getAttribute(ATTR_TYPE).takeIf { it.isNotEmpty() } ?: return
        val type = ResourceType.fromXmlValue(typeAttr) ?: return
        val name = element.getAttribute(ATTR_NAME).takeIf { it.isNotEmpty() } ?: return
        val typeKey = type.getName()
        val location = context.getLocation(element)
        recordLocation(typeKey, name, location)

        val textContent = getDirectTextContent(element).trim()
        if (textContent.startsWith("@")) {
            val ref = parseReference(textContent, type)
            if (ref != null) {
                addReference(typeKey, name, ref, location)
            }
        }

        if (type == ResourceType.STYLE) {
            val parentAttr = element.getAttribute(ATTR_PARENT)
            if (parentAttr.isNotEmpty()) {
                val parentName = resolveStyleParentName(parentAttr)
                if (parentName != null) {
                    addReference(typeKey, name, parentName, location)
                }
            }
        }
    }

    private fun getDirectTextContent(element: Element): String {
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

    private fun parseReference(reference: String, expectedType: ResourceType): String? {
        var ref = reference
        if (!ref.startsWith("@")) return null
        ref = ref.substring(1)
        if (ref.startsWith("+")) ref = ref.substring(1)

        val slashIndex = ref.indexOf('/')
        if (slashIndex < 0) return null

        val typePartRaw = ref.substring(0, slashIndex)
        val namePart = ref.substring(slashIndex + 1)

        val colonIndex = typePartRaw.lastIndexOf(':')
        val typePart: String
        if (colonIndex >= 0) {
            val pkg = typePartRaw.substring(0, colonIndex)
            if (pkg == "android") return null
            typePart = typePartRaw.substring(colonIndex + 1)
        } else {
            typePart = typePartRaw
        }

        val refType = ResourceType.fromXmlValue(typePart) ?: return null
        if (refType != expectedType) return null

        return namePart.takeIf { it.isNotEmpty() }
    }

    private fun recordLocation(typeKey: String, name: String, location: Location) {
        val key = "$typeKey:$name"
        if (!resourceLocations.containsKey(key)) {
            resourceLocations[key] = location
        }
    }

    private fun addReference(typeKey: String, from: String, to: String, location: Location) {
        resourceReferences
            .getOrPut(typeKey) { mutableMapOf() }
            .getOrPut(from) { mutableListOf() }
            .add(Pair(to, location))
    }

    override fun afterCheckRootProject(context: Context) {
        for ((typeKey, refMap) in resourceReferences) {
            detectCycles(context, typeKey, refMap)
        }
    }

    private fun detectCycles(
        context: Context,
        typeKey: String,
        refMap: Map<String, List<Pair<String, Location>>>
    ) {
        val state = mutableMapOf<String, Int>() // 0=unvisited, 1=in-progress, 2=done
        val reported = mutableSetOf<String>()

        fun dfs(node: String, path: MutableList<String>, pathSet: MutableSet<String>) {
            val s = state[node] ?: 0
            if (s == 2) return
            if (s == 1) {
                // Found a cycle - node is already in path
                val cycleStart = path.indexOf(node)
                if (cycleStart >= 0) {
                    val cycleNodes = path.subList(cycleStart, path.size)
                    val cycleKey = cycleNodes.sorted().joinToString(",")
                    if (!reported.contains(cycleKey)) {
                        reported.add(cycleKey)

                        val isSelfCycle = cycleNodes.size == 1 && cycleNodes[0] == node

                        // Find the location of the reference that creates the cycle
                        val lastNode = path.last()
                        val refs = refMap[lastNode]
                        val refLocation = refs?.firstOrNull { it.first == node }?.second

                        val reportLocation = refLocation
                            ?: resourceLocations["$typeKey:$node"]
                            ?: resourceLocations["$typeKey:${path.firstOrNull()}"]

                        if (reportLocation != null) {
                            val message = if (isSelfCycle) {
                                // Determine the actual resource type for the message
                                val resourceTypeName = getResourceTypeName(typeKey, node)
                                "$resourceTypeName `$node` should not reference itself"
                            } else {
                                val cycleDescription = (cycleNodes + node).joinToString(" -> ")
                                "Cycle detected in resource definitions of type `$typeKey`: $cycleDescription"
                            }
                            context.report(
                                ISSUE,
                                reportLocation,
                                message
                            )
                        }
                    }
                }
                return
            }

            state[node] = 1
            path.add(node)
            pathSet.add(node)

            val refs = refMap[node]
            if (refs != null) {
                for ((ref, _) in refs) {
                    dfs(ref, path, pathSet)
                }
            }

            path.removeAt(path.size - 1)
            pathSet.remove(node)
            state[node] = 2
        }

        for (node in refMap.keys) {
            if ((state[node] ?: 0) == 0) {
                dfs(node, mutableListOf(), mutableSetOf())
            }
        }
    }

    private fun getResourceTypeName(typeKey: String, resourceName: String): String {
        // Try to get the actual resource type from our tracking map
        val type = resourceTypes[resourceName]
        if (type != null) {
            return type.getName().replaceFirstChar { it.uppercase() }
        }
        // Fall back to the typeKey
        return typeKey.replaceFirstChar { it.uppercase() }
    }
}