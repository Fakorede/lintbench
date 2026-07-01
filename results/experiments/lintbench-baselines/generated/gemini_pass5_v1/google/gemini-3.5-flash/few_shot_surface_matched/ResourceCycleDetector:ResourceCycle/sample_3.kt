package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext

class ResourceCycleDetector : ResourceXmlDetector() {

  private val declarations = mutableMapOf<String, com.android.tools.lint.detector.api.Location>()
  private val dependencies = mutableMapOf<String, MutableSet<String>>()

  override fun beforeCheckRootProject(context: Context) {
    declarations.clear()
    dependencies.clear()
  }

  override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean {
    return true
  }

  override fun getApplicableElements(): Collection<String>? {
    return listOf("*")
  }

  override fun getApplicableAttributes(): Collection<String>? {
    return listOf("*")
  }

  private fun extractResourceUrls(value: String): List<String> {
    val result = mutableListOf<String>()
    val regex = """[@?](?!android:)([a-zA-Z0-9_]+)/([a-zA-Z0-9_.]+)""".toRegex()
    regex.findAll(value).forEach { match ->
      val type = match.groupValues[1]
      val name = match.groupValues[2]
      result.add("@$type/$name")
    }
    return result
  }

  private fun getDefinedResource(context: XmlContext, element: org.w3c.dom.Element): String? {
    val folderType = context.resourceFolderType ?: return null
    if (folderType == com.android.resources.ResourceFolderType.VALUES) {
      val tagName = element.tagName
      if (tagName == "style" || tagName == "color" || tagName == "dimen" || tagName == "string" || tagName == "integer" || tagName == "bool") {
        val name = element.getAttribute("name")
        if (name.isNotEmpty()) {
          return "@$tagName/$name"
        }
      } else if (tagName == "item") {
        val parent = element.parentNode as? org.w3c.dom.Element
        if (parent != null && parent.tagName == "style") {
          val parentName = parent.getAttribute("name")
          if (parentName.isNotEmpty()) {
            return "@style/$parentName"
          }
        }
      }
    } else {
      val type = folderType.name.lowercase()
      val name = context.file.name.substringBefore('.')
      return "@$type/$name"
    }
    return null
  }

  override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
    val definedRes = getDefinedResource(context, element) ?: return

    if (!declarations.containsKey(definedRes)) {
      declarations[definedRes] = context.getLocation(element)
    }

    val tagName = element.tagName
    if (context.resourceFolderType == com.android.resources.ResourceFolderType.VALUES) {
      if (tagName == "color" || tagName == "dimen" || tagName == "string" || tagName == "integer" || tagName == "bool" || tagName == "item") {
        val text = element.textContent
        if (text != null) {
          val refs = extractResourceUrls(text)
          for (ref in refs) {
            dependencies.getOrPut(definedRes) { mutableSetOf() }.add(ref)
          }
        }
      } else if (tagName == "style") {
        val name = element.getAttribute("name")
        if (name.contains('.')) {
          val hasExplicitParent = element.hasAttribute("parent")
          if (!hasExplicitParent) {
            val parentName = name.substringBeforeLast('.')
            if (parentName.isNotEmpty()) {
              dependencies.getOrPut(definedRes) { mutableSetOf() }.add("@style/$parentName")
            }
          }
        }
      }
    }
  }

  override fun visitAttribute(context: XmlContext, attribute: org.w3c.dom.Attr) {
    val element = attribute.ownerElement ?: return
    val definedRes = getDefinedResource(context, element) ?: return

    val value = attribute.value ?: return

    if (attribute.name == "parent" && element.tagName == "style") {
      var parentVal = value
      if (parentVal.isNotEmpty()) {
        if (!parentVal.startsWith("@") && !parentVal.startsWith("?")) {
          parentVal = "@style/$parentVal"
        }
        val refs = extractResourceUrls(parentVal)
        for (ref in refs) {
          dependencies.getOrPut(definedRes) { mutableSetOf() }.add(ref)
        }
        if (parentVal.startsWith("@style/")) {
          dependencies.getOrPut(definedRes) { mutableSetOf() }.add(parentVal)
        }
      }
    } else {
      val refs = extractResourceUrls(value)
      for (ref in refs) {
        dependencies.getOrPut(definedRes) { mutableSetOf() }.add(ref)
      }
    }
  }

  override fun afterCheckRootProject(context: Context) {
    val visited = mutableSetOf<String>()
    val stack = mutableListOf<String>()
    val stackSet = mutableSetOf<String>()

    fun dfs(node: String) {
      if (stackSet.contains(node)) {
        val cycleStartIndex = stack.indexOf(node)
        val cyclePath = stack.subList(cycleStartIndex, stack.size) + node
        val cycleString = cyclePath.joinToString(" -> ")
        val location = cyclePath.mapNotNull { declarations[it] }.firstOrNull()
        if (location != null) {
          context.report(
            ISSUE,
            location,
            "Cycle in resource definitions: $cycleString"
          )
        }
        return
      }
      if (visited.contains(node)) {
        return
      }

      visited.add(node)
      stack.add(node)
      stackSet.add(node)

      val neighbors = dependencies[node]
      if (neighbors != null) {
        for (neighbor in neighbors) {
          dfs(neighbor)
        }
      }

      stack.removeAt(stack.size - 1)
      stackSet.remove(node)
    }

    for (node in dependencies.keys) {
      if (!visited.contains(node)) {
        dfs(node)
      }
    }
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "ResourceCycle",
      briefDescription = "Cycle in resource definitions",
      explanation = "There should be no cycles in resource definitions as this can lead to runtime exceptions.",
      category = Category.CORRECTNESS,
      priority = 8,
      severity = Severity.FATAL,
      implementation = Implementation(ResourceCycleDetector::class.java, Scope.RESOURCE_FILE_SCOPE),
    )
  }
}