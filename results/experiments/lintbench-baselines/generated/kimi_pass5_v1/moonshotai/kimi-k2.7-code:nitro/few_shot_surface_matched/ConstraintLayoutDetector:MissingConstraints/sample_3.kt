package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner

class ConstraintLayoutDetector : LayoutDetector(), XmlScanner {

  override fun getApplicableElements(): Collection<String> {
    return APPLICABLE
  }

  override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
    var child: org.w3c.dom.Node? = element.firstChild
    while (child != null) {
      if (child.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
        checkConstraints(context, child as org.w3c.dom.Element)
      }
      child = child.nextSibling
    }
  }

  private fun checkConstraints(context: XmlContext, element: org.w3c.dom.Element) {
    val tag = element.tagName
    if (tag.endsWith(".Guideline") ||
      tag.endsWith(".Barrier") ||
      tag.endsWith(".Group") ||
      tag.endsWith(".Placeholder") ||
      tag == "include"
    ) {
      return
    }

    val hasAbsoluteX = element.getAttributeNS(TOOLS_URI, "layout_editor_absoluteX").isNotEmpty()
    val hasAbsoluteY = element.getAttributeNS(TOOLS_URI, "layout_editor_absoluteY").isNotEmpty()
    if (!hasAbsoluteX && !hasAbsoluteY) {
      return
    }

    val hasHorizontal = hasConstraint(element, HORIZONTAL_CONSTRAINTS)
    val hasVertical = hasConstraint(element, VERTICAL_CONSTRAINTS)

    val missingHorizontal = hasAbsoluteX && !hasHorizontal
    val missingVertical = hasAbsoluteY && !hasVertical
    if (!missingHorizontal && !missingVertical) {
      return
    }

    val message = when {
      missingHorizontal && missingVertical ->
        "This view is not constrained horizontally or vertically. At runtime it will jump to the top-left unless you add constraints."
      missingHorizontal ->
        "This view is not constrained horizontally. At runtime it will jump to the left unless you add a horizontal constraint."
      else ->
        "This view is not constrained vertically. At runtime it will jump to the top unless you add a vertical constraint."
    }

    context.report(ISSUE, element, context.getElementLocation(element), message)
  }

  private fun hasConstraint(element: org.w3c.dom.Element, names: Set<String>): Boolean {
    val attributes = element.attributes ?: return false
    for (i in 0 until attributes.length) {
      val attr = attributes.item(i)
      val localName = attr?.localName
      if (localName != null && localName in names) {
        return true
      }
    }
    return false
  }

  companion object {
    private const val TOOLS_URI = "http://schemas.android.com/tools"
    private const val CONSTRAINT_LAYOUT = "androidx.constraintlayout.widget.ConstraintLayout"
    private const val CONSTRAINT_LAYOUT_OLD = "android.support.constraint.ConstraintLayout"

    private val APPLICABLE = listOf(CONSTRAINT_LAYOUT, CONSTRAINT_LAYOUT_OLD)

    private val HORIZONTAL_CONSTRAINTS = setOf(
      "layout_constraintLeft_toLeftOf",
      "layout_constraintLeft_toRightOf",
      "layout_constraintRight_toLeftOf",
      "layout_constraintRight_toRightOf",
      "layout_constraintStart_toStartOf",
      "layout_constraintStart_toEndOf",
      "layout_constraintEnd_toStartOf",
      "layout_constraintEnd_toEndOf"
    )

    private val VERTICAL_CONSTRAINTS = setOf(
      "layout_constraintTop_toTopOf",
      "layout_constraintTop_toBottomOf",
      "layout_constraintBottom_toTopOf",
      "layout_constraintBottom_toBottomOf",
      "layout_constraintBaseline_toBaselineOf"
    )

    @JvmField
    val ISSUE = Issue.create(
      id = "MissingConstraints",
      briefDescription = "Missing Constraints in ConstraintLayout",
      explanation = "The layout editor allows you to place widgets anywhere on the canvas, " +
        "and it records the current position with designtime attributes (such as " +
        "layout_editor_absoluteX). These attributes are not applied at runtime, so if you " +
        "push your layout on a device, the widgets may appear in a different location than " +
        "shown in the editor. To fix this, make sure a widget has both horizontal and vertical " +
        "constraints by dragging from the edge connections.",
      category = Category.CORRECTNESS,
      priority = 5,
      severity = Severity.ERROR,
      implementation = Implementation(ConstraintLayoutDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
    )
  }
}