package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.ResourceUrl
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Attr
import org.w3c.dom.Element
import org.w3c.dom.Node

class ResourceCycleDetector : ResourceXmlDetector(), XmlScanner {

  override fun beforeCheckRootProject(context: Context) {
    graph.clear()
    definitions.clear()
    reportedCycles.clear()
  }

  override fun appliesTo(resourceType: ResourceType): Boolean = true

  override fun getApplicableElements(): Collection<String>? = XmlScanner.ALL

  override fun getApplicableAttributes(): Collection<String>? = XmlScanner.ALL

  override fun visitElement(context: XmlContext, element: Element) {
    val ref = getResourceRef(context, element)
    if (ref != null) {
      definitions[ref] = context.getElementLocation(element)
      handleStyleParent(context, ref, element)
    }

    if (!element.hasElementChildren()) {
      val source = findEnclosingResourceRef(context, element) ?: return
      val text = element.textContent?.trim().orEmpty()
      if (text.isNotEmpty()) {
        addResourceReference(context, source, text, context.getElementLocation(element))
      }
    }
  }

  override fun visitAttribute(context: XmlContext, attribute: Attr) {
    val owner = attribute.ownerElement ?: return
    val source = findEnclosingResourceRef(context, owner) ?: return
    val value = attribute.value ?: return
    addResourceReference(context, source, value, context.getValueLocation(attribute))
  }

  override fun afterCheckRootProject(context: Context) {
    detectCycles(context)
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "ResourceCycle",
      briefDescription = "Cycle in resource definitions",
      explanation = "There should be no cycles in resource definitions as this can lead to runtime exceptions.",
      category = Category.CORRECTNESS,
      priority = 10,
      severity = Severity.FATAL,
      implementation = Implementation(ResourceCycleDetector::class.java, Scope.ALL_RESOURCES)
    )
  }

  private data class ResourceRef(val type: ResourceType, val name: String)

  private val graph = mutableMapOf<ResourceRef, MutableMap<ResourceRef, Location>>()
  private val definitions = mutableMapOf<ResourceRef, Location>()
  private val reportedCycles = mutableSetOf<Set<ResourceRef>>()

  private fun getResourceRef(context: XmlContext, element: Element): ResourceRef? {
    if (element.tagName == "resources") return null

    val folderType = context.resourceFolderType
    val folderResourceType = folderType?.getResourceType()
    if (folderResourceType != null && element == element.ownerDocument.documentElement) {
      val name = context.file.name.substringBeforeLast('.')
      return ResourceRef(folderResourceType, name)
    }

    val type = tagToResourceType(element.tagName) ?: return null
    val name = element.getAttribute("name").takeIf { it.isNotEmpty() } ?: return null
    return ResourceRef(type, name)
  }

  private fun findEnclosingResourceRef(context: XmlContext, element: Element): ResourceRef? {
    var current: Node? = element
    while (current != null && current is Element) {
      getResourceRef(context, current)?.let { return it }
      current = current.parentNode
    }
    return null
  }

  private fun tagToResourceType(tag: String): ResourceType? = when (tag) {
    "integer-array", "string-array" -> ResourceType.ARRAY
    else -> try {
      ResourceType.valueOf(tag.uppercase())
    } catch (e: IllegalArgumentException) {
      null
    }
  }

  private fun addResourceReference(
    context: XmlContext,
    source: ResourceRef,
    value: String,
    location: Location
  ) {
    val url = ResourceUrl.parse(value) ?: return
    if (url.isFramework) return
    if (url.type == ResourceType.ATTR) return
    if (url.name.isEmpty()) return
    val target = ResourceRef(url.type, url.name)
    graph.getOrPut(source) { mutableMapOf() }[target] = location
  }

  private fun handleStyleParent(context: XmlContext, ref: ResourceRef, element: Element) {
    if (ref.type != ResourceType.STYLE) return

    val name = element.getAttribute("name")
    if (name.contains('.')) {
      val parentName = name.substringBeforeLast('.')
      val target = ResourceRef(ResourceType.STYLE, parentName)
      graph.getOrPut(ref) { mutableMapOf() }[target] = context.getElementLocation(element)
    }

    val parent = element.getAttribute("parent")
    if (parent.isNotEmpty()) {
      val url = ResourceUrl.parse(parent)
      if (url != null && !url.isFramework && url.type != ResourceType.ATTR) {
        graph.getOrPut(ref) { mutableMapOf() }[ResourceRef(url.type, url.name)] =
          context.getElementLocation(element)
      } else if (url == null && !parent.contains(':')) {
        graph.getOrPut(ref) { mutableMapOf() }[ResourceRef(ResourceType.STYLE, parent)] =
          context.getElementLocation(element)
      }
    }
  }

  private fun Element.hasElementChildren(): Boolean {
    val children = childNodes
    for (i in 0 until children.length) {
      if (children.item(i).nodeType == Node.ELEMENT_NODE) return true
    }
    return false
  }

  private fun detectCycles(context: Context) {
    val visited = mutableSetOf<ResourceRef>()
    val stack = mutableListOf<ResourceRef>()
    val stackSet = mutableSetOf<ResourceRef>()

    fun dfs(node: ResourceRef) {
      visited.add(node)
      stack.add(node)
      stackSet.add(node)

      val edges = graph[node] ?: emptyMap()
      for ((target, location) in edges) {
        if (target in stackSet) {
          val index = stack.indexOf(target)
          if (index >= 0) {
            val cycleNodes = stack.subList(index, stack.size) + target
            val nodeSet = cycleNodes.toSet()
            if (reportedCycles.add(nodeSet)) {
              val message = buildString {
                append("Cycle in resource definitions: ")
                append(cycleNodes.joinToString(" → ") { "${it.type.name}/${it.name}" })
              }
              context.report(ISSUE, location, message)
            }
          }
        } else if (target !in visited) {
          dfs(target)
        }
      }

      stack.removeAt(stack.size - 1)
      stackSet.remove(node)
    }

    for (node in graph.keys) {
      if (node !in visited) dfs(node)
    }
  }
}