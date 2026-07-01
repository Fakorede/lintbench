package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceFolderType
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner

class ResourceCycleDetector : ResourceXmlDetector(), XmlScanner {

  private val declaredIds = mutableSetOf<String>()
  private val suspects = mutableListOf<Suspect>()

  private data class Suspect(
    val context: XmlContext,
    val element: org.w3c.dom.Element,
    val idName: String,
  )

  override fun beforeCheckRootProject(context: Context) {
    declaredIds.clear()
    suspects.clear()
  }

  override fun afterCheckRootProject(context: Context) {
    for ((ctx, element, idName) in suspects) {
      if (!declaredIds.contains(idName)) {
        val location = ctx.getValueLocation(element)
        ctx.report(
          ISSUE,
          element,
          location,
          "Defining a style which sets `android:id` to a dynamically generated id can cause aapt to crash; declare the id explicitly with `<item type=\"id\" name=\"$idName\" />` instead.",
        )
      }
    }
    declaredIds.clear()
    suspects.clear()
  }

  override fun appliesTo(folderType: ResourceFolderType): Boolean {
    return folderType == ResourceFolderType.VALUES
  }

  override fun getApplicableElements(): Collection<String>? {
    return listOf(ITEM, STYLE)
  }

  override fun getApplicableAttributes(): Collection<String>? {
    return null
  }

  override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
    when (element.tagName) {
      ITEM -> {
        if (TYPE_ID == element.getAttribute(TYPE)) {
          val name = element.getAttribute(NAME)
          if (name.isNotEmpty()) {
            declaredIds.add(name)
          }
        }
      }
      STYLE -> {
        val children = element.childNodes
        for (i in 0 until children.length) {
          val child = children.item(i) as? org.w3c.dom.Element ?: continue
          if (child.tagName != ITEM) continue
          if (child.getAttribute(NAME) != ANDROID_ID) continue
          val value = child.textContent?.trim() ?: continue
          val idName = when {
            value.startsWith(AT_PLUS_ID) -> value.substring(AT_PLUS_ID.length)
            value.startsWith(AT_ID) -> value.substring(AT_ID.length)
            else -> null
          } ?: continue
          if (idName.isNotEmpty()) {
            suspects.add(Suspect(context, child, idName))
          }
        }
      }
    }
  }

  override fun visitAttribute(context: XmlContext, attribute: org.w3c.dom.Attr) {
    // Not needed: the style's android:id value is supplied as the text content of an <item>.
  }

  companion object {
    private const val ITEM = "item"
    private const val STYLE = "style"
    private const val NAME = "name"
    private const val TYPE = "type"
    private const val TYPE_ID = "id"
    private const val ANDROID_ID = "android:id"
    private const val AT_ID = "@id/"
    private const val AT_PLUS_ID = "@+id/"

    @JvmField
    val ISSUE = Issue.create(
      id = "AaptCrash",
      briefDescription = "Potential AAPT crash",
      explanation = "Defining a style which sets `android:id` to a dynamically generated id can cause many versions of `aapt`, the resource packaging tool, to crash. To work around this, declare the id explicitly with `<item type=\"id\" name=\"...\" />` instead.",
      category = Category.CORRECTNESS,
      priority = 9,
      severity = Severity.FATAL,
      implementation = Implementation(ResourceCycleDetector::class.java, Scope.RESOURCE_FILE_SCOPE),
    )
  }
}