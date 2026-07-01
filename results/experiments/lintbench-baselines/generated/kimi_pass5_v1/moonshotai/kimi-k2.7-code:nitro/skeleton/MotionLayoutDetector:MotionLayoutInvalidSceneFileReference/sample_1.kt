package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class MotionLayoutDetector : ResourceXmlDetector() {

    companion object {
        private const val MOTION_LAYOUT_TAG =
            "androidx.constraintlayout.motion.widget.MotionLayout"
        private const val MOTION_LAYOUT_TAG_SUPPORT =
            "android.support.constraint.motion.MotionLayout"
        private const val LAYOUT_DESCRIPTION_ATTR = "layoutDescription"
        private const val XML_RESOURCE_PREFIX = "@xml/"

        private val IMPLEMENTATION = Implementation(
            MotionLayoutDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "MotionLayoutInvalidSceneFileReference",
            briefDescription = "$LAYOUT_DESCRIPTION_ATTR must specify a scene file",
            explanation = """
                A `MotionLayout` requires a motion scene file that describes the animations \
                and constraints used by the layout. The `app:layoutDescription` attribute \
                must be present and must reference a motion scene XML resource with \
                `@xml/<scene_file>`.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.LAYOUT

    override fun getApplicableElements(): Collection<String>? =
        listOf(MOTION_LAYOUT_TAG, MOTION_LAYOUT_TAG_SUPPORT)

    override fun visitElement(context: XmlContext, element: Element) {
        val descriptionAttr = element.attributes?.run {
            (0 until length).map { item(it) }.firstOrNull {
                it.localName == LAYOUT_DESCRIPTION_ATTR
            }
        }

        val location: Location = if (descriptionAttr != null) {
            context.getLocation(descriptionAttr)
        } else {
            context.getElementLocation(element)
        }

        when {
            descriptionAttr == null -> {
                context.report(
                    ISSUE,
                    element,
                    location,
                    "MotionLayout is missing the `app:layoutDescription` attribute; it must reference a motion scene file (@xml/...)"
                )
            }
            descriptionAttr.nodeValue.isNullOrBlank() -> {
                context.report(
                    ISSUE,
                    element,
                    location,
                    "`app:layoutDescription` must reference a motion scene file (@xml/...)"
                )
            }
            !descriptionAttr.nodeValue.startsWith(XML_RESOURCE_PREFIX) -> {
                context.report(
                    ISSUE,
                    element,
                    location,
                    "`app:layoutDescription` must reference a motion scene file with `@xml/...`"
                )
            }
        }
    }

    override fun afterCheckRootProject(context: Context) {
        // Per-element checks are sufficient; no project-wide aggregation is required.
    }
}