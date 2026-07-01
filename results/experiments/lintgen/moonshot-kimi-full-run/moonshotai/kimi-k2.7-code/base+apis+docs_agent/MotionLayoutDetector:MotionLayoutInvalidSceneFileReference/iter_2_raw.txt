package com.android.tools.lint.checks

import com.android.SdkConstants
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Attr
import org.w3c.dom.Element

class MotionLayoutDetector : Detector(), XmlScanner {

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.LAYOUT

    override fun getApplicableElements(): Collection<String>? =
        listOf(
            SdkConstants.CLASS_MOTION_LAYOUT,
            MOTION_LAYOUT_TAG
        )

    override fun visitElement(context: XmlContext, element: Element) {
        val attr: Attr? = element.getAttributeNodeNS(
            SdkConstants.AUTO_URI,
            SdkConstants.ATTR_CONSTRAINT_LAYOUT_DESCRIPTION
        )

        val message =
            "${SdkConstants.ATTR_CONSTRAINT_LAYOUT_DESCRIPTION} must specify a scene file"

        val fix = LintFix.create()
            .name("Learn more")
            .url(URL)
            .setData(KEY_URL, URL)
            .build()

        if (attr == null) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                message,
                fix
            )
            return
        }

        val value = attr.value
        if (value.isBlank() || !value.startsWith("@xml/")) {
            context.report(
                ISSUE,
                attr,
                context.getValueLocation(attr),
                message,
                fix
            )
        }
    }

    companion object {
        private const val MOTION_LAYOUT_TAG = "MotionLayout"
        const val KEY_URL = "url"
        private const val URL = "https://developer.android.com/training/constraint-layout/motionlayout"

        @JvmField
        val ISSUE = Issue.create(
            "MotionLayoutInvalidSceneFileReference",
            "Invalid MotionLayout scene file reference",
            """
                A `MotionLayout` requires the `app:${SdkConstants.ATTR_CONSTRAINT_LAYOUT_DESCRIPTION}` attribute to reference a valid motion scene XML resource (`@xml/...`).
            """.trimIndent(),
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            Implementation(
                MotionLayoutDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE
            ),
            URL
        )
    }
}