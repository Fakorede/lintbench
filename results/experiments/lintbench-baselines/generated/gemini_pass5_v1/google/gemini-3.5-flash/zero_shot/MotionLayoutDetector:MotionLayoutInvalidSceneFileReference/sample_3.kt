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

    companion object {
        private const val ATTR_LAYOUT_DESCRIPTION = "layoutDescription"
        private const val MOTION_LAYOUT_FQCN = "androidx.constraintlayout.motion.widget.MotionLayout"
        private const val MOTION_LAYOUT_SHORT = "MotionLayout"

        @JvmField
        val ISSUE = Issue.create(
            id = "MotionLayoutInvalidSceneFileReference",
            briefDescription = "MotionLayout missing or invalid scene file reference",
            explanation = """
                A motion scene file specifies the animations used in a `MotionLayout`. \
                The `app:layoutDescription` attribute is required to specify a valid motion scene file.
                """,
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(
                MotionLayoutDetector::class.java,
                Scope.LAYOUT_RESOURCE_FILES
            )
        )
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(MOTION_LAYOUT_FQCN, MOTION_LAYOUT_SHORT)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val attribute = element.getAttributeNodeNS(AUTO_URI, ATTR_LAYOUT_DESCRIPTION)
        if (attribute == null) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "MotionLayout requires a `app:layoutDescription` attribute"
            )
            return
        }

        val value = attribute.value
        if (value.isNullOrEmpty()) {
            context.report(
                ISSUE,
                attribute,
                context.getValueLocation(attribute),
                "The `app:layoutDescription` attribute cannot be empty"
            )
            return
        }

        if (!value.startsWith("@xml/")) {
            context.report(
                ISSUE,
                attribute,
                context.getValueLocation(attribute),
                "The `app:layoutDescription` attribute must point to an XML resource (e.g., `@xml/scene`)"
            )
        }
    }
}