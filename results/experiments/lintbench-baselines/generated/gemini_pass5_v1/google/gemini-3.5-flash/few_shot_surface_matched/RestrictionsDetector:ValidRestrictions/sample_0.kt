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
    if (root.tagName != "restrictions") {
      return
    }
    val children = root.childNodes
    for (i in 0 until children.length) {
      val child = children.item(i)
      if (child is org.w3c.dom.Element && child.tagName == "restriction") {
        checkRestriction(context, child)
      }
    }
  }

  private fun checkRestriction(context: XmlContext, element: org.w3c.dom.Element) {
    val keyAttr = element.getAttributeNodeNS(com.android.SdkConstants.ANDROID_URI, "key")
    val isBundleInsideBundleArray = isBundleInsideBundleArray(element)
    if (keyAttr == null) {
      if (!isBundleInsideBundleArray) {
        context.report(ISSUE, element, context.getNameLocation(element), "Missing `key` attribute")
      }
    } else if (keyAttr.value.isEmpty()) {
      context.report(ISSUE, keyAttr, context.getValueLocation(keyAttr), "Empty `key` attribute")
    }

    val typeAttr = element.getAttributeNodeNS(com.android.SdkConstants.ANDROID_URI, "restrictionType")
    if (typeAttr == null) {
      context.report(ISSUE, element, context.getNameLocation(element), "Missing `restrictionType` attribute")
      return
    }
    val type = typeAttr.value
    val validTypes = setOf("bool", "string", "integer", "choice", "multi-select", "hidden", "bundle", "bundle_array")
    if (type !in validTypes) {
      context.report(ISSUE, typeAttr, context.getValueLocation(typeAttr), "Invalid restriction type `$type`")
      return
    }

    if (type != "hidden") {
      val titleAttr = element.getAttributeNodeNS(com.android.SdkConstants.ANDROID_URI, "title")
      if (titleAttr == null && !isBundleInsideBundleArray && !isInsideBundle(element)) {
        context.report(ISSUE, element, context.getNameLocation(element), "Missing `title` attribute")
      }
    }

    if (type == "choice" || type == "multi-select") {
      val entriesAttr = element.getAttributeNodeNS(com.android.SdkConstants.ANDROID_URI, "entries")
      if (entriesAttr == null) {
        context.report(ISSUE, element, context.getNameLocation(element), "Missing `entries` attribute for `$type` restriction")
      }
      val entryValuesAttr = element.getAttributeNodeNS(com.android.SdkConstants.ANDROID_URI, "entryValues")
      if (entryValuesAttr == null) {
        context.report(ISSUE, element, context.getNameLocation(element), "Missing `entryValues` attribute for `$type` restriction")
      }
    }

    if (type == "bundle" || type == "bundle_array") {
      val defaultAttr = element.getAttributeNodeNS(com.android.SdkConstants.ANDROID_URI, "defaultValue")
      if (defaultAttr != null) {
        context.report(ISSUE, defaultAttr, context.getLocation(defaultAttr), "Restriction type `$type` cannot have a default value")
      }
    }

    val childNodes = element.childNodes
    for (i in 0 until childNodes.length) {
      val child = childNodes.item(i)
      if (child is org.w3c.dom.Element && child.tagName == "restriction") {
        if (type != "bundle" && type != "bundle_array") {
          context.report(ISSUE, child, context.getNameLocation(child), "Restriction type `$type` cannot have nested restrictions")
        } else if (type == "bundle_array") {
          val childTypeAttr = child.getAttributeNodeNS(com.android.SdkConstants.ANDROID_URI, "restrictionType")
          if (childTypeAttr != null && childTypeAttr.value != "bundle") {
            context.report(ISSUE, childTypeAttr, context.getValueLocation(childTypeAttr), "Restriction arrays can only contain bundle restrictions")
          }
        }
        checkRestriction(context, child)
      }
    }
  }

  private fun isBundleInsideBundleArray(element: org.w3c.dom.Element): Boolean {
    val parent = element.parentNode as? org.w3c.dom.Element ?: return false
    if (parent.tagName == "restriction") {
      val parentType = parent.getAttributeNS(com.android.SdkConstants.ANDROID_URI, "restrictionType")
      return parentType == "bundle_array"
    }
    return false
  }

  private fun isInsideBundle(element: org.w3c.dom.Element): Boolean {
    var parentNode = element.parentNode
    while (parentNode is org.w3c.dom.Element) {
      if (parentNode.tagName == "restriction") {
        val parentType = parentNode.getAttributeNS(com.android.SdkConstants.ANDROID_URI, "restrictionType")
        if (parentType == "bundle") {
          return true
        }
      }
      parentNode = parentNode.parentNode
    }
    return false
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "ValidRestrictions",
      briefDescription = "Invalid Restrictions Descriptor",
      explanation = "Ensures that an applications restrictions XML file is properly formed",
      category = Category.CORRECTNESS,
      priority = 5,
      severity = Severity.FATAL,
      implementation = Implementation(RestrictionsDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
    )
  }
}