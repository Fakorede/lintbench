package com.android.tools.lint.checks

import com.android.SdkConstants.AUTO_URI
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class MotionLayoutDetector : LayoutDetector() {

    override fun getApplicableElements(): List<String> = APPLICABLE_TAGS

    override fun visitElement(context: XmlContext, element: Element) {
        val attr = element.getAttributeNodeNS(AUTO_URI, ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)

        if (attr == null) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "A `MotionLayout` must specify a motion scene file using `app:layoutDescription`"
            )
            return
        }

        val value = attr.value
        if (value.isBlank()) {
            context.report(
                ISSUE,
                attr,
                context.getValueLocation(attr),
                "`app:layoutDescription` must not be empty"
            )
            return
        }

        val url = ResourceUrl.parse(value)
        if (url == null || url.type != ResourceType.XML) {
            context.report(
                ISSUE,
                attr,
                context.getValueLocation(attr),
                "`app:layoutDescription` must reference a valid `@xml` motion scene file"
            )
            return
        }

        if (!context.project.resources.hasResource(ResourceType.XML, url.name)) {
            context.report(
                ISSUE,
                attr,
                context.getValueLocation(attr),
                "The motion scene file `${url.name}` does not exist"
            )
        }
    }

    companion object {
        private const val ATTR_CONSTRAINT_LAYOUT_DESCRIPTION = "layoutDescription"
        private const val MOTION_LAYOUT = "androidx.constraintlayout.motion.widget.MotionLayout"
        private const val LEGACY_MOTION_LAYOUT = "android.support.constraint.motion.MotionLayout"

        private val APPLICABLE_TAGS = listOf(MOTION_LAYOUT, LEGACY_MOTION_LAYOUT)

        @JvmField
        val ISSUE = Issue.create(
            id = "MotionLayoutInvalidSceneFileReference",
            briefDescription = "Invalid MotionLayout scene file reference",
            explanation = """
                A motion scene file specifies the animations used in a `MotionLayout`. \
                The `app:layoutDescription` attribute is required to specify a valid motion \
                scene file.
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
}