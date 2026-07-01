package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner

class ConstraintLayoutDetector : LayoutDetector(), XmlScanner {

  override fun getApplicableElements(): Collection<String> {
    return listOf(
      "androidx.constraintlayout.widget.ConstraintLayout",
      "android.support.constraint.ConstraintLayout"
    )
  }

  override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
    val children = element.childNodes ?: return
    for (i in 0 until children.length) {
      val child = children.item(i) as? org.w3c.dom.Element ?: continue
      if (isHelperTag(child.tagName)) continue

      val constraintNames = getConstraintAttributeNames(child)
      val hasHorizontal = hasHorizontalConstraint(constraintNames)
      val hasVertical = hasVerticalConstraint(constraintNames)

      if (!hasHorizontal || !hasVertical) {
        val message = when {
          !hasHorizontal && !hasVertical ->
            "This view is not constrained horizontally or vertically. Add constraints to ensure it appears in the correct position at runtime."
          !hasHorizontal ->
            "This view is not constrained horizontally. Add a constraint to ensure it appears in the correct position at runtime."
          else ->
            "This view is not constrained vertically. Add a constraint to ensure it appears in the correct position at runtime."
        }
        context.report(ISSUE, child, context.getElementLocation(child), message)
      }
    }
  }

  private fun isHelperTag(tagName: String): Boolean {
    return when (tagName) {
      "androidx.constraintlayout.widget.Guideline",
      "android.support.constraint.Guideline",
      "androidx.constraintlayout.widget.Barrier",
      "android.support.constraint.Barrier",
      "androidx.constraintlayout.widget.Group",
      "android.support.constraint.Group",
      "androidx.constraintlayout.widget.Flow",
      "android.support.constraint.Flow",
      "androidx.constraintlayout.widget.Layer",
      "android.support.constraint.Layer",
      "androidx.constraintlayout.widget.Placeholder",
      "android.support.constraint.Placeholder" -> true
      else -> false
    }
  }

  private fun getConstraintAttributeNames(node: org.w3c.dom.Element): List<String> {
    val names = mutableListOf<String>()
    val attrs = node.attributes ?: return names
    for (i in 0 until attrs.length) {
      val attr = attrs.item(i) as? org.w3c.dom.Node ?: continue
      if ((attr.namespaceURI ?: "") != APP_NAMESPACE) continue
      val localName = attr.localName ?: continue
      if (localName.startsWith("layout_constraint")) {
        names.add(localName)
      }
    }
    return names
  }

  private fun hasHorizontalConstraint(names: List<String>): Boolean {
    return names.any { name ->
      HORIZONTAL_TOKENS.any { token -> name.contains(token) } || name == CIRCLE_CONSTRAINT
    }
  }

  private fun hasVerticalConstraint(names: List<String>): Boolean {
    return names.any { name ->
      VERTICAL_TOKENS.any { token -> name.contains(token) } || name == CIRCLE_CONSTRAINT
    }
  }

  companion object {
    private const val APP_NAMESPACE = "http://schemas.android.com/apk/res-auto"
    private const val CIRCLE_CONSTRAINT = "layout_constraintCircle"
    private val HORIZONTAL_TOKENS = listOf("Left", "Right", "Start", "End")
    private val VERTICAL_TOKENS = listOf("Top", "Bottom", "Baseline")

    @JvmField
    val ISSUE = Issue.create(
      id = "MissingConstraints",
      briefDescription = "Missing Constraints",
      explanation = "The layout editor can record a widget's position using designtime " +
        "attributes (such as layout_editor_absoluteX), but these attributes are not applied at " +
        "runtime. A widget in a ConstraintLayout that does not have both horizontal and " +
        "vertical constraints may be laid out in an unintended position on the device. " +
        "Add constraints from the widget's edges to an anchor to ensure it appears where expected.",
      category = Category.CORRECTNESS,
      priority = 8,
      severity = Severity.ERROR,
      implementation = Implementation(ConstraintLayoutDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
    )
  }
}