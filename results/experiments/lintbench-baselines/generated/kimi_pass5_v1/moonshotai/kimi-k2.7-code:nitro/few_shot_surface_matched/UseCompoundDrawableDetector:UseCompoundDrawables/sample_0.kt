package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*

class UseCompoundDrawableDetector : LayoutDetector(), XmlScanner {

  override fun getApplicableElements(): Collection<String> {
    return listOf("LinearLayout")
  }

  override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
    val children = (0 until element.childNodes.length)
      .map { element.childNodes.item(it) }
      .filterIsInstance<org.w3c.dom.Element>()

    if (children.size != 2) {
      return
    }

    val first = children[0].tagName
    val second = children[1].tagName
    if ((first == "ImageView" && second == "TextView") ||
        (first == "TextView" && second == "ImageView")) {
      context.report(
        ISSUE,
        element,
        context.getElementLocation(element),
        "This tag and its children can be replaced by a single `TextView` with compound drawables"
      )
    }
  }

  companion object {
    @JvmField
    val ISSUE: Issue = Issue.create(
      id = "UseCompoundDrawables",
      briefDescription = "Use compound drawables",
      explanation = "A `LinearLayout` which contains an `ImageView` and a `TextView` can be more " +
        "efficiently handled as a compound drawable (a single `TextView`, using the `drawableTop`, " +
        "`drawableLeft`, `drawableRight` and/or `drawableBottom` attributes to draw one or more " +
        "images adjacent to the text).\n\n" +
        "If the two widgets are offset from each other with margins, this can be replaced with a " +
        "`drawablePadding` attribute.",
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