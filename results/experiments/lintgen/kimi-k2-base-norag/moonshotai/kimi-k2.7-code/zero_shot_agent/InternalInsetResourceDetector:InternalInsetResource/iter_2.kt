package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.tools.lint.detector.api.XmlScannerConstants
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import org.jetbrains.uast.UReferenceExpression
import org.w3c.dom.Attr

class InternalInsetResourceDetector : Detector(), SourceCodeScanner, XmlScanner {

    override fun getApplicableFiles(): Collection<Scope> =
        listOf(Scope.RESOURCE_FILE, Scope.JAVA_FILE)

    override fun getApplicableAttributes(): Collection<String>? = XmlScannerConstants.ALL

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value ?: return
        if (!value.startsWith(PREFIX)) return
        val name = value.substring(PREFIX.length)
        if (name in INSET_RESOURCES) {
            context.report(ISSUE, attribute, context.getValueLocation(attribute), MESSAGE)
        }
    }

    override fun getApplicableReferenceNames(): List<String> = INSET_RESOURCES.toList()

    override fun visitReference(
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: PsiElement
    ) {
        val field = referenced as? PsiField ?: return
        val containingClass = field.containingClass ?: return
        if (containingClass.qualifiedName == "android.R.dimen") {
            context.report(ISSUE, reference, context.getLocation(reference), MESSAGE)
        }
    }

    companion object {
        private const val MESSAGE = "Using internal inset dimension resource"
        private const val PREFIX = "@android:dimen/"

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
            id = "InternalInsetResource",
            briefDescription = MESSAGE,
            explanation = """
                The internal inset dimension resources are not a supported way to
                retrieve the relevant insets for your application. The insets are
                dynamic values that can change while your app is visible, and your
                app's window may not intersect with the system UI. To get the relevant
                value for your app and listen to updates, use
                `androidx.core.view.WindowInsetsCompat` and related APIs.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = Implementation(
                InternalInsetResourceDetector::class.java,
                Scope.RESOURCE_FILE_SCOPE,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}