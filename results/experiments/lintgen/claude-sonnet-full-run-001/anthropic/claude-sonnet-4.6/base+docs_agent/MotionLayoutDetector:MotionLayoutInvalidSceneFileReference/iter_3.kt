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

    companion object {
        private const val MOTION_LAYOUT_CLASS = "androidx.constraintlayout.motion.widget.MotionLayout"
        const val ATTR_CONSTRAINT_LAYOUT_DESCRIPTION = "layoutDescription"

        @JvmField
        val INVALID_SCENE_FILE_REFERENCE = Issue.create(
            id = "MotionLayoutInvalidSceneFileReference",
            briefDescription = "`$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` must specify a scene file",
            explanation = """
                A motion scene file specifies the animations used in a `MotionLayout`. \
                The `$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` is required to specify a valid motion \
                scene file.
            """,
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(
                MotionLayoutDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(MOTION_LAYOUT_CLASS)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val ns = SdkConstants.AUTO_URI
        val hasAttr = element.hasAttributeNS(ns, ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)

        if (!hasAttr) {
            context.report(
                INVALID_SCENE_FILE_REFERENCE,
                element,
                context.getNameLocation(element),
                "`MotionLayout` is missing the `$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` attribute " +
                        "that specifies a motion scene file"
            )
            return
        }

        val attrValue = element.getAttributeNS(ns, ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)

        if (attrValue.isNullOrBlank()) {
            val attrNode = element.getAttributeNodeNS(ns, ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)
            context.report(
                INVALID_SCENE_FILE_REFERENCE,
                element,
                context.getValueLocation(attrNode),
                "`$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` must specify a valid motion scene file"
            )
            return
        }

        if (!attrValue.startsWith("@xml/") && !attrValue.startsWith("@+xml/")) {
            val attrNode = element.getAttributeNodeNS(ns, ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)
            context.report(
                INVALID_SCENE_FILE_REFERENCE,
                element,
                context.getValueLocation(attrNode),
                "`$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` must specify a motion scene file " +
                        "using `@xml/` resource reference, but was `$attrValue`"
            )
        }
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT
    }
}