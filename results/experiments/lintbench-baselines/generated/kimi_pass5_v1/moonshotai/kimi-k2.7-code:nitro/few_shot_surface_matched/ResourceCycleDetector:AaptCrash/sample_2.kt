package com.android.tools.lint.checks

import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_TYPE
import com.android.SdkConstants.TAG_ITEM
import com.android.SdkConstants.TAG_STYLE
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Attr
import org.w3c.dom.Element
import org.w3c.dom.Node

class ResourceCycleDetector : ResourceXmlDetector(), XmlScanner {

  private val declaredIds = mutableSetOf<String>()
  private val suspects = mutableListOf<Suspect>()

  private data class Suspect(
    val context: XmlContext,
    val attribute: Attr,
    val idName: String
  )

  override fun beforeCheckRootProject(context: Context) {
    super.beforeCheckRootProject(context)
    declaredIds.clear()
    suspects.clear()
  }

  override fun appliesTo(folderType: ResourceFolderType): Boolean {
    return folderType == ResourceFolderType.VALUES
  }

  override fun getApplicableElements(): Collection<String> {
    return listOf(TAG_ITEM, TAG_STYLE)
  }

  override fun getApplicableAttributes(): Collection<String> {
    return listOf(ATTR_NAME)
  }

  override fun visitElement(context: XmlContext, element: Element) {
    if (element.tagName != TAG_ITEM) {
      return
    }

    val type = element.getAttribute(ATTR_TYPE)
    if (type == "id") {
      val name = element.getAttribute(ATTR_NAME)
      if (name.isNotEmpty()) {
        declaredIds.add(name)
      }
    }
  }

  override fun visitAttribute(context: XmlContext, attribute: Attr) {
    if (attribute.name != ATTR_NAME) {
      return
    }

    val nameValue = attribute.value ?: return
    if (nameValue != "android:id" && nameValue != "id") {
      return
    }

    val item = attribute.ownerElement ?: return
    if (item.tagName != TAG_ITEM) {
      return
    }

    val parent = item.parentNode ?: return
    if (parent.nodeType != Node.ELEMENT_NODE || parent.nodeName != TAG_STYLE) {
      return
    }

    val idValue = item.textContent?.trim() ?: return
    if (!idValue.startsWith("@+id/")) {
      return
    }

    val idName = idValue.substring("@+id/".length)
    if (idName.isEmpty()) {
      return
    }

    suspects.add(Suspect(context, attribute, idName))
  }

  override fun afterCheckRootProject(context: Context) {
    super.afterCheckRootProject(context)

    for (suspect in suspects) {
      if (!declaredIds.contains(suspect.idName)) {
        suspect.context.report(
          ISSUE,
          suspect.attribute,
          suspect.context.getValueLocation(suspect.attribute),
          "Defining a style which sets `android:id` to a dynamically generated id can " +
            "cause many versions of `aapt` to crash. Declare the id explicitly with " +
            "`<item type=\"id\" name=\"${suspect.idName}\" />` instead."
        )
      }
    }
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "AaptCrash",
      briefDescription = "Potential AAPT crash",
      explanation = "Defining a style which sets `android:id` to a dynamically generated id can " +
        "cause many versions of `aapt`, the resource packaging tool, to crash. To work around " +
        "this, declare the id explicitly with `<item type=\"id\" name=\"...\" />` instead.",
      category = Category.CORRECTNESS,
      priority = 9,
      severity = Severity.FATAL,
      implementation = Implementation(ResourceCycleDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
    )
  }
}