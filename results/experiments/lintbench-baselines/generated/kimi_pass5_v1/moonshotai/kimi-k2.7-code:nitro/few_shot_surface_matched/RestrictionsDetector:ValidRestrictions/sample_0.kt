package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.*

class RestrictionsDetector : ResourceXmlDetector(), XmlScanner {

  override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean =
    folderType == com.android.resources.ResourceFolderType.XML

  override fun visitDocument(context: XmlContext, document: Document) {
    val root = document.documentElement ?: return
    if (root.tagName != TAG_RESTRICTIONS) {
      return
    }
    checkRestrictions(context, root, HashSet())
  }

  private fun checkRestrictions(context: XmlContext, element: Element, keys: MutableSet<String>) {
    var child = element.firstChild
    while (child != null) {
      if (child is Element) {
        if (child.tagName != TAG_RESTRICTION) {
          context.report(
            ISSUE,
            child,
            context.getElementLocation(child),
            "Unexpected element <${child.tagName}> inside <${element.tagName}>; expected <$TAG_RESTRICTION>"
          )
        } else {
          checkRestriction(context, child, keys)
        }
      }
      child = child.nextSibling
    }
  }

  private fun checkRestriction(context: XmlContext, element: Element, keys: MutableSet<String>) {
    val keyAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_KEY)
    if (keyAttr == null) {
      context.report(
        ISSUE,
        element,
        context.getElementLocation(element),
        "Missing required android:$ATTR_KEY attribute"
      )
    } else {
      val key = keyAttr.value
      if (key.isEmpty()) {
        context.report(
          ISSUE,
          keyAttr,
          context.getValueLocation(keyAttr),
          "android:$ATTR_KEY must not be empty"
        )
      } else if (!keys.add(key)) {
        context.report(
          ISSUE,
          keyAttr,
          context.getNameLocation(keyAttr),
          "Duplicate key '$key'"
        )
      }
    }

    val titleAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_TITLE)
    if (titleAttr == null || titleAttr.value.isEmpty()) {
      context.report(
        ISSUE,
        element,
        context.getElementLocation(element),
        "Missing required android:$ATTR_TITLE attribute"
      )
    }

    val typeAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_RESTRICTION_TYPE)
    if (typeAttr == null) {
      context.report(
        ISSUE,
        element,
        context.getElementLocation(element),
        "Missing required android:$ATTR_RESTRICTION_TYPE attribute"
      )
    } else {
      val type = typeAttr.value
      if (type !in VALID_TYPES) {
        context.report(
          ISSUE,
          typeAttr,
          context.getValueLocation(typeAttr),
          "Unknown restriction type '$type'"
        )
      } else {
        when (type) {
          TYPE_BUNDLE, TYPE_BUNDLE_ARRAY -> checkRestrictions(context, element, keys)
          TYPE_CHOICE, TYPE_MULTI_SELECT -> checkChoice(context, element)
          TYPE_BOOL -> checkBool(context, element)
          TYPE_INTEGER -> checkInteger(context, element)
          TYPE_HIDDEN -> checkHidden(context, element)
        }
      }
    }
  }

  private fun checkChoice(context: XmlContext, element: Element) {
    if (element.getAttributeNS(ANDROID_URI, ATTR_ENTRIES).isEmpty()) {
      context.report(
        ISSUE,
        element,
        context.getElementLocation(element),
        "android:$ATTR_ENTRIES is required for $TYPE_CHOICE/$TYPE_MULTI_SELECT restrictions"
      )
    }
    if (element.getAttributeNS(ANDROID_URI, ATTR_ENTRY_VALUES).isEmpty()) {
      context.report(
        ISSUE,
        element,
        context.getElementLocation(element),
        "android:$ATTR_ENTRY_VALUES is required for $TYPE_CHOICE/$TYPE_MULTI_SELECT restrictions"
      )
    }
  }

  private fun checkBool(context: XmlContext, element: Element) {
    val defaultValue = element.getAttributeNS(ANDROID_URI, ATTR_DEFAULT_VALUE)
    if (defaultValue.isNotEmpty() &&
      !defaultValue.equals("true", ignoreCase = true) &&
      !defaultValue.equals("false", ignoreCase = true)
    ) {
      val attr = element.getAttributeNodeNS(ANDROID_URI, ATTR_DEFAULT_VALUE)
      context.report(
        ISSUE,
        attr ?: element,
        attr?.let { context.getValueLocation(it) } ?: context.getElementLocation(element),
        "android:$ATTR_DEFAULT_VALUE for a bool restriction must be 'true' or 'false'"
      )
    }
  }

  private fun checkInteger(context: XmlContext, element: Element) {
    val defaultValue = element.getAttributeNS(ANDROID_URI, ATTR_DEFAULT_VALUE)
    if (defaultValue.isNotEmpty() && defaultValue.toIntOrNull() == null) {
      val attr = element.getAttributeNodeNS(ANDROID_URI, ATTR_DEFAULT_VALUE)
      context.report(
        ISSUE,
        attr ?: element,
        attr?.let { context.getValueLocation(it) } ?: context.getElementLocation(element),
        "android:$ATTR_DEFAULT_VALUE for an integer restriction must be a valid integer"
      )
    }
  }

  private fun checkHidden(context: XmlContext, element: Element) {
    if (element.getAttributeNS(ANDROID_URI, ATTR_DEFAULT_VALUE).isEmpty()) {
      context.report(
        ISSUE,
        element,
        context.getElementLocation(element),
        "android:$ATTR_DEFAULT_VALUE is required for a hidden restriction"
      )
    }
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "ValidRestrictions",
      briefDescription = "Invalid Restrictions Descriptor",
      explanation = "Ensures that an application's restrictions XML file is properly formed. " +
        "See https://developer.android.com/reference/android/content/RestrictionsManager.html",
      category = Category.CORRECTNESS,
      priority = 6,
      severity = Severity.FATAL,
      implementation = Implementation(RestrictionsDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
    )

    private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
    private const val TAG_RESTRICTIONS = "restrictions"
    private const val TAG_RESTRICTION = "restriction"
    private const val ATTR_KEY = "key"
    private const val ATTR_TITLE = "title"
    private const val ATTR_RESTRICTION_TYPE = "restrictionType"
    private const val ATTR_DEFAULT_VALUE = "defaultValue"
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