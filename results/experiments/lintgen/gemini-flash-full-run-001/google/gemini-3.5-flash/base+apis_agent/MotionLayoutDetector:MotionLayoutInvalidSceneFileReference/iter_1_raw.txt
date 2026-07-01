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

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(
            "MotionLayout",
            "androidx.constraintlayout.motion.widget.MotionLayout",
            "com.android.constraintlayout.motion.widget.MotionLayout"
        )
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val attribute = element.getAttributeNodeNS(
            SdkConstants.AUTO_URI,
            SdkConstants.ATTR_CONSTRAINT_LAYOUT_DESCRIPTION
        )
        if (attribute == null) {
            context.report(
                INVALID_SCENE_FILE_REFERENCE,
                element,
                context.getNameLocation(element),
                "The attribute `app:layoutDescription` is missing"
            )
            return
        }
        val value = attribute.value
        if (!value.startsWith("@xml/")) {
            context.report(
                INVALID_SCENE_FILE_REFERENCE,
                attribute,
                context.getValueLocation(attribute),
                "The `layoutDescription` attribute must specify a scene file"
            )
        }
    }

    companion object {
        const val KEY_URL = "url"

        @JvmField
        val INVALID_SCENE_FILE_REFERENCE = Issue.create(
            id = "MotionLayoutInvalidSceneFileReference",
            briefDescription = "MotionLayout missing or invalid scene file reference",
            explanation = """
                A motion scene file specifies the animations used in a `MotionLayout`. \
                The `app:layoutDescription` is required to specify a valid motion scene file.
                """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = Implementation(
                MotionLayoutDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )

        @JvmField
        val ISSUE = INVALID_SCENE_FILE_REFERENCE
    }
}