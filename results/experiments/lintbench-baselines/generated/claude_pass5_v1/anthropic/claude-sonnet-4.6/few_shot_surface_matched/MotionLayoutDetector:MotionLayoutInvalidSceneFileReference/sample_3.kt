package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.TAG
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

class MotionLayoutDetector : ResourceXmlDetector() {

    companion object {
        private const val MOTION_LAYOUT = "androidx.constraintlayout.motion.widget.MotionLayout"
        private const val ATTR_CONSTRAINT_LAYOUT_DESCRIPTION = "layoutDescription"
        private const val MOTION_SCENE_TAG = "MotionScene"

        @JvmField
        val INVALID_SCENE_FILE_REFERENCE = Issue.create(
            id = "MotionLayoutInvalidSceneFileReference",
            briefDescription = "Invalid scene file reference",
            explanation = """
                A motion scene file specifies the animations used in a `MotionLayout`. \
                The `layoutDescription` attribute is required to specify a valid motion scene file.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                MotionLayoutDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }

    /**
     * Map from resource reference value (e.g. "@xml/scene") to the XmlContext and Element
     * where the layoutDescription attribute was found, so we can validate after the full
     * project has been checked.
     */
    private data class PendingCheck(
        val context: XmlContext,
        val element: Element,
        val attrValue: String
    )

    private val pendingChecks = mutableListOf<PendingCheck>()

    /** Set of XML resource file names (without extension) that contain a MotionScene root. */
    private val motionSceneFiles = mutableSetOf<String>()

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT || folderType == ResourceFolderType.XML
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(MOTION_LAYOUT, MOTION_SCENE_TAG)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val tagName = element.tagName ?: element.localName ?: return

        if (tagName == MOTION_SCENE_TAG) {
            // Record this XML file as a valid motion scene file
            val fileName = context.file.nameWithoutExtension
            motionSceneFiles.add(fileName)
            return
        }

        if (tagName == MOTION_LAYOUT) {
            // Look for layoutDescription attribute in the auto namespace
            val descAttr = element.getAttributeNodeNS(AUTO_URI, ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)

            if (descAttr == null || descAttr.value.isNullOrBlank()) {
                context.report(
                    INVALID_SCENE_FILE_REFERENCE,
                    element,
                    context.getNameLocation(element),
                    "`MotionLayout` must specify a `layoutDescription` attribute that references a valid motion scene file"
                )
                return
            }

            val value = descAttr.value.trim()
            if (!value.startsWith("@xml/")) {
                context.report(
                    INVALID_SCENE_FILE_REFERENCE,
                    element,
                    context.getValueLocation(descAttr),
                    "The `layoutDescription` attribute must reference a motion scene XML file (e.g. `@xml/scene`)"
                )
                return
            }

            // Defer the validation until afterCheckRootProject so all XML files are known
            pendingChecks.add(PendingCheck(context, element, value))
        }
    }

    override fun afterCheckRootProject(context: com.android.tools.lint.detector.api.Context) {
        for (pending in pendingChecks) {
            val value = pending.attrValue
            // value is of the form @xml/<name>
            val resourceName = value.removePrefix("@xml/").trim()
            if (resourceName.isEmpty() || resourceName !in motionSceneFiles) {
                val element = pending.element
                val xmlContext = pending.context
                val descAttr = element.getAttributeNodeNS(AUTO_URI, ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)
                val location = if (descAttr != null) {
                    xmlContext.getValueLocation(descAttr)
                } else {
                    xmlContext.getNameLocation(element)
                }
                xmlContext.report(
                    INVALID_SCENE_FILE_REFERENCE,
                    element,
                    location,
                    "The file referenced by `layoutDescription` (`$value`) could not be found or is not a valid motion scene file"
                )
            }
        }
        pendingChecks.clear()
    }
}