package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.IMAGE_VIEW
import com.android.SdkConstants.LINEAR_LAYOUT
import com.android.SdkConstants.TEXT_VIEW
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class UseCompoundDrawableDetector : LayoutDetector() {

  override fun getApplicableElements(): Collection<String> {
    return listOf(LINEAR_LAYOUT)
  }

  override fun visitElement(context: XmlContext, element: Element) {
    val children = mutableListOf<Element>()
    var child = element.firstChild
    while (child != null) {
      if (child is Element) {
        children.add(child)
      }
      child = child.nextSibling
    }

    if (children.size != 2) return

    val first = children[0]
    val second = children[1]

    val firstTag = first.tagName
    val secondTag = second.tagName

    val isFirstImage = firstTag == IMAGE_VIEW || firstTag == "ImageButton"
    val isFirstText = firstTag == TEXT_VIEW
    val isSecondImage = secondTag == IMAGE_VIEW || secondTag == "ImageButton"
    val isSecondText = secondTag == TEXT_VIEW

    val (imageView, textView) = when {
      isFirstImage && isSecondText -> Pair(first, second)
      isFirstText && isSecondImage -> Pair(second, first)
      else -> return
    }

    if (imageView.hasAttributeNS(ANDROID_URI, "layout_weight") ||
        textView.hasAttributeNS(ANDROID_URI, "layout_weight")) {
      return
    }

    if (imageView.hasAttributeNS(ANDROID_URI, "clickable") &&
        imageView.getAttributeNS(ANDROID_URI, "clickable") == "true") {
      return
    }

    if (imageView.hasAttributeNS(ANDROID_URI, "id")) {
      return
    }

    context.report(
      ISSUE,
      element,
      context.getNameLocation(element),
      "This tag and its children can be replaced by one `<TextView/>` and a compound drawable"
    )
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "UseCompoundDrawables",
      briefDescription = "Can be replaced by a `TextView` with compound drawables",
      explanation = "A `LinearLayout` which contains an `ImageView` and a `TextView` can be more " +
        "efficiently handled as a compound drawable (a single TextView, using the " +
        "`drawableTop`, `drawableLeft`, `drawableRight` and/or `drawableBottom` " +
        "attributes to draw one or more images adjacent to the text).\n\n" +
        "If the two widgets are offset from each other with margins, this can be " +
        "replaced with a `drawablePadding` attribute.",
      category = Category.PERFORMANCE,
      priority = 6,
      severity = Severity.WARNING,
      implementation = Implementation(UseCompoundDrawableDetector::class.java, Scope.LAYOUT_SCOPE),
    )
  }
}