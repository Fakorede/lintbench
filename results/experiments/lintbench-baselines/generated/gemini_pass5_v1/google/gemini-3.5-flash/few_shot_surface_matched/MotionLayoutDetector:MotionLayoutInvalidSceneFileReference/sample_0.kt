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
import com.android.tools.lint.detector.api.XmlScanner

class MotionLayoutDetector : ResourceXmlDetector(), XmlScanner {

    private val declaredMotionScenes = mutableSetOf<String>()
    private val referencedScenes = mutableListOf<PendingReference>()

    override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean {
        return folderType == com.android.resources.ResourceFolderType.LAYOUT ||
                folderType == com.android.resources.ResourceFolderType.XML
    }

    override fun getApplicableElements(): Collection<String> {
        return listOf(
            "MotionLayout",
            "androidx.constraintlayout.motion.widget.MotionLayout",
            "MotionScene"
        )
    }

    override fun visitElement(context: XmlContext, element: org.w3c.dom.Element) {
        val tagName = element.tagName
        if (tagName == "MotionScene") {
            val fileName = context.file.nameWithoutExtension
            declaredMotionScenes.add("@xml/$fileName")
        } else if (tagName == "MotionLayout" || tagName == "androidx.constraintlayout.motion.widget.MotionLayout") {
            val layoutDescriptionAttr = element.getAttributeNodeNS(
                com.android.SdkConstants.AUTO_URI,
                "layoutDescription"
            )
            if (layoutDescriptionAttr == null || layoutDescriptionAttr.value.isEmpty()) {
                context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    "MotionLayout must specify a scene file using `app:layoutDescription`"
                )
            } else {
                val value = layoutDescriptionAttr.value
                if (!value.startsWith("@xml/")) {
                    context.report(
                        ISSUE,
                        layoutDescriptionAttr,
                        context.getValueLocation(layoutDescriptionAttr),
                        "The `layoutDescription` attribute must specify a valid motion scene file reference (e.g. `@xml/scene_file`)"
                    )
                } else {
                    referencedScenes.add(
                        PendingReference(
                            reference = value,
                            context = context,
                            element = element,
                            location = context.getValueLocation(layoutDescriptionAttr)
                        )
                    )
                }
            }
        }
    }

    override fun afterCheckRootProject(context: Context) {
        for (pending in referencedScenes) {
            if (!declaredMotionScenes.contains(pending.reference)) {
                pending.context.report(
                    ISSUE,
                    pending.element,
                    pending.location,
                    "The motion scene file `${pending.reference}` does not exist"
                )
            }
        }
    }

    private class PendingReference(
        val reference: String,
        val context: XmlContext,
        val element: org.w3c.dom.Element,
        val location: Location
    )

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "MotionLayoutInvalidSceneFileReference",
            briefDescription = "Invalid MotionLayout scene file reference",
            explanation = "A motion scene file specifies the animations used in a `MotionLayout`. " +
                    "The `layoutDescription` attribute is required to specify a valid motion scene file.",
            category = Category.CORRECTNESS,
            priority = 8,
            severity = Severity.ERROR,
            implementation = Implementation(
                MotionLayoutDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}