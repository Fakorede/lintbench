package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import org.w3c.dom.Attr
import org.w3c.dom.Element

class MotionLayoutDetector : ResourceXmlDetector() {

    companion object {
        const val ATTR_CONSTRAINT_LAYOUT_DESCRIPTION = "layoutDescription"
        const val KEY_URL = "https://developer.android.com/reference/androidx/constraintlayout/motion/widget/MotionLayout"

        val ISSUE = Issue.create(
            id = "MotionLayoutInvalidSceneFileReference",
            briefDescription = "$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION must specify a scene file",
            explanation = "A motion scene file specifies the animations used in a `MotionLayout`. " +
                "The `$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` attribute is required to specify a valid motion scene file.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                MotionLayoutDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            ),
            moreInfo = KEY_URL
        )
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf(
            "androidx.constraintlayout.motion.widget.MotionLayout",
            "MotionLayout"
        )
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val attr = findLayoutDescriptionAttribute(element)

        if (attr == null) {
            context.report(
                ISSUE,
                context.getLocation(element),
                "MotionLayout must specify a `$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` attribute pointing to a motion scene file"
            )
            return
        }

        val value = attr.value
        if (!value.startsWith("@xml/")) {
            context.report(
                ISSUE,
                context.getLocation(attr),
                "The `$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` attribute must reference a valid motion scene XML resource (e.g., `@xml/scene_file`)"
            )
        }
    }

    private fun findLayoutDescriptionAttribute(element: Element): Attr? {
        val attributes = element.attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as Attr
            val name = attr.localName ?: attr.name
            if (name == ATTR_CONSTRAINT_LAYOUT_DESCRIPTION || name.endsWith(":$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION")) {
                return attr
            }
        }
        return null
    }
}