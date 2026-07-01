package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class MotionLayoutDetector : ResourceXmlDetector() {

    companion object {
        private const val ATTR_CONSTRAINT_LAYOUT_DESCRIPTION = "layoutDescription"
        private const val AUTO_URI = "http://schemas.android.com/apk/res-auto"

        private val IMPLEMENTATION = Implementation(
            MotionLayoutDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "MotionLayoutInvalidSceneFileReference",
            briefDescription = "$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION must specify a scene file",
            explanation = """
                A motion scene file specifies the animations used in a `MotionLayout`. \
                The `app:layoutDescription` attribute is required to specify a valid motion scene file \
                (e.g., `@xml/my_scene`).
                """,
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(
            "androidx.constraintlayout.motion.widget.MotionLayout",
            "MotionLayout"
        )
    }

    override fun afterCheckRootProject(context: Context) {
        // No-op
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val attribute = if (element.hasAttributeNS(AUTO_URI, ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)) {
            element.getAttributeNodeNS(AUTO_URI, ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)
        } else {
            element.getAttributeNode("app:$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION")
        }

        if (attribute == null) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "MotionLayout requires a `layoutDescription` attribute"
            )
            return
        }

        val value = attribute.value
        if (value.isNullOrEmpty()) {
            context.report(
                ISSUE,
                attribute,
                context.getLocation(attribute),
                "The `layoutDescription` attribute cannot be empty"
            )
        } else if (!value.startsWith("@xml/")) {
            context.report(
                ISSUE,
                attribute,
                context.getLocation(attribute),
                "The `layoutDescription` attribute must point to an XML resource (e.g. `@xml/scene_file`)"
            )
        }
    }
}