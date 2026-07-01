package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner

class RestrictionsDetector : ResourceXmlDetector(), XmlScanner {

  override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean {
    return folderType == com.android.resources.ResourceFolderType.XML
  }

  override fun visitDocument(context: XmlContext, document: org.w3c.dom.Document) {
    val root = document.documentElement ?: return
    if (root.tagName != TAG_RESTRICTIONS) {
      context.report(
        ISSUE,
        root,
        context.getElementLocation(root),
        "Restrictions XML files must have a `<restrictions>` root element"
      )
      return
    }

    var child = root.firstChild
    while (child != null) {
      if (child.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
        val element = child as org.w3c.dom.Element
        if (element.tagName != TAG_RESTRICTION) {
          context.report(
            ISSUE,
            element,
            context.getElementLocation(element),
            "Only `<restriction>` elements are allowed inside `<restrictions>`"
          )
        } else {
          validateRestriction(context, element)
        }
      }
      child = child.nextSibling
    }
  }

  private fun validateRestriction(context: XmlContext, element: org.w3c.dom.Element) {
    val key = element.getAttributeNS(ANDROID_NS, ATTR_KEY)
    if (key.isBlank()) {
      context.report(
        ISSUE,
        element,
        context.getElementLocation(element),
        "A `<restriction>` element must specify an `android:key` attribute"
      )
    }

    val title = element.getAttributeNS(ANDROID_NS, ATTR_TITLE)
    if (title.isBlank()) {
      context.report(
        ISSUE,
        element,
        context.getElementLocation(element),
        "A `<restriction>` element must specify an `android:title` attribute"
      )
    }

    val type = element.getAttributeNS(ANDROID_NS, ATTR_RESTRICTION_TYPE)
    if (type.isBlank()) {
      context.report(
        ISSUE,
        element,
        context.getElementLocation(element),
        "A `<restriction>` element must specify an `android:restrictionType` attribute"
      )
      return
    }

    if (type !in VALID_TYPES) {
      context.report(
        ISSUE,
        element,
        context.getElementLocation(element),
        "`android:restrictionType` must be one of: ${VALID_TYPES.joinToString(", ")}"
      )
      return
    }

    when (type) {
      "choice" -> validateChoice(context, element)
      "bundle" -> validateBundle(context, element)
      "bundle_array" -> validateBundleArray(context, element)
      "hidden" -> validateHidden(context, element)
    }
  }

  private fun validateChoice(context: XmlContext, element: org.w3c.dom.Element) {
    var hasEntry = false
    var child = element.firstChild
    while (child != null) {
      if (child.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
        val childEl = child as org.w3c.dom.Element
        if (childEl.tagName == TAG_ENTRY) {
          hasEntry = true
          val entryKey = childEl.getAttributeNS(ANDROID_NS, ATTR_KEY)
          val entryValue = childEl.getAttributeNS(ANDROID_NS, ATTR_VALUE)
          if (entryKey.isBlank()) {
            context.report(
              ISSUE,
              childEl,
              context.getElementLocation(childEl),
              "Each `<entry>` must specify an `android:key` attribute"
            )
          }
          if (entryValue.isBlank()) {
            context.report(
              ISSUE,
              childEl,
              context.getElementLocation(childEl),
              "Each `<entry>` must specify an `android:value` attribute"
            )
          }
        } else {
          context.report(
            ISSUE,
            childEl,
            context.getElementLocation(childEl),
            "A `choice` restriction can only contain `<entry>` elements"
          )
        }
      }
      child = child.nextSibling
    }

    if (!hasEntry) {
      context.report(
        ISSUE,
        element,
        context.getElementLocation(element),
        "A `choice` restriction must contain at least one `<entry>` element"
      )
    }
  }

  private fun validateBundle(context: XmlContext, element: org.w3c.dom.Element) {
    var hasRestriction = false
    var child = element.firstChild
    while (child != null) {
      if (child.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
        val childEl = child as org.w3c.dom.Element
        if (childEl.tagName == TAG_RESTRICTION) {
          hasRestriction = true
          validateRestriction(context, childEl)
        } else {
          context.report(
            ISSUE,
            childEl,
            context.getElementLocation(childEl),
            "A `bundle` restriction can only contain nested `<restriction>` elements"
          )
        }
      }
      child = child.nextSibling
    }

    if (!hasRestriction) {
      context.report(
        ISSUE,
        element,
        context.getElementLocation(element),
        "A `bundle` restriction must contain at least one nested `<restriction>`"
      )
    }
  }

  private fun validateBundleArray(context: XmlContext, element: org.w3c.dom.Element) {
    var hasBundle = false
    var child = element.firstChild
    while (child != null) {
      if (child.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
        val childEl = child as org.w3c.dom.Element
        if (childEl.tagName == TAG_RESTRICTION) {
          hasBundle = true
          if (childEl.getAttributeNS(ANDROID_NS, ATTR_RESTRICTION_TYPE) != "bundle") {
            context.report(
              ISSUE,
              childEl,
              context.getElementLocation(childEl),
              "A `bundle_array` restriction can only contain `bundle` restrictions"
            )
          } else {
            validateRestriction(context, childEl)
          }
        } else {
          context.report(
            ISSUE,
            childEl,
            context.getElementLocation(childEl),
            "A `bundle_array` restriction can only contain `<restriction>` elements"
          )
        }
      }
      child = child.nextSibling
    }

    if (!hasBundle) {
      context.report(
        ISSUE,
        element,
        context.getElementLocation(element),
        "A `bundle_array` restriction must contain at least one `bundle` restriction"
      )
    }
  }

  private fun validateHidden(context: XmlContext, element: org.w3c.dom.Element) {
    val defaultValue = element.getAttributeNS(ANDROID_NS, ATTR_DEFAULT_VALUE)
    if (defaultValue.isBlank()) {
      context.report(
        ISSUE,
        element,
        context.getElementLocation(element),
        "A `hidden` restriction must specify an `android:defaultValue` attribute"
      )
    }
  }

  companion object {
    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    private const val TAG_RESTRICTIONS = "restrictions"
    private const val TAG_RESTRICTION = "restriction"
    private const val TAG_ENTRY = "entry"
    private const val ATTR_KEY = "key"
    private const val ATTR_TITLE = "title"
    private const val ATTR_RESTRICTION_TYPE = "restrictionType"
    private const val ATTR_VALUE = "value"
    private const val ATTR_DEFAULT_VALUE = "defaultValue"

    private val VALID_TYPES = listOf(
      "hidden",
      "bool",
      "string",
      "integer",
      "choice",
      "bundle",
      "bundle_array"
    )

    @JvmField
    val ISSUE = Issue.create(
      id = "ValidRestrictions",
      briefDescription = "Invalid Restrictions Descriptor",
      explanation = "Ensures that an application's restrictions XML file is properly formed. " +
        "Each `<restriction>` must define `android:key`, `android:title`, and a valid " +
        "`android:restrictionType`, and child elements must match the chosen type.",
      category = Category.CORRECTNESS,
      priority = 9,
      severity = Severity.FATAL,
      implementation = Implementation(
        RestrictionsDetector::class.java,
        Scope.RESOURCE_FILE_SCOPE
      )
    )
  }
}