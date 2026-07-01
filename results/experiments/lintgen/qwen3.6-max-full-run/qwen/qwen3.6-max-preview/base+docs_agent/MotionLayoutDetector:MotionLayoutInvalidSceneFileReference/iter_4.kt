package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Element

class MotionLayoutDetector : Detector(), XmlScanner {

    companion object {
        const val ATTR_CONSTRAINT_LAYOUT_DESCRIPTION = "layoutDescription"

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "MotionLayoutInvalidSceneFileReference",
            briefDescription = "`layoutDescription` must specify a scene file",
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

    override fun getApplicableElements(): Collection<String>? {
        return listOf("androidx.constraintlayout.motion.widget.MotionLayout", "MotionLayout")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val attr = findLayoutDescriptionAttr(element)

        if (attr == null) {
            context.report(
                ISSUE,
                context.getLocation(element),
                "MotionLayout must specify a `layoutDescription` attribute pointing to a motion scene file"
            )
            return
        }

        val value = attr.value
        if (!value.startsWith("@xml/")) {
            context.report(
                ISSUE,
                context.getLocation(attr),
                "`layoutDescription` must reference a valid motion scene file in `res/xml/` (e.g., `@xml/scene_file`)"
            )
        }
    }

    private fun findLayoutDescriptionAttr(element: Element): Attr? {
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as Attr
            val name = attr.localName ?: attr.name
            if (name == ATTR_CONSTRAINT_LAYOUT_DESCRIPTION) {
                return attr
            }
        }
        return null
    }
}