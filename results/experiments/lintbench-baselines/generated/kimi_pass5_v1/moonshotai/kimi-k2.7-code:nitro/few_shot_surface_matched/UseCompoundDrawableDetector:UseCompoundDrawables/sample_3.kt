package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*

class UseCompoundDrawableDetector : LayoutDetector(), XmlScanner {

  override fun getApplicableElements(): Collection<String> {
    return listOf("LinearLayout")
  }

  override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
    val children = mutableListOf<org.w3c.dom.Element>()
    val childNodes = element.childNodes
    for (i in 0 until childNodes.length) {
      val child = childNodes.item(i) as? org.w3c.dom.Element ?: continue
      children.add(child)
    }
    if (children.size != 2) return

    val first = children[0].tagName
    val second = children[1].tagName
    val matches = (first == "ImageView" && second == "TextView") ||
        (first == "TextView" && second == "ImageView")
    if (!matches) return

    context.report(
      ISSUE,
      element,
      context.getElementLocation(element),
      "This tag and its children can be replaced by a `<TextView>` with compound drawables"
    )
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "UseCompoundDrawables",
      briefDescription = "Node can be replaced by a `TextView` with compound drawables",
      explanation = "A `LinearLayout` which contains an `ImageView` and a `TextView` can be more " +
        "efficiently handled as a compound drawable (a single TextView, using the " +
        "`drawableTop`, `drawableLeft`, `drawableRight` and/or `drawableBottom` attributes " +
        "to draw one or more images adjacent to the text).\n\n" +
        "If the two widgets are offset from each other with margins, this can be replaced " +
        "with a `drawablePadding` attribute.",
      category = Category.PERFORMANCE,
      priority = 4,
      severity = Severity.WARNING,
      implementation = Implementation(UseCompoundDrawableDetector::class.java, Scope.RESOURCE_FILE_SCOPE),
    )
  }
}