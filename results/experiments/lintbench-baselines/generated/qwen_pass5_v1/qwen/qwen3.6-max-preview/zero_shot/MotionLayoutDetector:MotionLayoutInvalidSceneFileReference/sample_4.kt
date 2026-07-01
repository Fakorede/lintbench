package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.*
import com.android.utils.SdkConstants
import org.w3c.dom.Attr
import org.w3c.dom.Element

class MotionLayoutDetector : ResourceXmlDetector() {

    companion object {
        private const val ATTR_CONSTRAINT_LAYOUT_DESCRIPTION = "layoutDescription"

        @JvmField
        val ISSUE = Issue.create(
            id = "MotionLayoutInvalidSceneFileReference",
            briefDescription = "`$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` must specify a scene file",
            explanation = """
                A motion scene file specifies the animations used in a `MotionLayout`. \
                The `$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` is required to specify a valid motion scene file.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = Implementation(
                MotionLayoutDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.LAYOUT

    override fun getApplicableElements(): Collection<String>? = listOf(
        "androidx.constraintlayout.motion.widget.MotionLayout",
        "MotionLayout"
    )

    override fun visitElement(context: XmlContext, element: Element) {
        val attr = findLayoutDescriptionAttr(element)

        if (attr == null) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Missing `$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` attribute"
            )
            return
        }

        val value = attr.value
        if (!value.startsWith("@xml/")) {
            context.report(
                ISSUE,
                attr,
                context.getLocation(attr),
                "`$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` must reference a valid XML scene file (e.g., `@xml/scene`)"
            )
        }
    }

    private fun findLayoutDescriptionAttr(element: Element): Attr? {
        element.getAttributeNodeNS(SdkConstants.AUTO_URI, ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)?.let { return it as Attr }
        val attrs = element.attributes
        for (i in 0 until attrs.length) {
            val node = attrs.item(i)
            if (node.localName == ATTR_CONSTRAINT_LAYOUT_DESCRIPTION) {
                return node as Attr
            }
        }
        return null
    }
}