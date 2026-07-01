package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_COLOR
import com.android.SdkConstants.ATTR_DRAWABLE
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_PARENT
import com.android.SdkConstants.ATTR_TYPE
import com.android.SdkConstants.TAG_COLOR
import com.android.SdkConstants.TAG_DRAWABLE
import com.android.SdkConstants.TAG_ITEM
import com.android.SdkConstants.TAG_STYLE
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.tools.lint.detector.api.Category
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

    // Maps resource type -> (resource name -> list of referenced resource names)
    private val dependencyGraph = mutableMapOf<ResourceType, MutableMap<String, MutableList<String>>>()

    // Maps resource type -> (resource name -> Location) for error reporting
    private val locationMap = mutableMapOf<ResourceType, MutableMap<String, Location>>()

    override fun beforeCheckRootProject(context: com.android.tools.lint.detector.api.Context) {
        dependencyGraph.clear()
        locationMap.clear()
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES ||
            folderType == ResourceFolderType.COLOR ||
            folderType == ResourceFolderType.DRAWABLE ||
            folderType == ResourceFolderType.LAYOUT ||
            folderType == ResourceFolderType.ANIM ||
            folderType == ResourceFolderType.ANIMATOR ||
            folderType == ResourceFolderType.XML
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(
            TAG_STYLE,
            TAG_COLOR,
            TAG_DRAWABLE,
            TAG_ITEM,
            "selector",
            "shape",
            "layer-list",
            "level-list",
            "transition",
            "ripple"
        )
    }

    override fun getApplicableAttributes(): Collection<String> {
        return listOf(
            ATTR_PARENT,
            ATTR_COLOR,
            ATTR_DRAWABLE,
            "src"
        )
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val tag = element.tagName ?: return

        when (tag) {
            TAG_STYLE -> {
                val nameAttr = element.getAttributeNS(null, ATTR_NAME) ?: return
                if (nameAttr.isEmpty()) return

                val parentAttr = element.getAttributeNS(null, ATTR_PARENT)
                val styleName = nameAttr
                val location = context.getLocation(element)

                addToLocationMap(ResourceType.STYLE, styleName, location)

                // Explicit parent via parent attribute
                if (!parentAttr.isNullOrEmpty()) {
                    val parentName = stripResourcePrefix(parentAttr)
                    addDependency(ResourceType.STYLE, styleName, parentName)
                }

                // Implicit parent via dot notation (e.g., "ParentStyle.ChildStyle")
                val dotIndex = styleName.lastIndexOf('.')
                if (dotIndex > 0 && parentAttr.isNullOrEmpty()) {
                    val implicitParent = styleName.substring(0, dotIndex)
                    addDependency(ResourceType.STYLE, styleName, implicitParent)
                }
            }

            TAG_COLOR -> {
                val nameAttr = element.getAttributeNS(null, ATTR_NAME) ?: return
                if (nameAttr.isEmpty()) return

                val textContent = element.textContent?.trim() ?: return
                val location = context.getLocation(element)

                addToLocationMap(ResourceType.COLOR, nameAttr, location)

                if (textContent.startsWith("@color/")) {
                    val referencedName = textContent.substring("@color/".length)
                    addDependency(ResourceType.COLOR, nameAttr, referencedName)
                }
            }

            TAG_ITEM -> {
                val typeAttr = element.getAttributeNS(null, ATTR_TYPE)
                val nameAttr = element.getAttributeNS(null, ATTR_NAME) ?: return
                if (nameAttr.isEmpty()) return

                val textContent = element.textContent?.trim() ?: ""
                val location = context.getLocation(element)

                when (typeAttr) {
                    "color" -> {
                        addToLocationMap(ResourceType.COLOR, nameAttr, location)
                        if (textContent.startsWith("@color/")) {
                            val ref = textContent.substring("@color/".length)
                            addDependency(ResourceType.COLOR, nameAttr, ref)
                        }
                    }
                    "drawable" -> {
                        addToLocationMap(ResourceType.DRAWABLE, nameAttr, location)
                        if (textContent.startsWith("@drawable/")) {
                            val ref = textContent.substring("@drawable/".length)
                            addDependency(ResourceType.DRAWABLE, nameAttr, ref)
                        }
                    }
                    "style" -> {
                        addToLocationMap(ResourceType.STYLE, nameAttr, location)
                        val parentAttr = element.getAttributeNS(null, ATTR_PARENT)
                        if (!parentAttr.isNullOrEmpty()) {
                            val parentName = stripResourcePrefix(parentAttr)
                            addDependency(ResourceType.STYLE, nameAttr, parentName)
                        }
                    }
                    else -> {
                        // Check text content for any resource reference cycles
                        if (textContent.startsWith("@")) {
                            val resourceType = getResourceTypeFromReference(textContent)
                            val refName = getResourceNameFromReference(textContent)
                            if (resourceType != null && refName != null && nameAttr.isNotEmpty()) {
                                addToLocationMap(resourceType, nameAttr, location)
                                addDependency(resourceType, nameAttr, refName)
                            }
                        }
                    }
                }
            }

            TAG_DRAWABLE -> {
                val nameAttr = element.getAttributeNS(null, ATTR_NAME) ?: return
                if (nameAttr.isEmpty()) return

                val textContent = element.textContent?.trim() ?: ""
                val location = context.getLocation(element)

                addToLocationMap(ResourceType.DRAWABLE, nameAttr, location)

                if (textContent.startsWith("@drawable/")) {
                    val ref = textContent.substring("@drawable/".length)
                    addDependency(ResourceType.DRAWABLE, nameAttr, ref)
                }
            }
        }
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val attrName = attribute.localName ?: attribute.name ?: return
        val value = attribute.value ?: return

        val ownerElement = attribute.ownerElement ?: return
        val elementTag = ownerElement.tagName ?: return

        when (attrName) {
            ATTR_PARENT -> {
                if (elementTag == TAG_STYLE) {
                    // Already handled in visitElement
                    return
                }
            }

            ATTR_COLOR -> {
                if (value.startsWith("@color/")) {
                    val refName = value.substring("@color/".length)
                    val elementName = getElementResourceName(ownerElement) ?: return
                    val location = context.getLocation(attribute)
                    addToLocationMap(ResourceType.COLOR, elementName, location)
                    addDependency(ResourceType.COLOR, elementName, refName)
                }
            }

            ATTR_DRAWABLE, "src" -> {
                if (value.startsWith("@drawable/")) {
                    val refName = value.substring("@drawable/".length)
                    val elementName = getElementResourceName(ownerElement) ?: return
                    val location = context.getLocation(attribute)
                    addToLocationMap(ResourceType.DRAWABLE, elementName, location)
                    addDependency(ResourceType.DRAWABLE, elementName, refName)
                }
            }
        }
    }

    override fun afterCheckRootProject(context: com.android.tools.lint.detector.api.Context) {
        // Detect cycles in each resource type graph
        for ((resourceType, graph) in dependencyGraph) {
            val visited = mutableSetOf<String>()
            val inStack = mutableSetOf<String>()
            val cycleChains = mutableListOf<List<String>>()

            for (node in graph.keys) {
                if (node !in visited) {
                    detectCycle(node, graph, visited, inStack, mutableListOf(), cycleChains)
                }
            }

            for (cycle in cycleChains) {
                val cycleDescription = cycle.joinToString(" -> ")
                val startNode = cycle.firstOrNull() ?: continue
                val location = locationMap[resourceType]?.get(startNode)
                    ?: continue

                context.report(
                    ISSUE,
                    location,
                    "Cycle detected in resource definitions: $cycleDescription"
                )
            }
        }
    }

    private fun detectCycle(
        node: String,
        graph: Map<String, List<String>>,
        visited: MutableSet<String>,
        inStack: MutableSet<String>,
        currentPath: MutableList<String>,
        cycles: MutableList<List<String>>
    ) {
        visited.add(node)
        inStack.add(node)
        currentPath.add(node)

        val neighbors = graph[node] ?: emptyList()
        for (neighbor in neighbors) {
            if (neighbor !in visited) {
                if (neighbor in graph) {
                    detectCycle(neighbor, graph, visited, inStack, currentPath, cycles)
                }
            } else if (neighbor in inStack) {
                // Found a cycle, extract it
                val cycleStart = currentPath.indexOf(neighbor)
                if (cycleStart >= 0) {
                    val cycle = currentPath.subList(cycleStart, currentPath.size).toMutableList()
                    cycle.add(neighbor) // close the cycle
                    cycles.add(cycle.toList())
                }
            }
        }

        inStack.remove(node)
        currentPath.removeAt(currentPath.size - 1)
    }

    private fun addDependency(type: ResourceType, from: String, to: String) {
        val typeGraph = dependencyGraph.getOrPut(type) { mutableMapOf() }
        val deps = typeGraph.getOrPut(from) { mutableListOf() }
        if (to !in deps) {
            deps.add(to)
        }
    }

    private fun addToLocationMap(type: ResourceType, name: String, location: Location) {
        val typeMap = locationMap.getOrPut(type) { mutableMapOf() }
        if (name !in typeMap) {
            typeMap[name] = location
        }
    }

    private fun stripResourcePrefix(value: String): String {
        return when {
            value.startsWith("@style/") -> value.substring("@style/".length)
            value.startsWith("@android:style/") -> value.substring("@android:style/".length)
            value.startsWith("@color/") -> value.substring("@color/".length)
            value.startsWith("@drawable/") -> value.substring("@drawable/".length)
            else -> value
        }
    }

    private fun getResourceTypeFromReference(reference: String): ResourceType? {
        return when {
            reference.startsWith("@color/") -> ResourceType.COLOR
            reference.startsWith("@drawable/") -> ResourceType.DRAWABLE
            reference.startsWith("@style/") -> ResourceType.STYLE
            reference.startsWith("@layout/") -> ResourceType.LAYOUT
            reference.startsWith("@anim/") -> ResourceType.ANIM
            reference.startsWith("@animator/") -> ResourceType.ANIMATOR
            else -> null
        }
    }

    private fun getResourceNameFromReference(reference: String): String? {
        val slashIndex = reference.indexOf('/')
        return if (slashIndex >= 0 && slashIndex < reference.length - 1) {
            reference.substring(slashIndex + 1)
        } else null
    }

    private fun getElementResourceName(element: Element): String? {
        val nameAttr = element.getAttributeNS(null, ATTR_NAME)
        if (!nameAttr.isNullOrEmpty()) return nameAttr

        val idAttr = element.getAttributeNS(ANDROID_URI, ATTR_ID)
        if (!idAttr.isNullOrEmpty()) {
            return when {
                idAttr.startsWith("@+id/") -> idAttr.substring("@+id/".length)
                idAttr.startsWith("@id/") -> idAttr.substring("@id/".length)
                else -> idAttr
            }
        }

        return null
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