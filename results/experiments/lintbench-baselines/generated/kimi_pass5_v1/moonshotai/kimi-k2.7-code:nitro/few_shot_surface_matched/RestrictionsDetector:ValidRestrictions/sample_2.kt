package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
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
    val baseName = context.file.name.substringBeforeLast(".", "")
    if (!baseName.equals(TAG_RESTRICTIONS, ignoreCase = true) &&
      !baseName.startsWith("${TAG_RESTRICTIONS}_", ignoreCase = true)
    ) {
      return
    }

    val root = document.documentElement ?: return
    if (root.tagName != TAG_RESTRICTIONS) {
      context.report(
        ISSUE,
        root,
        context.getElementLocation(root),
        "Restrictions XML files must have a <restrictions> root element"
      )
      return
    }

    checkRestrictionChildren(context, root)
  }

  private fun checkRestrictionChildren(
    context: XmlContext,
    parent: org.w3c.dom.Element
  ) {
    var child = parent.firstChild
    while (child != null) {
      if (child is org.w3c.dom.Element && child.tagName == TAG_RESTRICTION) {
        checkRestriction(context, child)
      }
      child = child.nextSibling
    }
  }

  private fun checkRestriction(
    context: XmlContext,
    element: org.w3c.dom.Element
  ) {
    val key = element.getAttributeNS(ANDROID_URI, ATTR_KEY)
    if (key.isBlank()) {
      context.report(
        ISSUE,
        element,
        context.getElementLocation(element),
        "A restriction must specify an android:key attribute"
      )
    }

    val title = element.getAttributeNS(ANDROID_URI, ATTR_TITLE)
    if (title.isBlank()) {
      context.report(
        ISSUE,
        element,
        context.getElementLocation(element),
        "A restriction must specify an android:title attribute"
      )
    }

    val type = element.getAttributeNS(ANDROID_URI, ATTR_RESTRICTION_TYPE)
    if (type.isBlank()) {
      context.report(
        ISSUE,
        element,
        context.getElementLocation(element),
        "A restriction must specify an android:restrictionType attribute"
      )
      return
    }

    if (type !in VALID_TYPES) {
      context.report(
        ISSUE,
        element,
        context.getElementLocation(element),
        "Unknown restriction type \"$type\"; expected one of ${VALID_TYPES.joinToString()}"
      )
      return
    }

    when (type) {
      TYPE_CHOICE, TYPE_MULTI_SELECT -> {
        val entries = collectChildrenByTagName(element, TAG_ENTRY)
        val entryValues = collectChildrenByTagName(element, TAG_ENTRY_VALUE)
        if (entries.isEmpty() || entryValues.isEmpty()) {
          context.report(
            ISSUE,
            element,
            context.getElementLocation(element),
            "Restrictions of type \"$type\" must contain <entry> and <entryValue> elements"
          )
        } else if (entries.size != entryValues.size) {
          context.report(
            ISSUE,
            element,
            context.getElementLocation(element),
            "There must be an equal number of <entry> and <entryValue> elements"
          )
        }
      }
      TYPE_BUNDLE, TYPE_BUNDLE_ARRAY -> {
        checkRestrictionChildren(context, element)
      }
    }
  }

  private fun collectChildrenByTagName(
    parent: org.w3c.dom.Element,
    tagName: String
  ): List<org.w3c.dom.Element> {
    val result = mutableListOf<org.w3c.dom.Element>()
    var child = parent.firstChild
    while (child != null) {
      if (child is org.w3c.dom.Element && child.tagName == tagName) {
        result.add(child)
      }
      child = child.nextSibling
    }
    return result
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "ValidRestrictions",
      briefDescription = "Invalid Restrictions Descriptor",
      explanation = "A restrictions descriptor XML resource must be properly formed. " +
        "It must have a <restrictions> root element, and each <restriction> child " +
        "must have android:key, android:title, and android:restrictionType attributes. " +
        "Choice and multi-select restrictions must contain matching <entry> and <entryValue> elements.",
      category = Category.CORRECTNESS,
      priority = 8,
      severity = Severity.FATAL,
      implementation = Implementation(RestrictionsDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
    )

    private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"

    private const val TAG_RESTRICTIONS = "restrictions"
    private const val TAG_RESTRICTION = "restriction"
    private const val TAG_ENTRY = "entry"
    private const val TAG_ENTRY_VALUE = "entryValue"

    private const val ATTR_KEY = "key"
    private const val ATTR_TITLE = "title"
    private const val ATTR_RESTRICTION_TYPE = "restrictionType"

    private const val TYPE_BOOL = "bool"
    private const val TYPE_STRING = "string"
    private const val TYPE_INTEGER = "integer"
    private const val TYPE_CHOICE = "choice"
    private const val TYPE_MULTI_SELECT = "multi_select"
    private const val TYPE_HIDDEN = "hidden"
    private const val TYPE_BUNDLE = "bundle"
    private const val TYPE_BUNDLE_ARRAY = "bundle_array"

    private val VALID_TYPES = setOf(
      TYPE_BOOL,
      TYPE_STRING,
      TYPE_INTEGER,
      TYPE_CHOICE,
      TYPE_MULTI_SELECT,
      TYPE_HIDDEN,
      TYPE_BUNDLE,
      TYPE_BUNDLE_ARRAY
    )
  }
}