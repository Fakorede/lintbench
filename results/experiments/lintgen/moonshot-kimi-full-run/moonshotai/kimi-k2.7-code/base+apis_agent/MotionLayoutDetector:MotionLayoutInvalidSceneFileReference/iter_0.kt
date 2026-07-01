package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr
import java.io.File

class MotionLayoutDetector : Detector(), Detector.XmlScanner {

    override fun getApplicableAttributes(): Collection<String>? {
        return listOf(SdkConstants.ATTR_CONSTRAINT_LAYOUT_DESCRIPTION)
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        if (SdkConstants.TOOLS_URI == attribute.namespaceURI) {
            return
        }

        val element = attribute.ownerElement ?: return
        val tag = element.tagName
        if (!tag.endsWith("MotionLayout")) {
            return
        }

        val value = attribute.value ?: ""
        if (value.isBlank()) {
            reportInvalid(context, attribute, "The `layoutDescription` attribute must specify a motion scene file.")
            return
        }

        if (!value.startsWith("@xml/")) {
            reportInvalid(
                context,
                attribute,
                "The `layoutDescription` attribute must reference a motion scene file with `@xml/...`."
            )
            return
        }

        val sceneName = value.substringAfter("@xml/")
        if (sceneName.isBlank() || sceneName.contains("/")) {
            reportInvalid(context, attribute, "The `layoutDescription` attribute must reference a valid motion scene file.")
            return
        }

        val exists = context.project.resourceFolders.any { resFolder ->
            File(resFolder, "xml/$sceneName.xml").exists()
        }

        if (!exists) {
            reportInvalid(
                context,
                attribute,
                "The motion scene file `res/xml/$sceneName.xml` could not be found."
            )
        }
    }

    private fun reportInvalid(context: XmlContext, attribute: Attr, message: String) {
        context.report(
            ISSUE,
            attribute,
            context.getValueLocation(attribute),
            message
        )
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "MotionLayoutInvalidSceneFileReference",
            briefDescription = "Invalid MotionLayout scene file reference",
            explanation = """
                A `MotionLayout` requires a motion scene file that defines its animations. \
                The `app:layoutDescription` attribute must reference an existing XML resource \
                in `res/xml/`.
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
}