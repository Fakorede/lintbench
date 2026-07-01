package com.android.tools.lint.checks

import com.android.ide.common.resources.ResourceUrl
import com.android.resources.ResourceType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScannerConstants
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import org.jetbrains.uast.UReferenceExpression
import org.w3c.dom.Attr

class InternalInsetResourceDetector : ResourceXmlDetector(), SourceCodeScanner {

    override fun applicableAttributes(): Collection<String>? = XmlScannerConstants.ALL

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value ?: return
        val url = ResourceUrl.parse(value) ?: return
        if (url.type == ResourceType.DIMEN && url.packageName == "android" && url.name in INSET_RESOURCES) {
            context.report(ISSUE, attribute, context.getValueLocation(attribute), MESSAGE)
        }
    }

    override fun getApplicableReferenceNames(): List<String>? = INSET_RESOURCES.toList()

    override fun visitReference(context: JavaContext, reference: UReferenceExpression, referenced: PsiElement?) {
        if (referenced == null) return
        val member = referenced as? PsiMember ?: return
        val containingClass = member.containingClass ?: return
        if (containingClass.qualifiedName == "android.R.dimen") {
            context.report(ISSUE, reference, context.getLocation(reference), MESSAGE)
        }
    }

    companion object {
        private const val MESSAGE = "Using internal inset dimension resource"

        private val INSET_RESOURCES = setOf(
            "status_bar_height",
            "status_bar_height_landscape",
            "status_bar_height_portrait",
            "navigation_bar_height",
            "navigation_bar_height_landscape",
            "navigation_bar_height_portrait",
            "navigation_bar_width",
            "navigation_bar_width_landscape",
            "navigation_bar_width_portrait"
        )

        @JvmField
        val ISSUE = Issue.create(
            "InternalInsetResource",
            MESSAGE,
            """
                The internal inset dimension resources are not a supported way to
                retrieve the relevant insets for your application. The insets are
                dynamic values that can change while your app is visible, and your
                app's window may not intersect with the system UI. To get the relevant
                value for your app and listen to updates, use
                `androidx.core.view.WindowInsetsCompat` and related APIs.
            """.trimIndent(),
            Category.CORRECTNESS,
            4,
            Severity.WARNING,
            Implementation(
                InternalInsetResourceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}