package com.android.tools.lint.checks

import com.android.SdkConstants.AUTO_URI
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class MotionLayoutDetector : LayoutDetector() {

    override fun getApplicableElements(): Collection<String>? {
        return listOf(
            "androidx.constraintlayout.motion.widget.MotionLayout",
            "android.support.constraint.motion.MotionLayout"
        )
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val attr = element.getAttributeNodeNS(AUTO_URI, "layoutDescription")
        if (attr == null) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "The attribute `app:layoutDescription` is missing. A `MotionLayout` requires a motion scene file."
            )
            return
        }

        val value = attr.value
        if (value.isNullOrEmpty()) {
            context.report(
                ISSUE,
                attr,
                context.getValueLocation(attr),
                "The `app:layoutDescription` attribute cannot be empty."
            )
        } else if (!value.startsWith("@xml/")) {
            context.report(
                ISSUE,
                attr,
                context.getValueLocation(attr),
                "The `app:layoutDescription` attribute must specify a valid motion scene file (e.g., `@xml/your_scene`)."
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "MotionLayoutInvalidSceneFileReference",
            briefDescription = "MotionLayout missing or invalid scene file reference",
            explanation = """
                A motion scene file specifies the animations used in a `MotionLayout`. \
                The `layoutDescription` attribute is required to specify a valid motion scene file (usually in `@xml/`).
                """,
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(
                MotionLayoutDetector::class.java,
                Scope.LAYOUT_RESOURCE_SCOPE
            )
        )
    }
}