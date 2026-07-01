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
import org.w3c.dom.Element

class MotionLayoutDetector : Detector(), XmlScanner {

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(
            "androidx.constraintlayout.motion.widget.MotionLayout",
            "MotionLayout"
        )
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val attributes = element.attributes
        var attr: Attr? = element.getAttributeNodeNS(SdkConstants.AUTO_URI, SdkConstants.ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)
        
        if (attr == null && attributes != null) {
            for (i in 0 until attributes.length) {
                val item = attributes.item(i) as? Attr ?: continue
                if (item.localName == SdkConstants.ATTR_CONSTRAINT_LAYOUT_DESCRIPTION) {
                    attr = item
                    break
                }
            }
        }

        if (attr == null) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "MotionLayout requires a `${SdkConstants.ATTR_CONSTRAINT_LAYOUT_DESCRIPTION}` attribute"
            )
        } else {
            val value = attr.value
            if (!value.startsWith("@xml/")) {
                context.report(
                    ISSUE,
                    attr,
                    context.getValueLocation(attr),
                    "The `${SdkConstants.ATTR_CONSTRAINT_LAYOUT_DESCRIPTION}` attribute must specify a valid motion scene file (e.g., `@xml/scene`)"
                )
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "MotionLayoutInvalidSceneFileReference",
            briefDescription = "MotionLayout layoutDescription must specify a scene file",
            explanation = """
                A motion scene file specifies the animations used in a `MotionLayout`. \
                The `layoutDescription` attribute is required to specify a valid motion scene file (typically in `@xml/`).
                """,
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