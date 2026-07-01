package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_COLOR
import com.android.SdkConstants.ATTR_DRAWABLE
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_LAYOUT
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_PARENT
import com.android.SdkConstants.ATTR_TYPE
import com.android.SdkConstants.DOT_XML
import com.android.SdkConstants.FD_RES_COLOR
import com.android.SdkConstants.FD_RES_DRAWABLE
import com.android.SdkConstants.FD_RES_LAYOUT
import com.android.SdkConstants.FD_RES_VALUES
import com.android.SdkConstants.TAG_COLOR
import com.android.SdkConstants.TAG_DRAWABLE
import com.android.SdkConstants.TAG_ITEM
import com.android.SdkConstants.TAG_STYLE
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceFolderType.COLOR
import com.android.resources.ResourceFolderType.DRAWABLE
import com.android.resources.ResourceFolderType.LAYOUT
import com.android.resources.ResourceFolderType.VALUES
import com.android.tools.lint.detector.api.Category
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

    // Maps resource type -> (resource name -> list of referenced resource names)
    private val graph: MutableMap<String, MutableMap<String, MutableList<String>>> = mutableMapOf()

    // Maps resource type -> (resource name -> location for reporting)
    private val locations: MutableMap<String, MutableMap<String, Location>> = mutableMapOf()

    // Track which cycles have already been reported to avoid duplicates
    private val reportedCycles: MutableSet<String> = mutableSetOf()

    override fun beforeCheckRootProject(context: com.android.tools.lint.detector.api.Context) {
        graph.clear()
        locations.clear()
        reportedCycles.clear()
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == VALUES || folderType == COLOR ||
                folderType == DRAWABLE || folderType == LAYOUT
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(TAG_STYLE, TAG_ITEM, TAG_COLOR, TAG_DRAWABLE, "selector", "layer-list",
            "level-list", "transition", "animated-selector", "ripple", "shape", "vector",
            "animated-vector", "inset", "clip", "scale", "rotate", "animation-list",
            "bitmap", "nine-patch", "include", "merge")
    }

    override fun getApplicableAttributes(): Collection<String> {
        return listOf(ATTR_PARENT, ATTR_LAYOUT, ATTR_DRAWABLE, ATTR_COLOR, "src")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val folderType = context.resourceFolderType ?: return
        val tag = element.tagName

        when {
            folderType == VALUES && tag == TAG_STYLE -> {
                visitStyleElement(context, element)
            }
            folderType == VALUES && (tag == TAG_ITEM || tag == TAG_COLOR || tag == TAG_DRAWABLE) -> {
                visitValueItem(context, element, folderType, tag)
            }
            folderType == LAYOUT && tag == "include" -> {
                visitLayoutInclude(context, element)
            }
            folderType == COLOR || folderType == DRAWABLE -> {
                // handled via attributes and file-level tracking
                visitFileResource(context, element, folderType)
            }
        }
    }

    private fun visitStyleElement(context: XmlContext, element: Element) {
        val nameAttr = element.getAttributeNode(ATTR_NAME) ?: return
        val name = nameAttr.value.trim()
        if (name.isEmpty()) return

        val type = "style"
        val loc = context.getNameLocation(element)
        addLocation(type, name, loc)

        val parentAttr = element.getAttributeNode(ATTR_PARENT)
        if (parentAttr != null) {
            val parentValue = parentAttr.value.trim()
            if (parentValue.isNotEmpty()) {
                val parentName = stripTypePrefix(parentValue, "style")
                addEdge(type, name, parentName)
            }
        } else {
            // Implicit parent via dot notation (e.g., "Parent.Child")
            val dotIndex = name.lastIndexOf('.')
            if (dotIndex > 0) {
                val impliedParent = name.substring(0, dotIndex)
                addEdge(type, name, impliedParent)
            }
        }
    }

    private fun visitValueItem(context: XmlContext, element: Element, folderType: ResourceFolderType, tag: String) {
        val nameAttr = element.getAttributeNode(ATTR_NAME) ?: return
        val name = nameAttr.value.trim()
        if (name.isEmpty()) return

        val typeAttr = element.getAttributeNode(ATTR_TYPE)
        val type = when {
            typeAttr != null -> typeAttr.value.trim()
            tag == TAG_COLOR -> "color"
            tag == TAG_DRAWABLE -> "drawable"
            else -> return
        }

        val loc = context.getNameLocation(element)
        addLocation(type, name, loc)

        // Check text content for a reference like @type/name
        val textContent = element.textContent?.trim() ?: return
        if (textContent.startsWith("@")) {
            val (refType, refName) = parseReference(textContent) ?: return
            val resolvedType = if (refType.isNullOrEmpty()) type else refType
            addEdge(type, name, resolvedType, refName)
        }
    }

    private fun visitLayoutInclude(context: XmlContext, element: Element) {
        val layoutAttr = element.getAttributeNodeNS(null, ATTR_LAYOUT)
            ?: element.getAttributeNode(ATTR_LAYOUT) ?: return
        val layoutRef = layoutAttr.value.trim()
        val currentLayout = getLayoutName(context) ?: return

        val (_, refName) = parseReference(layoutRef) ?: return
        if (refName != null) {
            val loc = context.getLocation(element)
            addLocation("layout", currentLayout, loc)
            addEdge("layout", currentLayout, "layout", refName)
        }
    }

    private fun visitFileResource(context: XmlContext, element: Element, folderType: ResourceFolderType) {
        if (element.parentNode?.nodeName == "#document" ||
            element.ownerDocument?.documentElement == element) {
            val name = getFileResourceName(context) ?: return
            val type = folderType.getName()
            val loc = context.getLocation(element)
            addLocation(type, name, loc)
        }
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val folderType = context.resourceFolderType ?: return
        val value = attribute.value?.trim() ?: return
        if (!value.startsWith("@")) return

        val attrName = attribute.localName ?: attribute.name ?: return

        when {
            folderType == LAYOUT -> {
                if (attrName == ATTR_LAYOUT) {
                    // handled in visitElement for <include>
                    return
                }
            }
            folderType == COLOR || folderType == DRAWABLE -> {
                handleFileResourceAttribute(context, attribute, folderType, value)
            }
            folderType == VALUES -> {
                // handled in visitValueItem via text content
            }
        }

        // Handle android:layout, android:drawable, android:color, src attributes
        if (attrName == ATTR_LAYOUT || attrName == ATTR_DRAWABLE || attrName == ATTR_COLOR || attrName == "src") {
            handleGenericResourceAttribute(context, attribute, folderType, value, attrName)
        }
    }

    private fun handleFileResourceAttribute(
        context: XmlContext,
        attribute: Attr,
        folderType: ResourceFolderType,
        value: String
    ) {
        val currentName = getFileResourceName(context) ?: return
        val type = folderType.getName()
        val (refType, refName) = parseReference(value) ?: return
        val resolvedType = if (refType.isNullOrEmpty()) type else refType
        if (refName != null) {
            addEdge(type, currentName, resolvedType, refName)
        }
    }

    private fun handleGenericResourceAttribute(
        context: XmlContext,
        attribute: Attr,
        folderType: ResourceFolderType,
        value: String,
        attrName: String
    ) {
        val (refType, refName) = parseReference(value) ?: return
        if (refName == null || refType == null) return

        when (folderType) {
            LAYOUT -> {
                val currentLayout = getLayoutName(context) ?: return
                val loc = context.getLocation(attribute)
                addLocation("layout", currentLayout, loc)
                if (refType == "layout") {
                    addEdge("layout", currentLayout, "layout", refName)
                }
            }
            COLOR -> {
                val currentName = getFileResourceName(context) ?: return
                val loc = context.getLocation(attribute)
                addLocation("color", currentName, loc)
                if (refType == "color") {
                    addEdge("color", currentName, "color", refName)
                }
            }
            DRAWABLE -> {
                val currentName = getFileResourceName(context) ?: return
                val loc = context.getLocation(attribute)
                addLocation("drawable", currentName, loc)
                if (refType == "drawable") {
                    addEdge("drawable", currentName, "drawable", refName)
                }
            }
            else -> {}
        }
    }

    override fun afterCheckRootProject(context: com.android.tools.lint.detector.api.Context) {
        // Detect cycles in each resource type graph
        for ((type, edges) in graph) {
            val visited = mutableSetOf<String>()
            val inStack = mutableSetOf<String>()
            val stackList = mutableListOf<String>()

            for (node in edges.keys) {
                if (node !in visited) {
                    detectCycle(context, type, node, edges, visited, inStack, stackList)
                }
            }
        }
    }

    private fun detectCycle(
        context: com.android.tools.lint.detector.api.Context,
        type: String,
        node: String,
        edges: Map<String, List<String>>,
        visited: MutableSet<String>,
        inStack: MutableSet<String>,
        stackList: MutableList<String>
    ) {
        visited.add(node)
        inStack.add(node)
        stackList.add(node)

        val neighbors = edges[node] ?: emptyList()
        for (neighbor in neighbors) {
            if (neighbor !in visited) {
                if (neighbor in edges) {
                    detectCycle(context, type, neighbor, edges, visited, inStack, stackList)
                }
            } else if (neighbor in inStack) {
                // Found a cycle
                val cycleStart = stackList.indexOf(neighbor)
                val cycle = stackList.subList(cycleStart, stackList.size).toList()
                reportCycle(context, type, cycle, neighbor)
            }
        }

        inStack.remove(node)
        stackList.removeAt(stackList.size - 1)
    }

    private fun reportCycle(
        context: com.android.tools.lint.detector.api.Context,
        type: String,
        cycle: List<String>,
        cycleEnd: String
    ) {
        // Create a canonical key for this cycle to avoid duplicate reports
        val cycleKey = "$type:${cycle.sorted().joinToString(",")}"
        if (cycleKey in reportedCycles) return
        reportedCycles.add(cycleKey)

        val cycleDescription = (cycle + cycleEnd).joinToString(" => ")
        val message = "Cycle detected: $cycleDescription"

        // Find the best location to report
        val typeLocations = locations[type]
        var location: Location? = null
        for (node in cycle) {
            val nodeLoc = typeLocations?.get(node)
            if (nodeLoc != null) {
                location = nodeLoc
                break
            }
        }

        if (location != null) {
            context.report(ISSUE, location, message)
        } else {
            // Report without location using project-level reporting
            val projectDir = context.project.dir
            val fallbackLocation = Location.create(projectDir)
            context.report(ISSUE, fallbackLocation, message)
        }
    }

    // ---- Helpers ----

    private fun addEdge(fromType: String, fromName: String, toName: String) {
        addEdge(fromType, fromName, fromType, toName)
    }

    private fun addEdge(fromType: String, fromName: String, toType: String, toName: String) {
        if (fromType != toType) return // only track same-type cycles for now
        val typeGraph = graph.getOrPut(fromType) { mutableMapOf() }
        val edges = typeGraph.getOrPut(fromName) { mutableListOf() }
        if (!edges.contains(toName)) {
            edges.add(toName)
        }
    }

    private fun addLocation(type: String, name: String, loc: Location) {
        val typeLocations = locations.getOrPut(type) { mutableMapOf() }
        if (!typeLocations.containsKey(name)) {
            typeLocations[name] = loc
        }
    }

    private fun getLayoutName(context: XmlContext): String? {
        val file = context.file
        val fileName = file.name
        return if (fileName.endsWith(DOT_XML)) fileName.dropLast(DOT_XML.length) else null
    }

    private fun getFileResourceName(context: XmlContext): String? {
        val file = context.file
        val fileName = file.name
        return if (fileName.endsWith(DOT_XML)) fileName.dropLast(DOT_XML.length) else null
    }

    /**
     * Parses a resource reference like @type/name or @android:type/name.
     * Returns a Pair of (type, name), either of which may be null if not parseable.
     */
    private fun parseReference(ref: String): Pair<String?, String?>? {
        if (!ref.startsWith("@")) return null
        var s = ref.substring(1) // strip @
        if (s.startsWith("+")) s = s.substring(1) // strip + for @+id/...

        // Strip namespace prefix like "android:"
        val colonIdx = s.indexOf(':')
        if (colonIdx >= 0) {
            s = s.substring(colonIdx + 1)
        }

        val slashIdx = s.indexOf('/')
        return if (slashIdx >= 0) {
            val type = s.substring(0, slashIdx)
            val name = s.substring(slashIdx + 1)
            Pair(type, name)
        } else {
            Pair(null, s)
        }
    }

    /**
     * Strips type prefix from a style parent reference, e.g. "@style/Foo" -> "Foo".
     */
    private fun stripTypePrefix(value: String, expectedType: String): String {
        if (value.startsWith("@")) {
            val (_, name) = parseReference(value) ?: return value
            return name ?: value
        }
        return value
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "ResourceCycle",
            briefDescription = "Cycle in resource definitions",
            explanation = """
                There should be no cycles in resource definitions as this can lead to \
                runtime exceptions at runtime.
                """,
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.FATAL,
            implementation = Implementation(
                ResourceCycleDetector::class.java,
                Scope.ALL_RESOURCES_SCOPE
            )
        )
    }
}