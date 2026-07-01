package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr
import org.w3c.dom.Element

class MotionLayoutDetector : LayoutDetector() {

    companion object {
        private const val MOTION_LAYOUT_CLASS = "androidx.constraintlayout.motion.widget.MotionLayout"
        private const val LAYOUT_DESCRIPTION = "layoutDescription"

        val ISSUE = Issue.create(
            id = "MotionLayoutInvalidSceneFileReference",
            briefDescription = "MotionLayout must reference a motion scene file",
            explanation = """
                A motion scene file specifies the animations used in a `MotionLayout`. The `app:layoutDescription`
                attribute is required and must reference a valid motion scene XML resource.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                MotionLayoutDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun getApplicableElements(): List<String> = listOf(MOTION_LAYOUT_CLASS)

    override fun visitElement(context: XmlContext, element: Element) {
        val attr = findLayoutDescription(element)

        if (attr == null) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "MotionLayout is missing a `layoutDescription` attribute pointing to a motion scene file"
            )
            return
        }

        val value = attr.value.orEmpty()
        if (!value.startsWith("@xml/")) {
            context.report(
                ISSUE,
                attr,
                context.getLocation(attr),
                "The `layoutDescription` attribute must reference a motion scene XML resource (`@xml/...`)"
            )
        }
    }

    private fun findLayoutDescription(element: Element): Attr? {
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as? Attr ?: continue
            if (attr.localName == LAYOUT_DESCRIPTION) {
                return attr
            }
        }
        return null
    }
}