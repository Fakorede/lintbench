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
import org.w3c.dom.Element
import java.io.File

class MotionLayoutDetector : Detector(), Detector.XmlScanner {

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.LAYOUT

    override fun getApplicableElements(): Collection<String>? =
        MOTION_LAYOUT_TAGS

    override fun visitElement(context: XmlContext, element: Element) {
        val attr = element.getAttributeNodeNS(
            SdkConstants.AUTO_URI,
            SdkConstants.ATTR_CONSTRAINT_LAYOUT_DESCRIPTION
        )

        if (attr == null) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "layoutDescription must specify a scene file"
            )
            return
        }

        val value = attr.value ?: ""
        if (value.isBlank() || !value.startsWith("@xml/")) {
            context.report(
                ISSUE,
                attr,
                context.getValueLocation(attr),
                "layoutDescription must specify a scene file"
            )
            return
        }

        val sceneName = value.substring(5)
        if (sceneName.isBlank() || sceneName.contains("/")) {
            context.report(
                ISSUE,
                attr,
                context.getValueLocation(attr),
                "layoutDescription must specify a scene file"
            )
            return
        }

        val exists = context.project.resourceFolders.any { resFolder ->
            File(resFolder, "xml/$sceneName.xml").exists()
        }

        if (!exists) {
            context.report(
                ISSUE,
                attr,
                context.getValueLocation(attr),
                "The motion scene file `res/xml/$sceneName.xml` could not be found"
            )
        }
    }

    companion object {
        private val MOTION_LAYOUT_TAGS = listOf(
            "MotionLayout",
            "androidx.constraintlayout.motion.widget.MotionLayout",
            "android.support.constraint.motion.widget.MotionLayout"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "MotionLayoutInvalidSceneFileReference",
            briefDescription = "Invalid MotionLayout scene file reference",
            explanation = """
                A `MotionLayout` requires a motion scene file that defines its animations. \
                The `app:layoutDescription` attribute must reference an existing XML resource \
                in `res/xml/`.
            """,
            moreInfo = "https://developer.android.com/training/constraint-layout/motionlayout",
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