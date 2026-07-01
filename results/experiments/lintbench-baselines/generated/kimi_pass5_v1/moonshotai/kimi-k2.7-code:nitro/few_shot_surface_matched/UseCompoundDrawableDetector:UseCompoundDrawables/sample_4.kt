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
    var imageView: org.w3c.dom.Element? = null
    var textView: org.w3c.dom.Element? = null
    var childElementCount = 0

    var child = element.firstChild
    while (child != null) {
      if (child.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
        childElementCount++
        val childElement = child as org.w3c.dom.Element
        when (childElement.tagName) {
          com.android.SdkConstants.IMAGE_VIEW -> imageView = childElement
          com.android.SdkConstants.TEXT_VIEW -> textView = childElement
          else -> return
        }
      }
      child = child.nextSibling
    }

    if (childElementCount != 2 || imageView == null || textView == null) {
      return
    }

    if (!imageView.hasAttributeNS(
        com.android.SdkConstants.ANDROID_URI,
        com.android.SdkConstants.ATTR_SRC)) {
      return
    }

    context.report(
      ISSUE,
      element,
      context.getElementLocation(element),
      "This layout and its two children can be replaced by a single `TextView` with compound drawables"
    )
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "UseCompoundDrawables",
      briefDescription = "Node can be replaced by a `TextView` with compound drawables",
      explanation = "A `LinearLayout` which contains an `ImageView` and a `TextView` can be more " +
        "efficiently handled as a compound drawable (a single TextView, using the `drawableTop`, " +
        "`drawableLeft`, `drawableRight` and/or `drawableBottom` attributes to draw one or more " +
        "images adjacent to the text). If the two widgets are offset from each other with margins, " +
        "this can be replaced with a `drawablePadding` attribute.",
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