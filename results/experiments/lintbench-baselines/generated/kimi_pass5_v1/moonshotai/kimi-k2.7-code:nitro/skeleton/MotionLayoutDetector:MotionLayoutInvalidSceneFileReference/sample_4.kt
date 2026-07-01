package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr
import org.w3c.dom.Element

class MotionLayoutDetector : ResourceXmlDetector() {

    private val sceneFiles = mutableSetOf<String>()
    private val references = mutableListOf<SceneReference>()

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.LAYOUT || folderType == ResourceFolderType.XML

    override fun getApplicableElements(): Collection<String>? = APPLICABLE_ELEMENTS

    override fun visitElement(context: XmlContext, element: Element) {
        when (element.tagName) {
            MOTION_SCENE -> {
                sceneFiles.add(context.file.nameWithoutExtension)
            }
            MOTION_LAYOUT, MOTION_LAYOUT_SUPPORT -> {
                val attr = getLayoutDescriptionAttribute(element)
                if (attr == null) {
                    context.report(
                        ISSUE,
                        context.getElementLocation(element),
                        "MotionLayout must specify a motion scene file using app:$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION"
                    )
                    return
                }

                val value = attr.value?.trim() ?: ""
                when {
                    value.isEmpty() -> {
                        context.report(
                            ISSUE,
                            context.getLocation(attr),
                            "MotionLayout must specify a motion scene file using app:$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION"
                        )
                    }
                    value.startsWith("@xml/") -> {
                        references.add(SceneReference(value.substring(5), context.getLocation(attr)))
                    }
                    else -> {
                        context.report(
                            ISSUE,
                            context.getLocation(attr),
                            "The app:$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION attribute must reference a motion scene with @xml/..."
                        )
                    }
                }
            }
        }
    }

    override fun afterCheckRootProject(context: Context) {
        for (reference in references) {
            if (!sceneFiles.contains(reference.name)) {
                context.report(
                    ISSUE,
                    reference.location,
                    "The motion scene file `@xml/${reference.name}` does not exist"
                )
            }
        }
        sceneFiles.clear()
        references.clear()
    }

    private fun getLayoutDescriptionAttribute(element: Element): Attr? {
        element.getAttributeNodeNS(AUTO_URI, ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)?.let { return it }

        val attrs = element.attributes
        for (i in 0 until attrs.length) {
            val node = attrs.item(i)
            if (node is Attr && node.localName == ATTR_CONSTRAINT_LAYOUT_DESCRIPTION) {
                return node
            }
        }
        return null
    }

    private data class SceneReference(val name: String, val location: Location)

    companion object {
        private const val ATTR_CONSTRAINT_LAYOUT_DESCRIPTION = "layoutDescription"
        private const val MOTION_SCENE = "MotionScene"
        private const val MOTION_LAYOUT = "androidx.constraintlayout.motion.widget.MotionLayout"
        private const val MOTION_LAYOUT_SUPPORT = "android.support.constraint.motion.MotionLayout"
        private const val AUTO_URI = "http://schemas.android.com/apk/res-auto"

        private val APPLICABLE_ELEMENTS = listOf(
            MOTION_LAYOUT,
            MOTION_LAYOUT_SUPPORT,
            MOTION_SCENE
        )

        private val IMPLEMENTATION = Implementation(
            MotionLayoutDetector::class.java,
            Scope.RESOURCE_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "MotionLayoutInvalidSceneFileReference",
            briefDescription = "$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION must specify a scene file",
            explanation = "A `MotionLayout` requires a motion scene file (an XML resource in `res/xml`) " +
                "that defines the transitions and constraint sets. The `app:$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` " +
                "attribute must reference an existing `@xml/...` resource.",
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION,
        )
    }
}