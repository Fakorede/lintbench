package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiField
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UReferenceExpression
import org.w3c.dom.Element
import java.util.EnumSet

class SelectedPhotoAccessDetector : Detector(), XmlScanner, SourceCodeScanner {
    companion object {
        val ISSUE = Issue.create(
            id = "SelectedPhotoAccess",
            briefDescription = "Behavior change when requesting photo library access",
            explanation = """
                Selected Photo Access is a new ability for users to share partial access to their photo library \
                when apps request access to their device storage on Android 14+.

                Instead of letting the system manage the selection lifecycle, we recommend you adapt your app \
                to handle partial access to the photo library.

                Reference documentation:
                - https://developer.android.com/about/versions/14/changes/partial-photo-video-access
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                SelectedPhotoAccessDetector::class.java,
                EnumSet.of(Scope.MANIFEST_SCOPE, Scope.JAVA_FILE_SCOPE)
            )
        )

        private const val READ_MEDIA_IMAGES = "android.permission.READ_MEDIA_IMAGES"
        private const val READ_MEDIA_VIDEO = "android.permission.READ_MEDIA_VIDEO"
    }

    override fun getApplicableElements(): Collection<String>? = listOf("uses-permission")

    override fun visitElement(context: XmlContext, element: Element) {
        val name = element.getAttributeNS(ANDROID_URI, "name")
        if (name == READ_MEDIA_IMAGES || name == READ_MEDIA_VIDEO) {
            context.report(
                ISSUE,
                element,
                context.getLocation(element),
                "Requesting `$name` triggers partial photo/video access on Android 14+. " +
                "Consider handling `READ_MEDIA_VISUAL_USER_SELECTED` or adapting to partial access."
            )
        }
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? = listOf(UReferenceExpression::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitReferenceExpression(node: UReferenceExpression) {
                val resolved = node.resolve()
                if (resolved is PsiField) {
                    val qName = resolved.qualifiedName
                    if (qName == "android.Manifest.permission.READ_MEDIA_IMAGES" ||
                        qName == "android.Manifest.permission.READ_MEDIA_VIDEO") {
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Requesting `${node.asSourceString()}` triggers partial photo/video access on Android 14+. " +
                            "Consider handling `READ_MEDIA_VISUAL_USER_SELECTED` or adapting to partial access."
                        )
                    }
                }
            }
        }
    }
}