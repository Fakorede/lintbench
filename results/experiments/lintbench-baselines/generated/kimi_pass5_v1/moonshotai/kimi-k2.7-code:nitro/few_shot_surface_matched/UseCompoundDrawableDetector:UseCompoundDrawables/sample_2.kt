package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner

class UseCompoundDrawableDetector : LayoutDetector(), XmlScanner {

  override fun getApplicableElements(): Collection<String> {
    return listOf(com.android.SdkConstants.LINEAR_LAYOUT)
  }

  override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
    if (context.resourceFolderType != com.android.resources.ResourceFolderType.LAYOUT) {
      return
    }

    val children = element.childNodes
    val elements = mutableListOf<org.w3c.dom.Element>()
    for (i in 0 until children.length) {
      val child = children.item(i)
      if (child.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
        elements.add(child as org.w3c.dom.Element)
      }
    }

    if (elements.size != 2) {
      return
    }

    val first = elements[0]
    val second = elements[1]
    val image: org.w3c.dom.Element
    val text: org.w3c.dom.Element

    if (first.tagName == com.android.SdkConstants.IMAGE_VIEW &&
        second.tagName == com.android.SdkConstants.TEXT_VIEW) {
      image = first
      text = second
    } else if (first.tagName == com.android.SdkConstants.TEXT_VIEW &&
        second.tagName == com.android.SdkConstants.IMAGE_VIEW) {
      image = second
      text = first
    } else {
      return
    }

    val uri = com.android.SdkConstants.ANDROID_URI

    // An image with a content description is likely meaningful on its own.
    if (image.hasAttributeNS(uri, com.android.SdkConstants.ATTR_CONTENT_DESCRIPTION)) {
      return
    }

    // Compound drawables do not support ImageView scale types.
    if (image.hasAttributeNS(uri, com.android.SdkConstants.ATTR_SCALE_TYPE)) {
      return
    }

    // layout_weight cannot be represented by a compound drawable.
    if (image.hasAttributeNS(uri, com.android.SdkConstants.ATTR_LAYOUT_WEIGHT) ||
        text.hasAttributeNS(uri, com.android.SdkConstants.ATTR_LAYOUT_WEIGHT)) {
      return
    }

    // Different layout_gravity values cannot be merged into one TextView.
    val imageGravity = image.getAttributeNS(uri, com.android.SdkConstants.ATTR_LAYOUT_GRAVITY)
    val textGravity = text.getAttributeNS(uri, com.android.SdkConstants.ATTR_LAYOUT_GRAVITY)
    if (imageGravity != textGravity) {
      return
    }

    context.report(
      ISSUE,
      element,
      context.getElementLocation(element),
      "This tag and its children can be replaced by a single `<TextView>` with compound drawables"
    )
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "UseCompoundDrawables",
      briefDescription = "Node can be replaced by a TextView with compound drawables",
      explanation = "A `LinearLayout` which contains an `ImageView` and a `TextView` can be more " +
        "efficiently handled as a compound drawable (a single `TextView`, using the " +
        "`drawableTop`, `drawableLeft`, `drawableRight` and/or `drawableBottom` attributes to " +
        "draw one or more images adjacent to the text). If the two widgets are offset from " +
        "each other with margins, this can be replaced with a `drawablePadding` attribute.",
      category = Category.PERFORMANCE,
      priority = 3,
      severity = Severity.WARNING,
      implementation = Implementation(
        UseCompoundDrawableDetector::class.java,
        Scope.RESOURCE_FILE_SCOPE
      )
    )
  }
}