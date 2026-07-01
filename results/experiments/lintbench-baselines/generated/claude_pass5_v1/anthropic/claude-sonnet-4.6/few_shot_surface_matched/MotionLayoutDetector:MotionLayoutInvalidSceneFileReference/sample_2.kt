package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.TAG_LAYOUT
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class MotionLayoutDetector : ResourceXmlDetector(), XmlScanner {

    companion object {
        private const val MOTION_LAYOUT_CLASS = "androidx.constraintlayout.motion.widget.MotionLayout"
        private const val ATTR_CONSTRAINT_LAYOUT_DESCRIPTION = "layoutDescription"
        private const val MOTION_SCENE_RESOURCE_PREFIX = "@xml/"

        @JvmField
        val INVALID_SCENE_FILE_REFERENCE = Issue.create(
            id = "MotionLayoutInvalidSceneFileReference",
            briefDescription = "Invalid motion scene file reference",
            explanation = """
                A motion scene file specifies the animations used in a `MotionLayout`. \
                The `layoutDescription` attribute is required to specify a valid motion scene file.
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

    /**
     * Pending reports: pairs of (XmlContext, Element) for MotionLayout elements
     * that are missing or have invalid layoutDescription attributes.
     */
    private val pendingErrors = mutableListOf<Pair<XmlContext, Element>>()

    /**
     * Set of XML resource names declared in res/xml/ folder.
     */
    private val xmlResources = mutableSetOf<String>()

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT || folderType == ResourceFolderType.XML
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(MOTION_LAYOUT_CLASS, "MotionLayout")
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val folderType = context.resourceFolderType

        // Collect XML resource file names from res/xml/
        if (folderType == ResourceFolderType.XML) {
            val fileName = context.file.nameWithoutExtension
            xmlResources.add(fileName)
            return
        }

        // We are in a layout file — check the layoutDescription attribute
        if (folderType != ResourceFolderType.LAYOUT) return

        val descriptionAttr =
            element.getAttributeNodeNS(AUTO_URI, ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)
                ?: element.getAttributeNode(ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)

        if (descriptionAttr == null) {
            // Missing attribute entirely — report after checking all files
            pendingErrors.add(Pair(context, element))
            return
        }

        val value = descriptionAttr.value
        if (value.isNullOrBlank() || !value.startsWith(MOTION_SCENE_RESOURCE_PREFIX)) {
            // Invalid or empty reference
            context.report(
                INVALID_SCENE_FILE_REFERENCE,
                element,
                context.getValueLocation(descriptionAttr),
                "`$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` must specify a valid motion scene file " +
                        "(e.g. `@xml/my_scene`)"
            )
            return
        }

        // Extract the resource name and check it later in afterCheckRootProject
        val resourceName = value.removePrefix(MOTION_SCENE_RESOURCE_PREFIX)
        if (resourceName.isBlank()) {
            context.report(
                INVALID_SCENE_FILE_REFERENCE,
                element,
                context.getValueLocation(descriptionAttr),
                "`$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` must specify a valid motion scene file " +
                        "(e.g. `@xml/my_scene`)"
            )
            return
        }

        // Store for deferred validation after all files are visited
        pendingErrors.add(Pair(context, element))
    }

    override fun afterCheckRootProject(context: com.android.tools.lint.detector.api.Context) {
        for ((xmlContext, element) in pendingErrors) {
            val descriptionAttr =
                element.getAttributeNodeNS(AUTO_URI, ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)
                    ?: element.getAttributeNode(ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)

            if (descriptionAttr == null) {
                // No layoutDescription attribute at all
                xmlContext.report(
                    INVALID_SCENE_FILE_REFERENCE,
                    element,
                    xmlContext.getNameLocation(element),
                    "`$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` must specify a valid motion scene file"
                )
                continue
            }

            val value = descriptionAttr.value ?: continue
            if (!value.startsWith(MOTION_SCENE_RESOURCE_PREFIX)) continue

            val resourceName = value.removePrefix(MOTION_SCENE_RESOURCE_PREFIX)
            if (resourceName.isBlank() || !xmlResources.contains(resourceName)) {
                xmlContext.report(
                    INVALID_SCENE_FILE_REFERENCE,
                    element,
                    xmlContext.getValueLocation(descriptionAttr),
                    "`$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` must specify a valid motion scene file; " +
                            "`$value` could not be found"
                )
            }
        }
        pendingErrors.clear()
        xmlResources.clear()
    }
}