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

class MotionLayoutDetector : ResourceXmlDetector() {

    private val sceneFiles = mutableSetOf<String>()
    private val pendingReferences = mutableListOf<MotionLayoutReference>()

    companion object {
        private const val ATTR_CONSTRAINT_LAYOUT_DESCRIPTION = "layoutDescription"

        private const val MOTION_LAYOUT_ANDROIDX =
            "androidx.constraintlayout.motion.widget.MotionLayout"
        private const val MOTION_LAYOUT_SUPPORT =
            "android.support.constraint.motion.MotionLayout"
        private const val MOTION_SCENE = "MotionScene"

        private val IMPLEMENTATION = Implementation(
            MotionLayoutDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "MotionLayoutInvalidSceneFileReference",
            briefDescription = "$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION must specify a scene file",
            explanation = """
                A `MotionLayout` uses a motion scene file to define the animations and \
                transitions applied to its child views. The `layoutDescription` attribute \
                must reference a valid motion scene XML resource in the `res/xml/` directory \
                using the `@xml/<scene_file>` syntax.
            """,
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.LAYOUT || folderType == ResourceFolderType.XML

    override fun getApplicableElements(): Collection<String>? = listOf(
        MOTION_LAYOUT_ANDROIDX,
        MOTION_LAYOUT_SUPPORT,
        MOTION_SCENE,
    )

    override fun beforeCheckRootProject(context: Context) {
        sceneFiles.clear()
        pendingReferences.clear()
    }

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            MOTION_LAYOUT_ANDROIDX, MOTION_LAYOUT_SUPPORT -> checkMotionLayout(context, element)
            MOTION_SCENE -> recordSceneFile(context, element)
        }
    }

    private fun checkMotionLayout(context: XmlContext, element: Element) {
        val attr = element.findAttribute(ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)
        if (attr == null) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "MotionLayout must specify a scene file via `$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION`",
            )
            return
        }

        val value = attr.value ?: ""
        if (value.isBlank()) {
            context.report(
                ISSUE,
                element,
                context.getLocation(attr),
                "The `$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` attribute must specify a scene file",
            )
            return
        }

        if (!value.startsWith("@xml/")) {
            context.report(
                ISSUE,
                element,
                context.getLocation(attr),
                "The `$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` attribute must reference a motion scene file with `@xml/<scene_file>`",
            )
            return
        }

        val sceneName = value.substring("@xml/".length)
        if (sceneName.isBlank()) {
            context.report(
                ISSUE,
                element,
                context.getLocation(attr),
                "The `$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` attribute must specify a valid scene file",
            )
            return
        }

        pendingReferences.add(MotionLayoutReference(context, element, attr, sceneName))
    }

    private fun recordSceneFile(context: XmlContext, element: Element) {
        if (element == element.ownerDocument.documentElement) {
            sceneFiles.add(context.file.nameWithoutExtension)
        }
    }

    override fun afterCheckRootProject(context: Context) {
        for (reference in pendingReferences) {
            if (reference.sceneName !in sceneFiles) {
                reference.context.report(
                    ISSUE,
                    reference.element,
                    reference.context.getLocation(reference.attr),
                    "The `$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` attribute references a missing scene file: `@xml/${reference.sceneName}`",
                )
            }
        }
        pendingReferences.clear()
        sceneFiles.clear()
    }

    private data class MotionLayoutReference(
        val context: XmlContext,
        val element: Element,
        val attr: Attr,
        val sceneName: String,
    )

    private fun Element.findAttribute(localName: String): Attr? {
        val attributes = attributes
        for (i in 0 until attributes.length) {
            val attr = attributes.item(i) as? Attr ?: continue
            if (attr.localName == localName) {
                return attr
            }
        }
        return null
    }
}