package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner

class ResourceCycleDetector : ResourceXmlDetector() {

  private val graph = mutableMapOf<ResourceRef, MutableList<Edge>>()
  private val elementSources = mutableMapOf<org.w3c.dom.Element, ResourceRef>()
  private val fileResourceCache = mutableMapOf<java.io.File, ResourceRef>()

  override fun beforeCheckRootProject(context: Context) {
    graph.clear()
    elementSources.clear()
    fileResourceCache.clear()
  }

  override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean {
    return folderType != com.android.resources.ResourceFolderType.RAW
  }

  override fun getApplicableElements(): Collection<String> {
    return listOf(XmlScanner.ALL)
  }

  override fun getApplicableAttributes(): Collection<String> {
    return listOf(XmlScanner.ALL)
  }

  override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
    val folderType = context.resourceFolderType ?: return
    val source = when (folderType) {
      com.android.resources.ResourceFolderType.VALUES -> {
        val tag = element.localName ?: element.tagName
        val name = element.getAttribute("name")
        val typeAttr = element.getAttribute("type").takeIf { it.isNotEmpty() }
        val type = tagToType(tag, typeAttr)
        if (type != null && name.isNotEmpty()) {
          ResourceRef(type, name)
        } else {
          val parent = element.parentNode as? org.w3c.dom.Element
          if (parent != null) elementSources[parent] else null
        }
      }
      else -> fileResourceCache.getOrPut(context.file) {
        val type = folderTypeToResourceType(folderType) ?: return@getOrPut null
        ResourceRef(type, context.file.name.substringBeforeLast('.'))
      }
    }
    elementSources[element] = source
  }

  override fun visitAttribute(context: XmlContext, attribute: org.w3c.dom.Attr) {
    val owner = attribute.ownerElement ?: return
    val source = elementSources[owner] ?: return
    val value = attribute.value ?: return
    val location = context.getValueLocation(attribute)

    for (target in findResourceReferences(value)) {
      addEdge(source, target, location)
    }

    if (attribute.name == "parent" && source.type == "style") {
      val parentStyle = parseStyleParent(value)
      if (parentStyle != null) addEdge(source, parentStyle, location)
    }
  }

  override fun afterCheckRootProject(context: Context) {
    val state = mutableMapOf<ResourceRef, Int>()
    val parent = mutableMapOf<ResourceRef, ResourceRef>()
    val reported = mutableSetOf<String>()

    fun reportCycle(start: ResourceRef, end: ResourceRef, closingLocation: Location) {
      val cycle = mutableListOf<ResourceRef>()
      var current: ResourceRef? = end
      while (current != null && current != start) {
        cycle.add(current)
        current = parent[current]
      }
      if (current == start) {
        cycle.add(start)
        cycle.reverse()

        val signature = canonicalCycleSignature(cycle)
        if (reported.add(signature)) {
          val message = buildString {
            append("Cycle in resource definitions: ")
            for (i in cycle.indices) {
              if (i > 0) append(" -> ")
              val ref = cycle[i]
              append(ref.type).append('/').append(ref.name)
            }
            append(" -> ").append(start.type).append('/').append(start.name)
          }
          context.report(ISSUE, closingLocation, message)
        }
      }
    }

    fun dfs(u: ResourceRef) {
      state[u] = 1
      for (edge in graph[u].orEmpty()) {
        val v = edge.to
        when (state[v]) {
          null -> {
            parent[v] = u
            dfs(v)
          }
          1 -> reportCycle(start = v, end = u, closingLocation = edge.location)
        }
      }
      state[u] = 2
    }

    for (node in graph.keys.toList()) {
      if (state[node] == null) dfs(node)
    }
  }

  private fun addEdge(from: ResourceRef, to: ResourceRef, location: Location) {
    graph.getOrPut(from) { mutableListOf() }.add(Edge(to, location))
  }

  private fun findResourceReferences(value: String): List<ResourceRef> {
    val result = mutableListOf<ResourceRef>()
    val refRegex = Regex("""@\*?(?:\w+:)?(?:\+)?(\w+)/([\w.]+)""")
    for (match in refRegex.findAll(value)) {
      result.add(ResourceRef(match.groupValues[1], match.groupValues[2]))
    }
    val themeRegex = Regex("""\?(?:\w+:)?(?:attr/)?([\w.]+)""")
    for (match in themeRegex.findAll(value)) {
      result.add(ResourceRef("attr", match.groupValues[1]))
    }
    return result
  }

  private fun parseStyleParent(value: String): ResourceRef? {
    val trimmed = value.trim()
    if (trimmed.isEmpty() || trimmed.startsWith('@') || trimmed.startsWith('?')) return null
    return ResourceRef("style", trimmed)
  }

  private fun tagToType(tag: String, typeAttr: String?): String? {
    return if (typeAttr != null && tag == "item") typeAttr else TAG_TO_TYPE[tag]
  }

  private fun folderTypeToResourceType(
    folderType: com.android.resources.ResourceFolderType
  ): String? {
    return when (folderType) {
      com.android.resources.ResourceFolderType.ANIM -> "anim"
      com.android.resources.ResourceFolderType.ANIMATOR -> "animator"
      com.android.resources.ResourceFolderType.COLOR -> "color"
      com.android.resources.ResourceFolderType.DRAWABLE -> "drawable"
      com.android.resources.ResourceFolderType.FONT -> "font"
      com.android.resources.ResourceFolderType.LAYOUT -> "layout"
      com.android.resources.ResourceFolderType.MENU -> "menu"
      com.android.resources.ResourceFolderType.MIPMAP -> "mipmap"
      com.android.resources.ResourceFolderType.NAVIGATION -> "navigation"
      com.android.resources.ResourceFolderType.TRANSITION -> "transition"
      com.android.resources.ResourceFolderType.XML -> "xml"
      else -> null
    }
  }

  private fun canonicalCycleSignature(cycle: List<ResourceRef>): String {
    val nodes = cycle.toMutableList()
    if (nodes.size > 1 && nodes.first() == nodes.last()) {
      nodes.removeAt(nodes.lastIndex)
    }
    if (nodes.isEmpty()) return ""

    val strings = nodes.map { "${it.type}/${it.name}" }
    val rotations = mutableListOf<List<String>>()
    for (i in strings.indices) {
      rotations.add(strings.subList(i, strings.size) + strings.subList(0, i))
    }
    val candidates = rotations + rotations.map { it.reversed() }
    return candidates.minByOrNull { it.joinToString("->") }?.joinToString("->") ?: ""
  }

  private data class ResourceRef(val type: String, val name: String)
  private data class Edge(val to: ResourceRef, val location: Location)

  private val TAG_TO_TYPE = mapOf(
    "string" to "string",
    "plurals" to "plurals",
    "string-array" to "array",
    "integer-array" to "array",
    "array" to "array",
    "color" to "color",
    "drawable" to "drawable",
    "dimen" to "dimen",
    "fraction" to "fraction",
    "integer" to "integer",
    "bool" to "bool",
    "style" to "style",
    "attr" to "attr",
    "declare-styleable" to "styleable",
    "id" to "id"
  )

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "ResourceCycle",
      briefDescription = "Cycle in resource definitions",
      explanation = "There should be no cycles in resource definitions as this can lead to runtime exceptions.",
      category = Category.CORRECTNESS,
      priority = 10,
      severity = Severity.FATAL,
      implementation = Implementation(ResourceCycleDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
    )
  }
}