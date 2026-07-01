package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext

class MotionLayoutDetector : ResourceXmlDetector() {

    companion object {
        private const val ATTR_LAYOUT_DESCRIPTION = "layoutDescription"
        private const val AUTO_URI = "http://schemas.android.com/apk/res-auto"
        private const val REQUIRED_PREFIX = "@xml/"
        private const val MOTION_LAYOUT_ANDROIDX = "androidx.constraintlayout.motion.widget.MotionLayout"
        private const val MOTION_LAYOUT_SUPPORT = "android.support.constraint.motion.MotionLayout"

        private val IMPLEMENTATION = Implementation(
            MotionLayoutDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "MotionLayoutInvalidSceneFileReference",
            briefDescription = "app:layoutDescription must specify a scene file",
            explanation = """
                A MotionLayout requires a motion scene file that describes the animations
                between its ConstraintSets. This is specified with the
                `app:layoutDescription` attribute, which must reference an XML resource in
                `res/xml/` (for example `@xml/scene`). Ensure that the referenced file exists.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    private data class SceneReference(
        val fileName: String,
        val location: Location,
    )

    private val references = mutableListOf<SceneReference>()

    override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean {
        return folderType == com.android.resources.ResourceFolderType.LAYOUT
    }

    override fun getApplicableElements(): Collection<String>? {
        return listOf(MOTION_LAYOUT_ANDROIDX, MOTION_LAYOUT_SUPPORT)
    }

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        val attr = element.descriptionAttr()
        val description = attr?.value

        if (description.isNullOrEmpty()) {
            context.report(
                ISSUE,
                context.getNameLocation(element),
                "MotionLayout must specify a motion scene file with `app:layoutDescription`",
            )
            return
        }

        if (!description.startsWith(REQUIRED_PREFIX)) {
            context.report(
                ISSUE,
                context.getValueLocation(attr),
                "`app:layoutDescription` must reference a motion scene file with `@xml/<filename>`",
            )
            return
        }

        val fileName = description.substring(REQUIRED_PREFIX.length)
        if (fileName.isEmpty() || fileName.contains("/")) {
            context.report(
                ISSUE,
                context.getValueLocation(attr),
                "`app:layoutDescription` must reference a valid `@xml/<filename>` resource",
            )
            return
        }

        references.add(SceneReference(fileName, context.getValueLocation(attr)))
    }

    override fun afterCheckRootProject(context: Context) {
        for (reference in references) {
            if (!existsXmlResource(context.project, reference.fileName)) {
                context.report(
                    ISSUE,
                    reference.location,
                    "Motion scene file `@xml/${reference.fileName}` does not exist",
                )
            }
        }
        references.clear()
    }

    private fun existsXmlResource(
        project: com.android.tools.lint.detector.api.Project,
        name: String,
    ): Boolean {
        if (project.getResources("xml", name).isNotEmpty()) return true
        return project.allLibraries.any { it.getResources("xml", name).isNotEmpty() }
    }

    private fun org.w3c.dom.Element.descriptionAttr(): org.w3c.dom.Attr? {
        getAttributeNodeNS(AUTO_URI, ATTR_LAYOUT_DESCRIPTION)?.let { return it }

        val attrs = attributes
        for (i in 0 until attrs.length) {
            val node = attrs.item(i) as? org.w3c.dom.Attr ?: continue
            if (node.localName == ATTR_LAYOUT_DESCRIPTION) {
                return node
            }
        }
        return null
    }
}