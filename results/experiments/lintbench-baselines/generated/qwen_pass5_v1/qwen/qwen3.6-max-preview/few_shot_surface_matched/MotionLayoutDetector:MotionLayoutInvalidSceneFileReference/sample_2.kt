package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class MotionLayoutDetector : ResourceXmlDetector(), XmlScanner {

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf("androidx.constraintlayout.motion.widget.MotionLayout")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val attr = element.getAttributeNodeNS(
            "http://schemas.android.com/apk/res-auto",
            "layoutDescription"
        )

        if (attr == null) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "MotionLayout must specify a `layoutDescription` attribute pointing to a valid motion scene file"
            )
        } else {
            val value = attr.value
            if (!value.startsWith("@xml/")) {
                context.report(
                    ISSUE,
                    attr,
                    context.getValueLocation(attr),
                    "`layoutDescription` must reference a valid motion scene file in `res/xml/`"
                )
            }
        }
    }

    override fun afterCheckRootProject(context: Context) {
        // Hook provided as requested; no cross-file aggregation required for this check.
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "MotionLayoutInvalidSceneFileReference",
            briefDescription = "MotionLayout must specify a scene file",
            explanation = "A motion scene file specifies the animations used in a `MotionLayout`. " +
                "The `layoutDescription` attribute is required to specify a valid motion scene file.",
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