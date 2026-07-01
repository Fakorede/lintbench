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
import org.w3c.dom.Element

class MotionLayoutDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String> {
        return listOf(
            "MotionLayout",
            "androidx.constraintlayout.motion.widget.MotionLayout",
            "com.android.constraintlayout.motion.widget.MotionLayout"
        )
    }

    override fun visitElement(context: XmlContext, element: Element) {
        if (context.resourceFolderType != ResourceFolderType.LAYOUT) {
            return
        }

        val hasLayoutDescription = element.hasAttributeNS(
            SdkConstants.AUTO_URI,
            SdkConstants.ATTR_CONSTRAINT_LAYOUT_DESCRIPTION
        )

        if (!hasLayoutDescription) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "MotionLayout requires a `app:layoutDescription` attribute"
            )
            return
        }

        val attr = element.getAttributeNodeNS(
            SdkConstants.AUTO_URI,
            SdkConstants.ATTR_CONSTRAINT_LAYOUT_DESCRIPTION
        ) ?: return

        val value = attr.value
        if (value.isNullOrEmpty() || !value.startsWith("@xml/")) {
            context.report(
                ISSUE,
                attr,
                context.getValueLocation(attr),
                "The `layoutDescription` attribute must point to a valid XML resource (e.g. `@xml/scene_file`)"
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
                The `app:layoutDescription` attribute is required to specify a valid motion scene file.
                """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = Implementation(
                MotionLayoutDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}