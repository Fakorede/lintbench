package com.android.tools.lint.checks

import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_PARENT
import com.android.SdkConstants.ATTR_TYPE
import com.android.SdkConstants.TAG_COLOR
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
        return true
    }

    override fun getApplicableElements(): Collection<String>? {
        return null
    }

    override fun visitDocument(context: XmlContext, document: Document) {
        val folderType = context.resourceFolderType ?: return
        when (folderType) {
            ResourceFolderType.VALUES -> processValuesDocument(context, document)
            ResourceFolderType.DRAWABLE -> processFileResource(context, document, ResourceType.DRAWABLE)
            ResourceFolderType.LAYOUT -> processFileResource(context, document, ResourceType.LAYOUT)
            ResourceFolderType.COLOR -> processFileResource(context, document, ResourceType.COLOR)
            ResourceFolderType.ANIM -> processFileResource(context, document, ResourceType.ANIM)
            ResourceFolderType.ANIMATOR -> processFileResource(context, document, ResourceType.ANIMATOR)
            ResourceFolderType.FONT -> processFileResource(context, document, ResourceType.FONT)
            ResourceFolderType.TRANSITION -> processFileResource(context, document, ResourceType.TRANSITION)
            ResourceFolderType.MENU -> processFileResource(context, document, ResourceType.MENU)
            ResourceFolderType.MIPMAP -> processFileResource(context, document, ResourceType.MIPMAP)
            ResourceFolderType.XML -> processFileResource(context, document, ResourceType.XML)
            else -> {}
        }
    }

    private fun getResourceNameFromFile(context: XmlContext): String? {
        val file = context.file
        val name = file.name
        val dotIndex = name.lastIndexOf('.')
        return if (dotIndex > 0) name.substring(0, dotIndex) else name
    }

    private fun processFileResource(context: XmlContext, document: Document, type: ResourceType) {
        val resourceName = getResourceNameFromFile(context) ?: return
        val root = document.documentElement ?: return
        val typeKey = type.getName()
        val location = context.getLocation(root)
        recordLocation(typeKey, resourceName, location)

        // Scan all elements for references to same type
        scanElementForReferences(context, root, type, resourceName)
    }

    private fun scanElementForReferences(
        context: XmlContext,
        element: Element?,
        type: ResourceType,
        fromName: String
    ) {
        if (element == null) return

        // Check all attributes for references
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

        // Check text content
        val textContent = getDirectTextContent(element).trim()
        if (textContent.startsWith("@")) {
            val ref = parseReference(textContent, type)
            if (ref != null) {
                val typeKey = type.getName()
                addReference(typeKey, fromName, ref, context.getLocation(element))
            }
        }

        // Check child elements
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
            TAG_COLOR -> processSimpleValue(context, element, ResourceType.COLOR)
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

        val parent = element.getAttribute(ATTR_PARENT)
        if (parent.isNotEmpty()) {
            val parentName = stripTypePrefix(parent)
            if (parentName.isNotEmpty() && !parentName.contains(':')) {
                addReference(typeKey, name, parentName, location)
            }
        } else {
            // Implicit parent via dot notation
            val dotIndex = name.lastIndexOf('.')
            if (dotIndex > 0) {
                val implicitParent = name.substring(0, dotIndex)
                addReference(typeKey, name, implicitParent, location)
            }
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
            val parent = element.getAttribute(ATTR_PARENT)
            if (parent.isNotEmpty()) {
                val parentName = stripTypePrefix(parent)
                if (parentName.isNotEmpty() && !parentName.contains(':')) {
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

    private fun stripTypePrefix(value: String): String {
        if (value.startsWith("@")) {
            val slashIndex = value.indexOf('/')
            if (slashIndex >= 0) {
                val namePart = value.substring(slashIndex + 1)
                // Remove package prefix from name if present
                val colonInName = namePart.indexOf(':')
                return if (colonInName >= 0) namePart.substring(colonInName + 1) else namePart
            }
        }
        val colonIndex = value.lastIndexOf(':')
        return if (colonIndex >= 0) value.substring(colonIndex + 1) else value
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
        // States: 0 = unvisited, 1 = in progress, 2 = done
        val state = mutableMapOf<String, Int>()
        val reported = mutableSetOf<String>()

        fun dfs(node: String, path: MutableList<String>, pathLocations: MutableList<Location>) {
            val s = state[node] ?: 0
            if (s == 2) return
            if (s == 1) {
                // Found a cycle - find where in path the cycle starts
                val cycleStart = path.indexOf(node)
                if (cycleStart >= 0) {
                    val cycleNodes = path.subList(cycleStart, path.size)
                    val cycleKey = cycleNodes.sorted().joinToString(",")
                    if (!reported.contains(cycleKey)) {
                        reported.add(cycleKey)

                        // Find the reference location that points to 'node' from the last element in path
                        val lastNode = path.last()
                        val refs = refMap[lastNode]
                        val refLocation = refs?.firstOrNull { it.first == node }?.second

                        // Use the reference location (attribute location) if available
                        val reportLocation = refLocation
                            ?: pathLocations.lastOrNull()
                            ?: resourceLocations["$typeKey:$node"]

                        if (reportLocation != null) {
                            val cycleDescription = (cycleNodes + node).joinToString(" -> ")
                            // Check if it's a self-reference
                            val message = if (cycleNodes.size == 1 && cycleNodes[0] == node) {
                                "${typeKey.replaceFirstChar { it.uppercase() }} ${cycleNodes[0]} should not reference itself"
                            } else {
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

            val refs = refMap[node]
            if (refs != null) {
                for ((ref, loc) in refs) {
                    pathLocations.add(loc)
                    dfs(ref, path, pathLocations)
                    if (pathLocations.isNotEmpty()) {
                        pathLocations.removeAt(pathLocations.size - 1)
                    }
                }
            }

            path.removeAt(path.size - 1)
            state[node] = 2
        }

        for (node in refMap.keys) {
            if ((state[node] ?: 0) == 0) {
                dfs(node, mutableListOf(), mutableListOf())
            }
        }
    }
}