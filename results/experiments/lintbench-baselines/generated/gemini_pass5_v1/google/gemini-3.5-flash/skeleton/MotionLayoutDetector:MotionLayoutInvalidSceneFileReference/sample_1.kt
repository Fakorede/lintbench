package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext

class MotionLayoutDetector : ResourceXmlDetector() {

    companion object {
        private const val ATTR_CONSTRAINT_LAYOUT_DESCRIPTION = "layoutDescription"

        private val IMPLEMENTATION = Implementation(
            MotionLayoutDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "MotionLayoutInvalidSceneFileReference",
            briefDescription = "$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION must specify a scene file",
            explanation = "A motion scene file specifies the animations used in a `MotionLayout`. The `$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` is required to specify a valid motion scene file.",
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean {
        return folderType == com.android.resources.ResourceFolderType.LAYOUT
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf(
            "androidx.constraintlayout.motion.widget.MotionLayout",
            "MotionLayout"
        )
    }

    override fun afterCheckRootProject(context: Context) {
        // No-op
    }

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        val layoutDescription = if (element.hasAttributeNS("http://schemas.android.com/apk/res-auto", ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)) {
            element.getAttributeNodeNS("http://schemas.android.com/apk/res-auto", ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)
        } else if (element.hasAttribute("app:$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION")) {
            element.getAttributeNode("app:$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION")
        } else if (element.hasAttribute(ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)) {
            element.getAttributeNode(ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)
        } else {
            null
        }

        if (layoutDescription == null) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "MotionLayout requires a `$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` attribute"
            )
            return
        }

        val value = layoutDescription.value
        if (!value.startsWith("@xml/")) {
            context.report(
                ISSUE,
                layoutDescription,
                context.getValueLocation(layoutDescription),
                "The `$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` attribute must reference an XML resource (e.g. `@xml/scene_file`)"
            )
        }
    }
}