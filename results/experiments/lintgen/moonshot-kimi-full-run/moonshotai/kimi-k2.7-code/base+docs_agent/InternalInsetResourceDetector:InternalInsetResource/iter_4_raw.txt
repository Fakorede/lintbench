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
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import org.jetbrains.uast.UReferenceExpression
import org.w3c.dom.Attr
import java.util.EnumSet
import java.util.regex.Pattern

class InternalInsetResourceDetector : Detector(), XmlScanner, SourceCodeScanner {

    override fun getApplicableAttributes(): Collection<String>? = XmlScannerConstants.ALL

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value ?: return
        val matcher = RESOURCE_PATTERN.matcher(value)
        while (matcher.find()) {
            val name = matcher.group(1) ?: continue
            if (name in INSET_RESOURCES) {
                report(context, attribute)
            }
        }
    }

    override fun getApplicableReferenceNames(): List<String> = INSET_RESOURCES

    override fun visitReference(
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: PsiElement
    ) {
        if (referenced !is PsiField) return
        val containingClass: PsiClass = referenced.containingClass ?: return
        val qualifiedName = containingClass.qualifiedName ?: return
        if (qualifiedName.endsWith(".R\$dimen") || qualifiedName == "android.R\$dimen") {
            report(context, reference)
        }
    }

    private fun report(context: XmlContext, attribute: Attr) {
        context.report(
            ISSUE,
            attribute,
            context.getValueLocation(attribute),
            "Using internal inset dimension resource"
        )
    }

    private fun report(context: JavaContext, reference: UReferenceExpression) {
        context.report(
            ISSUE,
            reference,
            context.getLocation(reference),
            "Using internal inset dimension resource"
        )
    }

    companion object {
        private val INSET_RESOURCES = listOf(
            "status_bar_height",
            "status_bar_height_default",
            "status_bar_height_portrait",
            "status_bar_height_landscape",
            "navigation_bar_height",
            "navigation_bar_height_default",
            "navigation_bar_height_portrait",
            "navigation_bar_height_landscape",
            "navigation_bar_width"
        )

        private val RESOURCE_PATTERN =
            Pattern.compile("@\\*?(?:android:)?dimen/(\\w+)")

        val ISSUE = Issue.create(
            id = "InternalInsetResource",
            briefDescription = "Using internal inset dimension resource",
            explanation = """
                The internal inset dimension resources are not a supported way to retrieve the relevant insets for your application. The insets are dynamic values that can change while your app is visible, and your app's window may not intersect with the system UI. To get the relevant value for your app and listen to updates, use `androidx.core.view.WindowInsetsCompat` and related APIs.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                InternalInsetResourceDetector::class.java,
                EnumSet.of(Scope.RESOURCE_FILE, Scope.JAVA_FILE)
            )
        )
    }
}