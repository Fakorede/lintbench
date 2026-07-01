package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*

class ConstraintLayoutDetector : LayoutDetector(), XmlScanner {

  override fun getApplicableElements(): Collection<String> = listOf(
    OLD_CONSTRAINT_LAYOUT,
    NEW_CONSTRAINT_LAYOUT
  )

  override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
    val children = element.childNodes
    for (i in 0 until children.length) {
      val child = children.item(i)
      if (child.nodeType != org.w3c.dom.Node.ELEMENT_NODE) continue

      val childElement = child as? org.w3c.dom.Element ?: continue
      val tag = childElement.tagName
      if (tag.isConstraintHelper()) continue

      val hasHorizontal = hasHorizontalConstraint(childElement)
      val hasVertical = hasVerticalConstraint(childElement)
      if (hasHorizontal && hasVertical) continue

      context.report(
        ISSUE,
        childElement,
        context.getLocation(childElement),
        buildMessage(hasHorizontal, hasVertical)
      )
    }
  }

  private fun String.isConstraintHelper(): Boolean {
    return endsWith("Guideline") || endsWith("Barrier") || endsWith("Group") || endsWith("Placeholder")
  }

  private fun hasHorizontalConstraint(element: org.w3c.dom.Element): Boolean {
    val attributes = element.attributes
    for (i in 0 until attributes.length) {
      val attr = attributes.item(i) as? org.w3c.dom.Attr ?: continue
      val name = attr.localName ?: continue
      if (name in HORIZONTAL_CONSTRAINTS) return true
    }
    return false
  }

  private fun hasVerticalConstraint(element: org.w3c.dom.Element): Boolean {
    val attributes = element.attributes
    for (i in 0 until attributes.length) {
      val attr = attributes.item(i) as? org.w3c.dom.Attr ?: continue
      val name = attr.localName ?: continue
      if (name in VERTICAL_CONSTRAINTS) return true
    }
    return false
  }

  private fun buildMessage(hasHorizontal: Boolean, hasVertical: Boolean): String {
    return when {
      !hasHorizontal && !hasVertical ->
        "This view is not constrained horizontally and vertically: at runtime it will jump to the left and top unless you add constraints"
      !hasHorizontal ->
        "This view is not constrained horizontally: at runtime it will jump to the left unless you add a constraint"
      else ->
        "This view is not constrained vertically: at runtime it will jump to the top unless you add a constraint"
    }
  }

  companion object {
    private const val OLD_CONSTRAINT_LAYOUT = "android.support.constraint.ConstraintLayout"
    private const val NEW_CONSTRAINT_LAYOUT = "androidx.constraintlayout.widget.ConstraintLayout"

    private val HORIZONTAL_CONSTRAINTS = setOf(
      "layout_constraintLeft_toLeftOf",
      "layout_constraintLeft_toRightOf",
      "layout_constraintRight_toLeftOf",
      "layout_constraintRight_toRightOf",
      "layout_constraintStart_toEndOf",
      "layout_constraintStart_toStartOf",
      "layout_constraintEnd_toStartOf",
      "layout_constraintEnd_toEndOf",
      "layout_constraintCircle"
    )

    private val VERTICAL_CONSTRAINTS = setOf(
      "layout_constraintTop_toTopOf",
      "layout_constraintTop_toBottomOf",
      "layout_constraintBottom_toTopOf",
      "layout_constraintBottom_toBottomOf",
      "layout_constraintBaseline_toBaselineOf",
      "layout_constraintCircle"
    )

    @JvmField
    val ISSUE = Issue.create(
      id = "MissingConstraints",
      briefDescription = "Missing Constraints in ConstraintLayout",
      explanation = "The layout editor allows you to place widgets anywhere on the canvas, " +
        "and it records the current position with designtime attributes (such as " +
        "`layout_editor_absoluteX`). These attributes are not applied at runtime, so if " +
        "you push your layout on a device, the widgets may appear in a different location " +
        "than shown in the editor. To fix this, make sure a widget has both horizontal and " +
        "vertical constraints by dragging from the edge connections.",
      category = Category.CORRECTNESS,
      priority = 8,
      severity = Severity.ERROR,
      implementation = Implementation(
        ConstraintLayoutDetector::class.java,
        Scope.RESOURCE_FILE_SCOPE
      )
    )
  }
}