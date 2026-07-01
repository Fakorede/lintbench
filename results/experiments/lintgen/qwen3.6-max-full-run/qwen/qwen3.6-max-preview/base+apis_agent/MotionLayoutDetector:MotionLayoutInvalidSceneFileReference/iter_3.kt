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

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf(
            "androidx.constraintlayout.motion.widget.MotionLayout",
            "android.support.constraint.motion.MotionLayout"
        )
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val attr = element.getAttributeNodeNS(SdkConstants.AUTO_URI, SdkConstants.ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)
            ?: element.getAttributeNode(SdkConstants.ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)

        if (attr == null) {
            context.report(
                ISSUE,
                context.getLocation(element),
                "Missing `${SdkConstants.ATTR_CONSTRAINT_LAYOUT_DESCRIPTION}` attribute"
            )
            return
        }

        val value = attr.value
        if (!value.startsWith("@xml/")) {
            context.report(
                ISSUE,
                context.getLocation(attr),
                "`${SdkConstants.ATTR_CONSTRAINT_LAYOUT_DESCRIPTION}` must reference a motion scene file in `res/xml/`"
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "MotionLayoutInvalidSceneFileReference",
            briefDescription = "`layoutDescription` must specify a scene file",
            explanation = "A motion scene file specifies the animations used in a `MotionLayout`. " +
                "The `${SdkConstants.ATTR_CONSTRAINT_LAYOUT_DESCRIPTION}` is required to specify a valid motion scene file.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                MotionLayoutDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
        const val KEY_URL = "https://developer.android.com/reference/androidx/constraintlayout/motion/widget/MotionLayout"
    }
}