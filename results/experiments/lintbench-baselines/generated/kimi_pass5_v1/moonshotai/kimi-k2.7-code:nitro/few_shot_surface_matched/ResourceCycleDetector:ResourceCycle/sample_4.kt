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
import com.android.tools.lint.detector.api.XmlScannerConstants
import org.w3c.dom.Attr
import org.w3c.dom.Element

class ResourceCycleDetector : ResourceXmlDetector(), XmlScanner {

  private val graph = mutableMapOf<String, MutableList<String>>()
  private val visiting = mutableSetOf<String>()
  private val visited = mutableSetOf<String>()
  private val reported = mutableSetOf<String>()

  override fun beforeCheckRootProject(context: Context) {
    graph.clear()
    visiting.clear()
    visited.clear()
    reported.clear()
  }

  override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean {
    return folderType != com.android.resources.ResourceFolderType.RAW &&
        folderType != com.android.resources.ResourceFolderType.XML
  }

  override fun getApplicableElements(): Collection<String> = XmlScannerConstants.ALL

  override fun getApplicableAttributes(): Collection<String> = XmlScannerConstants.ALL_ATTRIBUTES

  override fun visitElement(context: XmlContext, element: Element) {
    val resource = getCurrentResource(context, element) ?: return
    val value = element.textContent
    if (value.isNotBlank()) {
      findReferences(value).forEach { addEdge(resource, it) }
    }
  }

  override fun visitAttribute(context: XmlContext, attribute: Attr) {
    val resource = getCurrentResource(context, attribute.ownerElement) ?: return
    val value = attribute.value ?: return
    if (value.isNotBlank()) {
      findReferences(value).forEach { addEdge(resource, it) }
    }
  }

  override fun afterCheckRootProject(context: Context) {
    for (node in graph.keys) {
      if (node !in visited) {
        dfs(node, mutableListOf(), context)
      }
    }
  }

  private fun getCurrentResource(context: XmlContext, element: Element): String? {
    val folderType = context.resourceFolderType ?: return null
    return if (folderType == com.android.resources.ResourceFolderType.VALUES) {
      var current: Element? = element
      while (current != null) {
        val tag = current.localName ?: break
        if (tag == "resources") {
          return null
        }
        val name = current.getAttribute("name")
        if (name.isNotEmpty()) {
          if (tag == "item") {
            current = current.parentNode as? Element
            continue
          }
          return key(tag, name)
        }
        current = current.parentNode as? Element
      }
      null
    } else {
      val fileName = context.file.name
      val name = fileName.substringBeforeLast('.', fileName)
      key(folderType.getName(), name)
    }
  }

  private fun key(type: String, name: String): String = "$type/$name"

  private fun addEdge(from: String, to: String) {
    graph.getOrPut(from) { mutableListOf() }.add(to)
  }

  private fun findReferences(text: String): List<String> =
    REFERENCE_REGEX.findAll(text)
      .mapNotNull { match ->
        val type = match.groupValues[1]
        val name = match.groupValues[2]
        if (type.isNotEmpty() && name.isNotEmpty()) key(type, name) else null
      }
      .toList()

  private fun dfs(node: String, stack: MutableList<String>, context: Context) {
    if (node in visiting) {
      val cycle = stack.dropWhile { it != node } + node
      if (cycle.any { it !in reported }) {
        val message = "Cycle in resource definitions: " + cycle.joinToString(" → ")
        context.report(ISSUE, Location.create(context.project.dir), message)
        reported.addAll(cycle)
      }
      return
    }
    if (node in visited) return

    visiting.add(node)
    stack.add(node)
    for (next in graph[node].orEmpty()) {
      dfs(next, stack, context)
    }
    stack.removeAt(stack.size - 1)
    visiting.remove(node)
    visited.add(node)
  }

  companion object {
    private val REFERENCE_REGEX =
      Regex("[@?](?:\\*?[A-Za-z0-9_.]+:)?(?:\\+)?([A-Za-z0-9_]+)/([A-Za-z0-9_.]+)")

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