package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Attr
import org.w3c.dom.Element

class MotionLayoutDetector : Detector(), XmlScanner {

    override fun getApplicableElements(): Collection<String>? {
        return null
    }

    override fun visitElement(context: XmlContext, element: Element) {
        val tagName = element.tagName
        if (tagName != "MotionLayout" && !tagName.endsWith(".MotionLayout")) {
            return
        }

        var attr: Attr? = null
        val attributes = element.attributes
        if (attributes != null) {
            for (i in 0 until attributes.length) {
                val item = attributes.item(i) as? Attr ?: continue
                val localName = item.localName ?: item.name.substringAfter(':')
                if (localName == SdkConstants.ATTR_CONSTRAINT_LAYOUT_DESCRIPTION || localName == "layoutDescription") {
                    attr = item
                    break
                }
            }
        }

        if (attr == null) {
            context.report(
                ISSUE,
                element,
                context.getNameLocation(element),
                "MotionLayout requires a `layoutDescription` attribute"
            )
        } else {
            val value = attr.value
            if (!value.startsWith("@xml/") || value.length <= 5) {
                context.report(
                    ISSUE,
                    attr,
                    context.getValueLocation(attr),
                    "The `layoutDescription` attribute must specify a valid motion scene file (e.g., `@xml/scene`)"
                )
            } else {
                val sceneName = value.substring(5)
                var fileExists = false

                val currentResDir = context.file.parentFile?.parentFile
                if (currentResDir != null && currentResDir.name == "res") {
                    val xmlFolder = java.io.File(currentResDir, "xml")
                    if (xmlFolder.isDirectory) {
                        val sceneFile = java.io.File(xmlFolder, "$sceneName.xml")
                        if (sceneFile.exists()) {
                            fileExists = true
                        }
                    }
                }

                if (!fileExists) {
                    val resourceFolders = context.project.resourceFolders
                    for (resFolder in resourceFolders) {
                        val xmlFolder = java.io.File(resFolder, "xml")
                        if (xmlFolder.isDirectory) {
                            val sceneFile = java.io.File(xmlFolder, "$sceneName.xml")
                            if (sceneFile.exists()) {
                                fileExists = true
                                break
                            }
                        }
                    }
                }

                if (!fileExists) {
                    context.report(
                        ISSUE,
                        attr,
                        context.getValueLocation(attr),
                        "The associated motion scene file `@xml/$sceneName` does not exist"
                    )
                }
            }
        }
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "MotionLayoutInvalidSceneFileReference",
            briefDescription = "MotionLayout layoutDescription must specify a scene file",
            explanation = """
                A motion scene file specifies the animations used in a `MotionLayout`. \
                The `layoutDescription` attribute is required to specify a valid motion scene file (typically in `@xml/`).
                """,
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.ERROR,
            implementation = Implementation(
                MotionLayoutDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            )
        )
    }
}