package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element

class MotionLayoutDetector : Detector(), XmlScanner {

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.LAYOUT

    override fun getApplicableElements(): Collection<String>? =
        listOf(
            FULLY_QUALIFIED_MOTION_LAYOUT,
            MOTION_LAYOUT_TAG
        )

    override fun visitElement(context: XmlContext, element: Element) {
        val attr = element.getAttributeNodeNS(
            SdkConstants.AUTO_URI,
            SdkConstants.ATTR_CONSTRAINT_LAYOUT_DESCRIPTION
        )

        if (attr == null) {
            context.report(
                ISSUE,
                context.getLocation(element),
                "MotionLayout must specify a motion scene file via `${SdkConstants.ATTR_CONSTRAINT_LAYOUT_DESCRIPTION}`"
            )
            return
        }

        val value = attr.value
        if (value.isBlank() || !value.startsWith("@xml/")) {
            context.report(
                ISSUE,
                context.getValueLocation(attr),
                "The `${SdkConstants.ATTR_CONSTRAINT_LAYOUT_DESCRIPTION}` attribute must reference a valid motion scene file (`@xml/...`)"
            )
        }
    }

    companion object {
        private const val MOTION_LAYOUT_TAG = "MotionLayout"
        private const val FULLY_QUALIFIED_MOTION_LAYOUT =
            "androidx.constraintlayout.motion.widget.MotionLayout"

        val ISSUE = Issue.create(
            id = "MotionLayoutInvalidSceneFileReference",
            briefDescription = "Invalid MotionLayout scene file reference",
            explanation = """
                A `MotionLayout` requires the `${SdkConstants.ATTR_CONSTRAINT_LAYOUT_DESCRIPTION}` attribute to reference a valid motion scene XML resource (`@xml/...`).
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