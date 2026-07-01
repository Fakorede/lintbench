package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Document
import org.w3c.dom.Element

class RestrictionsDetector : ResourceXmlDetector(), XmlScanner {

  override fun appliesTo(folderType: ResourceFolderType): Boolean {
    return folderType == ResourceFolderType.XML
  }

  override fun visitDocument(context: XmlContext, document: Document) {
    val root = document.documentElement ?: return
    if (root.tagName != TAG_RESTRICTIONS) {
      return
    }

    val children = root.childNodes
    for (i in 0 until children.length) {
      val child = children.item(i)
      if (child is Element && child.tagName == TAG_RESTRICTION) {
        validateRestriction(context, child)
      }
    }
  }

  private fun validateRestriction(context: XmlContext, restriction: Element) {
    val key = restriction.getAttribute(ATTR_KEY)
    if (key.isBlank()) {
      reportMissing(context, restriction, ATTR_KEY)
    }

    if (!restriction.hasAttribute(ATTR_TITLE)) {
      reportMissing(context, restriction, ATTR_TITLE)
    }

    val type = restriction.getAttribute(ATTR_RESTRICTION_TYPE)
    if (type.isBlank()) {
      reportMissing(context, restriction, ATTR_RESTRICTION_TYPE)
      return
    }

    if (type !in RESTRICTION_TYPES) {
      context.report(
        ISSUE,
        restriction,
        context.getElementLocation(restriction),
        "Invalid restriction type `$type`; must be one of: " +
          RESTRICTION_TYPES.joinToString()
      )
      return
    }

    when (type) {
      TYPE_CHOICE -> validateChoice(context, restriction)
      TYPE_BUNDLE, TYPE_BUNDLE_ARRAY -> validateBundle(context, restriction, type)
      else -> ensureNoNestedElements(context, restriction)
    }
  }

  private fun validateChoice(context: XmlContext, restriction: Element) {
    val entryValues = getDirectChild(restriction, TAG_ENTRY_VALUES)
    val entries = getDirectChild(restriction, TAG_ENTRIES)

    if (entryValues == null) {
      context.report(
        ISSUE,
        restriction,
        context.getElementLocation(restriction),
        "A 'choice' restriction must contain a '<$TAG_ENTRY_VALUES>' element"
      )
    }
    if (entries == null) {
      context.report(
        ISSUE,
        restriction,
        context.getElementLocation(restriction),
        "A 'choice' restriction must contain a '<$TAG_ENTRIES>' element"
      )
    }

    if (entryValues != null && entries != null) {
      val valuesCount = countDirectValueChildren(entryValues)
      val entriesCount = countDirectValueChildren(entries)
      if (valuesCount == 0) {
        context.report(
          ISSUE,
          entryValues,
          context.getElementLocation(entryValues),
          "The '<$TAG_ENTRY_VALUES>' element must contain at least one '<$TAG_VALUE>' child"
        )
      }
      if (entriesCount == 0) {
        context.report(
          ISSUE,
          entries,
          context.getElementLocation(entries),
          "The '<$TAG_ENTRIES>' element must contain at least one '<$TAG_VALUE>' child"
        )
      }
      if (valuesCount != entriesCount) {
        context.report(
          ISSUE,
          restriction,
          context.getElementLocation(restriction),
          "The '<$TAG_ENTRY_VALUES>' and '<$TAG_ENTRIES>' elements must contain the same number of '<$TAG_VALUE>' children"
        )
      }
    }
  }

  private fun validateBundle(context: XmlContext, restriction: Element, type: String) {
    var restrictionCount = 0
    val children = restriction.childNodes
    for (i in 0 until children.length) {
      val child = children.item(i)
      if (child !is Element) {
        continue
      }
      if (child.tagName == TAG_RESTRICTION) {
        restrictionCount++
        validateRestriction(context, child)
      } else {
        context.report(
          ISSUE,
          child,
          context.getElementLocation(child),
          "A '$type' restriction can only contain nested '<$TAG_RESTRICTION>' elements"
        )
      }
    }

    if (restrictionCount == 0) {
      context.report(
        ISSUE,
        restriction,
        context.getElementLocation(restriction),
        "A '$type' restriction must contain at least one nested '<$TAG_RESTRICTION>' element"
      )
    }
  }

  private fun ensureNoNestedElements(context: XmlContext, restriction: Element) {
    val children = restriction.childNodes
    for (i in 0 until children.length) {
      val child = children.item(i)
      if (child is Element) {
        context.report(
          ISSUE,
          child,
          context.getElementLocation(child),
          "A '${restriction.getAttribute(ATTR_RESTRICTION_TYPE)}' restriction should not contain nested elements"
        )
      }
    }
  }

  private fun countDirectValueChildren(element: Element): Int {
    var count = 0
    val children = element.childNodes
    for (i in 0 until children.length) {
      val child = children.item(i)
      if (child is Element && child.tagName == TAG_VALUE) {
        count++
      }
    }
    return count
  }

  private fun getDirectChild(element: Element, tagName: String): Element? {
    val children = element.childNodes
    for (i in 0 until children.length) {
      val child = children.item(i)
      if (child is Element && child.tagName == tagName) {
        return child
      }
    }
    return null
  }

  private fun reportMissing(context: XmlContext, element: Element, attribute: String) {
    context.report(
      ISSUE,
      element,
      context.getElementLocation(element),
      "Missing required attribute '$attribute' on '<${element.tagName}>'"
    )
  }

  companion object {
    private const val TAG_RESTRICTIONS = "restrictions"
    private const val TAG_RESTRICTION = "restriction"
    private const val TAG_ENTRY_VALUES = "entryValues"
    private const val TAG_ENTRIES = "entries"
    private const val TAG_VALUE = "value"

    private const val ATTR_KEY = "key"
    private const val ATTR_TITLE = "title"
    private const val ATTR_RESTRICTION_TYPE = "restrictionType"

    private const val TYPE_CHOICE = "choice"
    private const val TYPE_BUNDLE = "bundle"
    private const val TYPE_BUNDLE_ARRAY = "bundle_array"

    private val RESTRICTION_TYPES = listOf(
      "bool",
      "string",
      "integer",
      TYPE_CHOICE,
      "hidden",
      TYPE_BUNDLE,
      TYPE_BUNDLE_ARRAY
    )

    @JvmField
    val ISSUE = Issue.create(
      id = "ValidRestrictions",
      briefDescription = "Invalid Restrictions Descriptor",
      explanation = """
                Ensures that an application's restrictions XML file is properly formed.
                Restrictions XML files are used by `RestrictionsManager` to expose
                user restrictions. They must declare valid `key`, `title`, and
                `restrictionType` attributes, use valid restriction types, and
                include required child elements for `choice`, `bundle`, and
                `bundle_array` types.
            """,
      category = Category.CORRECTNESS,
      priority = 8,
      severity = Severity.FATAL,
      implementation = Implementation(
        RestrictionsDetector::class.java,
        Scope.RESOURCE_XML_SCOPE
      )
    )
  }
}