package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TAG_USES_PERMISSION
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UElement
import org.w3c.dom.Element
import java.util.EnumSet

class SelectedPhotoAccessDetector : Detector(), Detector.XmlScanner, Detector.UastScanner {

    companion object {
        private const val PERMISSION_READ_MEDIA_IMAGES = "android.permission.READ_MEDIA_IMAGES"
        private const val PERMISSION_READ_MEDIA_VIDEO = "android.permission.READ_MEDIA_VIDEO"

        val ISSUE: Issue = Issue.create(
            id = "SelectedPhotoAccess",
            briefDescription = "Behavior change when requesting photo library access",
            explanation = """
                Selected Photo Access is a new ability for users to share partial access to their photo library \
                when apps request access to their device storage on Android 14+.

                Instead of letting the system manage the selection lifecycle, we recommend you adapt your app \
                to handle partial access to the photo library.

                Reference documentation:
                https://developer.android.com/about/versions/14/changes/partial-photo-video-access
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                SelectedPhotoAccessDetector::class.java,
                EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE)
            )
        )
    }

    override fun getApplicableElements(): Collection<String>? = listOf(TAG_USES_PERMISSION)

    override fun visitElement(context: XmlContext, element: Element) {
        val targetSdk = context.project.targetSdkVersion?.apiLevel ?: return
        if (targetSdk < 34) return

        val permission = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
        if (permission == PERMISSION_READ_MEDIA_IMAGES || permission == PERMISSION_READ_MEDIA_VIDEO) {
            context.report(
                ISSUE,
                context.getLocation(element),
                "When targeting Android 14+, requesting `$permission` may result in partial photo/video access. " +
                        "Ensure your app handles selected photo access appropriately."
            )
        }
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(ULiteralExpression::class.java)

    override fun visitLiteral(context: JavaContext, node: ULiteralExpression) {
        val targetSdk = context.project.targetSdkVersion?.apiLevel ?: return
        if (targetSdk < 34) return

        val value = node.value as? String ?: return
        if (value == PERMISSION_READ_MEDIA_IMAGES || value == PERMISSION_READ_MEDIA_VIDEO) {
            context.report(
                ISSUE,
                context.getLocation(node),
                "When targeting Android 14+, requesting `$value` may result in partial photo/video access. " +
                        "Ensure your app handles selected photo access appropriately."
            )
        }
    }
}