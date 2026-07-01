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
    return listOf(CONSTRAINT_LAYOUT, CONSTRAINT_LAYOUT_OLD)
  }

  override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
    val childNodes = element.childNodes ?: return
    for (i in 0 until childNodes.length) {
      val node = childNodes.item(i)
      if (node.nodeType != org.w3c.dom.Node.ELEMENT_NODE) continue

      val child = node as org.w3c.dom.Element
      val tag = child.tagName
      if (tag == GUIDELINE || tag == GUIDELINE_OLD ||
          tag == BARRIER || tag == BARRIER_OLD ||
          tag == GROUP || tag == GROUP_OLD ||
          tag == REQUEST_FOCUS || tag == MERGE) {
        continue
      }

      val attrs = child.attributes ?: continue
      var hasHorizontal = false
      var hasVertical = false

      for (j in 0 until attrs.length) {
        val attr = attrs.item(j) ?: continue
        val name = attr.localName ?: continue

        if (name == ATTR_LAYOUT_CONSTRAINT_CIRCLE) {
          hasHorizontal = true
          hasVertical = true
          break
        }

        when (name) {
          in HORIZONTAL_CONSTRAINTS -> hasHorizontal = true
          in VERTICAL_CONSTRAINTS -> hasVertical = true
        }

        if (hasHorizontal && hasVertical) break
      }

      val location = context.getElementLocation(child)
      if (!hasHorizontal) {
        context.report(
          ISSUE,
          child,
          location,
          "This view is not constrained horizontally. At runtime it will not be positioned as shown in the editor; add a constraint from the start/left/end/right edges."
        )
      }
      if (!hasVertical) {
        context.report(
          ISSUE,
          child,
          location,
          "This view is not constrained vertically. At runtime it will not be positioned as shown in the editor; add a constraint from the top/bottom/baseline edges."
        )
      }
    }
  }

  companion object {
    private const val CONSTRAINT_LAYOUT = "androidx.constraintlayout.widget.ConstraintLayout"
    private const val CONSTRAINT_LAYOUT_OLD = "android.support.constraint.ConstraintLayout"

    private const val GUIDELINE = "androidx.constraintlayout.widget.Guideline"
    private const val GUIDELINE_OLD = "android.support.constraint.Guideline"
    private const val BARRIER = "androidx.constraintlayout.widget.Barrier"
    private const val BARRIER_OLD = "android.support.constraint.Barrier"
    private const val GROUP = "androidx.constraintlayout.widget.Group"
    private const val GROUP_OLD = "android.support.constraint.Group"

    private const val REQUEST_FOCUS = "requestFocus"
    private const val MERGE = "merge"

    private const val ATTR_LAYOUT_CONSTRAINT_CIRCLE = "layout_constraintCircle"

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
      explanation = """
        The layout editor allows you to place widgets anywhere on the canvas, and it records the current position with designtime attributes (such as `layout_editor_absoluteX` and `layout_editor_absoluteY`). These attributes are not applied at runtime, so if you push your layout on a device, the widgets may appear in a different location than shown in the editor. To fix this, make sure a widget has both horizontal and vertical constraints by dragging from the edge connections.
      """.trimIndent(),
      category = Category.CORRECTNESS,
      priority = 8,
      severity = Severity.ERROR,
      implementation = Implementation(ConstraintLayoutDetector::class.java, Scope.RESOURCE_FILE_SCOPE)
    )
  }
}