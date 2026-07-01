package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner

private const val CONSTRAINT_LAYOUT = "androidx.constraintlayout.widget.ConstraintLayout"
private const val CONSTRAINT_LAYOUT_SUPPORT = "android.support.constraint.ConstraintLayout"
private const val TOOLS_URI = "http://schemas.android.com/tools"
private const val ATTR_LAYOUT_EDITOR_ABSOLUTE_X = "layout_editor_absoluteX"
private const val ATTR_LAYOUT_EDITOR_ABSOLUTE_Y = "layout_editor_absoluteY"
private const val LAYOUT_CONSTRAINT_PREFIX = "layout_constraint"

class ConstraintLayoutDetector : LayoutDetector(), XmlScanner {

  override fun getApplicableElements(): Collection<String> =
    listOf(CONSTRAINT_LAYOUT, CONSTRAINT_LAYOUT_SUPPORT)

  override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
    val children = element.childNodes
    for (i in 0 until children.length) {
      val child = children.item(i) as? org.w3c.dom.Element ?: continue
      checkConstraints(context, child)
    }
  }

  private fun checkConstraints(context: XmlContext, child: org.w3c.dom.Element) {
    val tag = child.tagName
    if (tag.endsWith("Guideline") || tag.endsWith("Barrier") || tag.endsWith("Group")) {
      return
    }

    val hasAbsoluteX = child.getAttributeNodeNS(TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_X) != null
    val hasAbsoluteY = child.getAttributeNodeNS(TOOLS_URI, ATTR_LAYOUT_EDITOR_ABSOLUTE_Y) != null
    if (!hasAbsoluteX && !hasAbsoluteY) {
      return
    }

    var hasHorizontal = false
    var hasVertical = false
    val attributes = child.attributes
    for (i in 0 until attributes.length) {
      val attr = attributes.item(i)
      val localName = attr.localName ?: continue
      if (!localName.startsWith(LAYOUT_CONSTRAINT_PREFIX)) {
        continue
      }
      val suffix = localName.substring(LAYOUT_CONSTRAINT_PREFIX.length)
      if (suffix.contains("Left", ignoreCase = true) ||
          suffix.contains("Right", ignoreCase = true) ||
          suffix.contains("Start", ignoreCase = true) ||
          suffix.contains("End", ignoreCase = true) ||
          suffix.contains("Horizontal", ignoreCase = true)) {
        hasHorizontal = true
      }
      if (suffix.contains("Top", ignoreCase = true) ||
          suffix.contains("Bottom", ignoreCase = true) ||
          suffix.contains("Baseline", ignoreCase = true) ||
          suffix.contains("Vertical", ignoreCase = true)) {
        hasVertical = true
      }
      if (hasHorizontal && hasVertical) {
        break
      }
    }

    if (hasAbsoluteX && !hasHorizontal) {
      context.report(
        ISSUE,
        child,
        context.getElementLocation(child),
        "This view is not constrained horizontally: at runtime it will jump to the left unless constrained."
      )
    }
    if (hasAbsoluteY && !hasVertical) {
      context.report(
        ISSUE,
        child,
        context.getElementLocation(child),
        "This view is not constrained vertically: at runtime it will jump to the top unless constrained."
      )
    }
  }

  companion object {
    @JvmField
    val ISSUE = Issue.create(
      id = "MissingConstraints",
      briefDescription = "Missing Constraints in ConstraintLayout",
      explanation = "The layout editor allows you to place widgets anywhere on the canvas, " +
        "and it records the current position with designtime attributes (such as " +
        "layout_editor_absoluteX). These attributes are not applied at runtime, so if you push " +
        "your layout on a device, the widgets may appear in a different location than shown in " +
        "the editor. To fix this, make sure a widget has both horizontal and vertical " +
        "constraints by dragging from the edge connections.",
      category = Category.CORRECTNESS,
      priority = 7,
      severity = Severity.ERROR,
      implementation = Implementation(
        ConstraintLayoutDetector::class.java,
        Scope.RESOURCE_FILE_SCOPE
      )
    )
  }
}