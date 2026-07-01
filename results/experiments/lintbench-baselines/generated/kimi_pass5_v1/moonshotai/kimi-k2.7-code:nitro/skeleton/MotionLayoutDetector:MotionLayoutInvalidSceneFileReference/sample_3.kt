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
import org.w3c.dom.Attr
import org.w3c.dom.Element
import java.io.File

class MotionLayoutDetector : ResourceXmlDetector() {

    companion object {
        private const val ATTR_CONSTRAINT_LAYOUT_DESCRIPTION = "layoutDescription"
        private const val ANDROIDX_MOTION_LAYOUT = "androidx.constraintlayout.motion.widget.MotionLayout"
        private const val SUPPORT_MOTION_LAYOUT = "android.support.constraint.motion.MotionLayout"
        private const val AUTO_URI = "http://schemas.android.com/apk/res-auto"
        private const val SCENE_REF_PREFIX = "@xml/"

        private val IMPLEMENTATION = Implementation(
            MotionLayoutDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "MotionLayoutInvalidSceneFileReference",
            briefDescription = "$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION must specify a scene file",
            explanation = """
                A `MotionLayout` requires a `MotionScene` to define its animations.
                The `app:$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` attribute must reference
                a valid MotionScene XML file in the `res/xml` directory, for example
                `@xml/motion_scene`.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    private data class SceneReference(
        val context: XmlContext,
        val attribute: Attr,
        val name: String,
    )

    private val references = mutableListOf<SceneReference>()

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.LAYOUT

    override fun getApplicableElements(): Collection<String>? =
        listOf(ANDROIDX_MOTION_LAYOUT, SUPPORT_MOTION_LAYOUT)

    override fun visitElement(context: XmlContext, element: Element) {
        val attribute = element.getAttributeNodeNS(AUTO_URI, ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)
        if (attribute == null) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "A `MotionLayout` must specify a motion scene file with the " +
                    "`app:$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` attribute"
            )
            return
        }

        val value = attribute.value
        if (value.isNullOrBlank()) {
            context.report(
                ISSUE,
                attribute,
                context.getLocation(attribute),
                "The `app:$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` attribute must specify a motion scene file"
            )
            return
        }

        if (!value.startsWith(SCENE_REF_PREFIX)) {
            context.report(
                ISSUE,
                attribute,
                context.getLocation(attribute),
                "The `app:$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` attribute must reference " +
                    "a MotionScene file in `res/xml`, e.g. `@xml/motion_scene`"
            )
            return
        }

        val sceneName = value.substring(SCENE_REF_PREFIX.length)
        if (sceneName.isBlank() || sceneName.contains('/')) {
            context.report(
                ISSUE,
                attribute,
                context.getLocation(attribute),
                "The `app:$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` attribute must reference " +
                    "a valid MotionScene file name"
            )
            return
        }

        references.add(SceneReference(context, attribute, sceneName))
    }

    override fun afterCheckRootProject(context: Context) {
        for (reference in references) {
            val exists = reference.context.project.resourceFolders.any { resFolder ->
                File(resFolder, "xml/${reference.name}.xml").exists()
            }
            if (!exists) {
                reference.context.report(
                    ISSUE,
                    reference.attribute,
                    reference.context.getLocation(reference.attribute),
                    "The referenced MotionScene file `@xml/${reference.name}` does not exist"
                )
            }
        }
        references.clear()
    }
}