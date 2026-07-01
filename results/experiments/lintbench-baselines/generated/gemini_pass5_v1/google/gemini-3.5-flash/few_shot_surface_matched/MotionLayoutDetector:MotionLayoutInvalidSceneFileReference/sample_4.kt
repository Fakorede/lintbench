package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner

class MotionLayoutDetector : ResourceXmlDetector(), XmlScanner {

    override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean {
        return folderType == com.android.resources.ResourceFolderType.LAYOUT
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(
            "androidx.constraintlayout.motion.widget.MotionLayout",
            "MotionLayout"
        )
    }

    override fun afterCheckRootProject(context: Context) {
        super.afterCheckRootProject(context)
    }

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        val tagName = element.tagName
        if (tagName != "androidx.constraintlayout.motion.widget.MotionLayout" && tagName != "MotionLayout") {
            return
        }

        val autoUri = "http://schemas.android.com/apk/res-auto"
        val layoutDescriptionAttr = "layoutDescription"

        val attribute = element.getAttributeNodeNS(autoUri, layoutDescriptionAttr)
        if (attribute == null) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "MotionLayout must specify a scene file using `app:layoutDescription`"
            )
            return
        }

        val value = attribute.value
        if (value.isNullOrEmpty() || !value.startsWith("@xml/")) {
            context.report(
                ISSUE,
                attribute,
                context.getValueLocation(attribute),
                "The `layoutDescription` attribute must specify a valid motion scene file (e.g. `@xml/scene_file`)"
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "MotionLayoutInvalidSceneFileReference",
            briefDescription = "MotionLayout missing or invalid scene file reference",
            explanation = "A motion scene file specifies the animations used in a `MotionLayout`. " +
                    "The `layoutDescription` attribute is required to specify a valid motion scene file.",
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(
                MotionLayoutDetector::class.java,
                Scope.LAYOUT_RESOURCE_FILES
            )
        )
    }
}