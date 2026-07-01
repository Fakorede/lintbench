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
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

class ResourceCycleDetector : Detector(), XmlScanner {

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
            severity = Severity.FATAL,
            implementation = Implementation(
                ResourceCycleDetector::class.java,
                Scope.ALL_RESOURCES_SCOPE
            )
        )

        private val RESOURCE_REFERENCE_PATTERN = Regex("^@\\+?(?:[^/:]+:)?([^/]+)/(.+)$")
    }

    // Graph: type -> (name -> list of referenced names)
    private val graphs: MutableMap<ResourceType, MutableMap<String, MutableList<String>>> =
        mutableMapOf()

    // Location of each resource definition: type -> (name -> location)
    private val locations: MutableMap<ResourceType, MutableMap<String, Location>> = mutableMapOf()

    // Edge locations: type -> (fromName -> list of (toName, location))
    private val edgeLocations: MutableMap<ResourceType, MutableMap<String, MutableList<Pair<String, Location>>>> =
        mutableMapOf()

    override fun getApplicableElements(): Collection<String> = XmlScanner.ALL

    override fun visitElement(context: XmlContext, element: Element) {
        val file = context.file
        val folderName = file.parentFile?.name ?: return
        val folderType = ResourceFolderType.getFolderType(folderName)

        if (folderType == ResourceFolderType.VALUES) {
            handleValuesElement(context, element)
        } else if (folderType != null) {
            handleFileResourceElement(context, element, folderType, file.nameWithoutExtension)
        }
    }

    private fun handleValuesElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            TAG_STYLE -> handleStyle(context, element)
            TAG_ITEM -> handleValueItem(context, element)
            else -> handleOtherValueElement(context, element)
        }
    }

    private fun handleStyle(context: XmlContext, element: Element) {
        val name = element.getAttribute(ATTR_NAME).trim().takeIf { it.isNotEmpty() } ?: return
        val parentAttr = element.getAttribute(ATTR_PARENT)

        val resolvedParent: String? = when {
            parentAttr.isNotEmpty() -> {
                // Explicit parent attribute
                val stripped = stripResourcePrefix(parentAttr, ResourceType.STYLE)
                when {
                    stripped != null -> stripped
                    !parentAttr.startsWith("@") && !parentAttr.startsWith("?") && !parentAttr.contains(':') -> {
                        // Plain style name reference
                        parentAttr.trim().takeIf { it.isNotEmpty() }
                    }
                    else -> null
                }
            }
            name.contains('.') -> {
                // Implicit parent via dot notation
                name.substringBeforeLast('.')
            }
            else -> null
        }

        val location = context.getLocation(element)
        recordResource(ResourceType.STYLE, name, resolvedParent, location, location)
    }

    private fun handleValueItem(context: XmlContext, element: Element) {
        val typeName = element.getAttribute(ATTR_TYPE).trim().takeIf { it.isNotEmpty() } ?: return
        val name = element.getAttribute(ATTR_NAME).trim().takeIf { it.isNotEmpty() } ?: return
        val type = ResourceType.fromXmlValue(typeName) ?: return

        val text = getTextContent(element).trim()
        val location = context.getLocation(element)

        if (text.startsWith("@")) {
            val ref = stripResourcePrefix(text, type)
            recordResource(type, name, ref, location, location)
        } else {
            ensureResourceExists(type, name, location)
        }
    }

    private fun handleOtherValueElement(context: XmlContext, element: Element) {
        // Handle <color>, <drawable>, <string>, etc. in values files
        val tagName = element.tagName
        val type = ResourceType.fromXmlTagName(tagName) ?: return
        val name = element.getAttribute(ATTR_NAME).trim().takeIf { it.isNotEmpty() } ?: return

        val text = getTextContent(element).trim()
        val location = context.getLocation(element)

        if (text.startsWith("@")) {
            val ref = stripResourcePrefix(text, type)
            recordResource(type, name, ref, location, location)
        } else {
            ensureResourceExists(type, name, location)
        }
    }

    private fun handleFileResourceElement(
        context: XmlContext,
        element: Element,
        folderType: ResourceFolderType,
        resourceName: String
    ) {
        val isRoot = element.parentNode is Document
        val location = context.getLocation(element)

        when (folderType) {
            ResourceFolderType.LAYOUT -> {
                if (isRoot) {
                    ensureResourceExists(ResourceType.LAYOUT, resourceName, location)
                }
                // Check for <include> tags
                if (element.tagName == "include") {
                    val layout = element.getAttribute("layout").trim()
                    if (layout.isNotEmpty()) {
                        val ref = stripResourcePrefix(layout, ResourceType.LAYOUT)
                        if (ref != null) {
                            recordResource(ResourceType.LAYOUT, resourceName, ref, location, location)
                        }
                    }
                }
            }
            ResourceFolderType.DRAWABLE -> {
                if (isRoot) {
                    ensureResourceExists(ResourceType.DRAWABLE, resourceName, location)
                }
                handleDrawableElement(context, element, resourceName, location)
            }
            ResourceFolderType.COLOR -> {
                if (isRoot) {
                    ensureResourceExists(ResourceType.COLOR, resourceName, location)
                }
                handleColorElement(context, element, resourceName, location)
            }
            ResourceFolderType.ANIM -> {
                if (isRoot) {
                    ensureResourceExists(ResourceType.ANIM, resourceName, location)
                }
            }
            ResourceFolderType.ANIMATOR -> {
                if (isRoot) {
                    ensureResourceExists(ResourceType.ANIMATOR, resourceName, location)
                }
            }
            ResourceFolderType.FONT -> {
                if (isRoot) {
                    ensureResourceExists(ResourceType.FONT, resourceName, location)
                }
                handleFontElement(context, element, resourceName, location)
            }
            ResourceFolderType.MIPMAP -> {
                if (isRoot) {
                    ensureResourceExists(ResourceType.MIPMAP, resourceName, location)
                }
            }
            ResourceFolderType.XML -> {
                if (isRoot) {
                    ensureResourceExists(ResourceType.XML, resourceName, location)
                }
            }
            ResourceFolderType.MENU -> {
                if (isRoot) {
                    ensureResourceExists(ResourceType.MENU, resourceName, location)
                }
            }
            else -> {}
        }
    }

    private fun handleDrawableElement(
        context: XmlContext,
        element: Element,
        resourceName: String,
        location: Location
    ) {
        // Check android:drawable attribute (selector, layer-list items)
        checkAttrRef(element, "android:drawable", ResourceType.DRAWABLE, resourceName, location)
        // Check android:src attribute
        checkAttrRef(element, "android:src", ResourceType.DRAWABLE, resourceName, location)
        // For adaptive icons
        checkAttrRef(element, "android:drawable", ResourceType.MIPMAP, resourceName, location)

        // Check item elements with drawable reference
        if (element.tagName == TAG_ITEM) {
            val drawable = element.getAttribute("android:drawable").trim()
            if (drawable.startsWith("@")) {
                val ref = stripResourcePrefix(drawable, ResourceType.DRAWABLE)
                if (ref != null) {
                    recordResource(ResourceType.DRAWABLE, resourceName, ref, location, location)
                }
            }
        }
    }

    private fun handleColorElement(
        context: XmlContext,
        element: Element,
        resourceName: String,
        location: Location
    ) {
        // Check android:color attribute in color state list items
        checkAttrRef(element, "android:color", ResourceType.COLOR, resourceName, location)

        if (element.tagName == TAG_ITEM) {
            val color = element.getAttribute("android:color").trim()
            if (color.startsWith("@")) {
                val ref = stripResourcePrefix(color, ResourceType.COLOR)
                if (ref != null) {
                    recordResource(ResourceType.COLOR, resourceName, ref, location, location)
                }
            }
        }
    }

    private fun handleFontElement(
        context: XmlContext,
        element: Element,
        resourceName: String,
        location: Location
    ) {
        if (element.tagName == "font") {
            // Check android:font and app:font attributes
            for (attr in listOf("android:font", "app:font")) {
                val fontRef = element.getAttribute(attr).trim()
                if (fontRef.startsWith("@")) {
                    val ref = stripResourcePrefix(fontRef, ResourceType.FONT)
                    if (ref != null) {
                        recordResource(ResourceType.FONT, resourceName, ref, location, location)
                    }
                }
            }
        }
    }

    private fun checkAttrRef(
        element: Element,
        attrName: String,
        type: ResourceType,
        resourceName: String,
        location: Location
    ) {
        val value = element.getAttribute(attrName).trim()
        if (value.startsWith("@")) {
            val ref = stripResourcePrefix(value, type)
            if (ref != null) {
                recordResource(type, resourceName, ref, location, location)
            }
        }
    }

    private fun getTextContent(element: Element): String {
        val sb = StringBuilder()
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.TEXT_NODE) {
                sb.append(child.nodeValue)
            }
        }
        return sb.toString().trim()
    }

    private fun stripResourcePrefix(text: String, type: ResourceType): String? {
        if (text.isBlank()) return null
        if (!text.startsWith("@")) return null

        val match = RESOURCE_REFERENCE_PATTERN.matchEntire(text) ?: return null
        val refType = match.groupValues[1].trim()
        val refName = match.groupValues[2].trim()

        return if (refType == type.getName()) refName else null
    }

    private fun ensureResourceExists(type: ResourceType, name: String, location: Location) {
        val graph = graphs.getOrPut(type) { mutableMapOf() }
        graph.getOrPut(name) { mutableListOf() }
        val locs = locations.getOrPut(type) { mutableMapOf() }
        if (!locs.containsKey(name)) {
            locs[name] = location
        }
    }

    private fun recordResource(
        type: ResourceType,
        name: String,
        reference: String?,
        nodeLocation: Location,
        edgeLocation: Location
    ) {
        val graph = graphs.getOrPut(type) { mutableMapOf() }
        val refs = graph.getOrPut(name) { mutableListOf() }

        val locs = locations.getOrPut(type) { mutableMapOf() }
        if (!locs.containsKey(name)) {
            locs[name] = nodeLocation
        }

        if (reference != null && reference.isNotEmpty() && !refs.contains(reference)) {
            refs.add(reference)
            val edgeLocs = edgeLocations.getOrPut(type) { mutableMapOf() }
            val edgeList = edgeLocs.getOrPut(name) { mutableListOf() }
            edgeList.add(Pair(reference, edgeLocation))
        }
    }

    override fun afterCheckRootProject(context: Context) {
        for ((type, graph) in graphs) {
            val visited = mutableSetOf<String>()
            val inStack = mutableSetOf<String>()
            val reported = mutableSetOf<String>()

            for (node in graph.keys) {
                if (node !in visited) {
                    detectCycle(
                        context, type, graph, node,
                        visited, inStack, mutableListOf(), reported
                    )
                }
            }
        }
    }

    private fun detectCycle(
        context: Context,
        type: ResourceType,
        graph: Map<String, List<String>>,
        node: String,
        visited: MutableSet<String>,
        inStack: MutableSet<String>,
        path: MutableList<String>,
        reported: MutableSet<String>
    ) {
        visited.add(node)
        inStack.add(node)
        path.add(node)

        val neighbors = graph[node] ?: emptyList()
        for (neighbor in neighbors) {
            if (neighbor !in visited) {
                detectCycle(context, type, graph, neighbor, visited, inStack, path, reported)
            } else if (neighbor in inStack) {
                val cycleKey = buildCycleKey(type, path, neighbor)
                if (cycleKey !in reported) {
                    reported.add(cycleKey)
                    reportCycle(context, type, path, neighbor)
                }
            }
        }

        path.removeAt(path.size - 1)
        inStack.remove(node)
    }

    private fun buildCycleKey(type: ResourceType, path: List<String>, cycleStart: String): String {
        val cycleIndex = path.indexOf(cycleStart)
        val cyclePath = if (cycleIndex >= 0) {
            path.subList(cycleIndex, path.size)
        } else {
            path
        }
        return type.getName() + ":" + cyclePath.sorted().joinToString(",")
    }

    private fun reportCycle(
        context: Context,
        type: ResourceType,
        path: List<String>,
        cycleStart: String
    ) {
        val cycleIndex = path.indexOf(cycleStart)
        val cyclePath = if (cycleIndex >= 0) {
            path.subList(cycleIndex, path.size) + cycleStart
        } else {
            path + cycleStart
        }

        val typeName = type.getName()
        val cycleDescription = cyclePath.joinToString(" => ") { "@$typeName/$it" }

        val locs = locations[type]
        // Try to get location of the cycle start node
        val location = locs?.get(cycleStart)
            ?: locs?.get(path.lastOrNull())
            ?: locs?.values?.firstOrNull()

        val message = "Cycle detected: $cycleDescription"

        if (location != null) {
            context.report(ISSUE, location, message)
        }
    }
}