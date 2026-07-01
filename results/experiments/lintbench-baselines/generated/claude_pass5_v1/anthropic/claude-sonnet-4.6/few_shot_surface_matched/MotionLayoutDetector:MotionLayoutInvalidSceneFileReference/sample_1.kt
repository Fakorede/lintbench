package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.DOT_XML
import com.android.SdkConstants.RES_FOLDER
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element
import java.io.File

class MotionLayoutDetector : ResourceXmlDetector(), XmlScanner {

    private val MOTION_LAYOUT = "androidx.constraintlayout.motion.widget.MotionLayout"
    private val ATTR_CONSTRAINT_LAYOUT_DESCRIPTION = "layoutDescription"

    data class PendingCheck(
        val context: XmlContext,
        val element: Element,
        val attrValue: String,
        val location: com.android.tools.lint.detector.api.Location
    )

    private val pendingChecks = mutableListOf<PendingCheck>()

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(MOTION_LAYOUT)
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val attr = element.getAttributeNodeNS(AUTO_URI, ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)
            ?: element.getAttributeNodeNS(ANDROID_URI, ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)

        if (attr == null) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "`MotionLayout` must specify a `$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` attribute"
            )
            return
        }

        val value = attr.value
        if (value.isBlank()) {
            context.report(
                ISSUE,
                attr,
                context.getValueLocation(attr),
                "`$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` must specify a valid motion scene file"
            )
            return
        }

        // Value should be in the form @xml/filename
        if (!value.startsWith("@xml/")) {
            context.report(
                ISSUE,
                attr,
                context.getValueLocation(attr),
                "`$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` must specify a valid motion scene file reference (e.g. `@xml/scene`)"
            )
            return
        }

        val fileName = value.removePrefix("@xml/")
        if (fileName.isBlank()) {
            context.report(
                ISSUE,
                attr,
                context.getValueLocation(attr),
                "`$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` must specify a valid motion scene file"
            )
            return
        }

        pendingChecks.add(
            PendingCheck(
                context,
                element,
                fileName,
                context.getValueLocation(attr)
            )
        )
    }

    override fun afterCheckRootProject(context: Context) {
        for (pending in pendingChecks) {
            val resourceDir = pending.context.file.parentFile?.parentFile ?: continue
            val xmlDir = File(resourceDir, "xml")
            val sceneFile = File(xmlDir, pending.attrValue + DOT_XML)
            if (!sceneFile.exists()) {
                pending.context.report(
                    ISSUE,
                    pending.location,
                    "`$ATTR_CONSTRAINT_LAYOUT_DESCRIPTION` must specify a valid motion scene file; " +
                        "`${pending.attrValue}` could not be found"
                )
            }
        }
        pendingChecks.clear()
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "MotionLayoutInvalidSceneFileReference",
            briefDescription = "Invalid motion scene file reference",
            explanation =
                "A motion scene file specifies the animations used in a `MotionLayout`. " +
                    "The `layoutDescription` attribute is required to specify a valid motion " +
                    "scene file.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                MotionLayoutDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}