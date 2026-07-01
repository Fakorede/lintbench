package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr
import org.w3c.dom.Element

class MotionLayoutDetector : LayoutDetector() {

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.LAYOUT

    override fun getApplicableElements(): Collection<String>? = listOf(
        MOTION_LAYOUT,
        MOTION_LAYOUT_OLD
    )

    override fun visitElement(context: XmlContext, element: Element) {
        val attr: Attr? = element.getAttributeNodeNS(
            SdkConstants.AUTO_URI,
            LAYOUT_DESCRIPTION
        )

        if (attr == null || attr.value.isEmpty()) {
            val location: Location = context.getNameLocation(element)
            context.report(
                ISSUE,
                location,
                "MotionLayout must specify a motion scene file using `app:layoutDescription`."
            )
            return
        }

        if (!attr.value.startsWith("@xml/")) {
            context.report(
                ISSUE,
                context.getLocation(attr),
                "`app:layoutDescription` must reference a motion scene file in `res/xml/` (e.g., `@xml/scene`)."
            )
        }
    }

    companion object {
        private const val MOTION_LAYOUT = "androidx.constraintlayout.motion.widget.MotionLayout"
        private const val MOTION_LAYOUT_OLD = "android.support.constraint.motion.MotionLayout"
        private const val LAYOUT_DESCRIPTION = "layoutDescription"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "MotionLayoutInvalidSceneFileReference",
            briefDescription = "MotionLayout `layoutDescription` must specify a scene file",
            explanation = """
                A motion scene file specifies the animations used in a `MotionLayout`.
                The `app:layoutDescription` attribute is required and must reference a valid
                motion scene file (for example, `@xml/scene`).
            """.trimIndent(),
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