package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

class RestrictionsDetector : ResourceXmlDetector() {

  override fun appliesTo(folderType: ResourceFolderType): Boolean {
    return folderType == ResourceFolderType.XML
  }

  override fun visitDocument(context: XmlContext, document: Document) {
    val root = document.documentElement ?: return
    if (root.tagName != TAG_RESTRICTIONS) {
      context.report(
        ISSUE,
        root,
        context.getLocation(root),
        "Restrictions XML files must have `<restrictions>` as the root element"
      )
      return
    }

    var child = root.firstChild
    while (child != null) {
      if (child.nodeType == Node.ELEMENT_NODE) {
        val element = child as Element
        if (element.tagName != TAG_RESTRICTION) {
          context.report(
            ISSUE,
            element,
            context.getLocation(element),
            "Unexpected tag `<${element.tagName}>` under `<restrictions>`; expected `<restriction>`"
          )
        } else {
          checkRestriction(context, element)
        }
      }
      child = child.nextSibling
    }
  }

  private fun checkRestriction(context: XmlContext, element: Element) {
    if (!element.hasAttributeNS(ANDROID_URI, ATTR_KEY)) {
      reportMissing(context, element, ATTR_KEY)
    }
    if (!element.hasAttributeNS(ANDROID_URI, ATTR_TITLE)) {
      reportMissing(context, element, ATTR_TITLE)
    }

    val typeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_RESTRICTION_TYPE)
    if (typeAttr == null) {
      reportMissing(context, element, ATTR_RESTRICTION_TYPE)
    } else {
      val type = typeAttr.value
      if (type !in VALID_TYPES) {
        context.report(
          ISSUE,
          typeAttr,
          context.getValueLocation(typeAttr),
          "Invalid restriction type `$type`"
        )
      } else {
        when (type) {
          TYPE_CHOICE, TYPE_MULTI_SELECT -> {
            if (!element.hasAttributeNS(ANDROID_URI, ATTR_ENTRIES)) {
              reportMissing(context, element, ATTR_ENTRIES)
            }
            if (!element.hasAttributeNS(ANDROID_URI, ATTR_ENTRY_VALUES)) {
              reportMissing(context, element, ATTR_ENTRY_VALUES)
            }
          }
          TYPE_BUNDLE, TYPE_BUNDLE_ARRAY -> {
            var child = element.firstChild
            while (child != null) {
              if (child.nodeType == Node.ELEMENT_NODE) {
                val childElement = child as Element
                if (childElement.tagName == TAG_RESTRICTION) {
                  checkRestriction(context, childElement)
                } else {
                  context.report(
                    ISSUE,
                    childElement,
                    context.getLocation(childElement),
                    "Unexpected tag `<${childElement.tagName}>` inside a bundle restriction; expected `<restriction>`"
                  )
                }
              }
              child = child.nextSibling
            }
          }
        }
      }
    }
  }

  private fun reportMissing(context: XmlContext, element: Element, attributeName: String) {
    context.report(
      ISSUE,
      element,
      context.getLocation(element),
      "Missing required attribute `android:$attributeName`"
    )
  }

  companion object {
    @JvmField
    val ISSUE: Issue = Issue.create(
      id = "ValidRestrictions",
      briefDescription = "Invalid Restrictions Descriptor",
      explanation = "Ensures that an application's restrictions XML file is properly formed.",
      category = Category.CORRECTNESS,
      priority = 5,
      severity = Severity.FATAL,
      implementation = Implementation(RestrictionsDetector::class.java, Scope.RESOURCE_XML_SCOPE)
    )

    private const val TAG_RESTRICTIONS = "restrictions"
    private const val TAG_RESTRICTION = "restriction"

    private const val ATTR_KEY = "key"
    private const val ATTR_TITLE = "title"
    private const val ATTR_RESTRICTION_TYPE = "restrictionType"
    private const val ATTR_ENTRIES = "entries"
    private const val ATTR_ENTRY_VALUES = "entryValues"

    private const val TYPE_BOOL = "bool"
    private const val TYPE_STRING = "string"
    private const val TYPE_INTEGER = "integer"
    private const val TYPE_CHOICE = "choice"
    private const val TYPE_MULTI_SELECT = "multi_select"
    private const val TYPE_HIDDEN = "hidden"
    private const val TYPE_BUNDLE = "bundle"
    private const val TYPE_BUNDLE_ARRAY = "bundle_array"

    private val VALID_TYPES = setOf(
      TYPE_BOOL, TYPE_STRING, TYPE_INTEGER, TYPE_CHOICE,
      TYPE_MULTI_SELECT, TYPE_HIDDEN, TYPE_BUNDLE, TYPE_BUNDLE_ARRAY
    )
  }
}