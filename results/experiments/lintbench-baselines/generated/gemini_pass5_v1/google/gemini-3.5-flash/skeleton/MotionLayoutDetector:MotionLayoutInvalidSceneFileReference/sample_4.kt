package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.ResourceXmlDetector
import org.w3c.dom.Element

class MotionLayoutDetector : ResourceXmlDetector() {

    companion object {
        private const val ATTR_CONSTRAINT_LAYOUT_DESCRIPTION = "layoutDescription"
        private const val AUTO_URI = "http://schemas.android.com/apk/res-auto"
        private const val MOTION_LAYOUT_FQCN = "androidx.constraintlayout.motion.widget.MotionLayout"
        private const val MOTION_LAYOUT_SHORT = "MotionLayout"

        private val IMPLEMENTATION = Implementation(
            MotionLayoutDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "MotionLayoutInvalidSceneFileReference",
            briefDescription = "$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION must specify a scene file",
            explanation = "A motion scene file specifies the animations used in a `MotionLayout`. The `$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` attribute is required to specify a valid motion scene file.",
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
        return listOf(MOTION_LAYOUT_FQCN, MOTION_LAYOUT_SHORT)
    }

    override fun afterCheckRootProject(context: Context) {
        // No-op
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val attribute = if (element.hasAttributeNS(AUTO_URI, ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)) {
            element.getAttributeNodeNS(AUTO_URI, ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)
        } else {
            null
        }

        if (attribute == null) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "MotionLayout is missing the required `$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` attribute"
            )
            return
        }

        val value = attribute.value
        val isValid = !value.isNullOrEmpty() && (value.startsWith("@xml/") || value.startsWith("@*xml/"))
        if (!isValid) {
            context.report(
                ISSUE,
                attribute,
                context.getValueLocation(attribute),
                "The `$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` attribute must reference an XML resource (e.g., `@xml/your_scene`)"
            )
        }
    }
}