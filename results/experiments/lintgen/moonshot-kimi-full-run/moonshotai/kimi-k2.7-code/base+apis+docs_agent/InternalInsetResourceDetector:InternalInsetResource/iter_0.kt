package com.android.tools.lint.checks

import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
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

class InternalInsetResourceDetector : Detector(), XmlScanner, SourceCodeScanner {

    companion object {
        private val INSET_DIMENS = setOf(
            "status_bar_height",
            "navigation_bar_height",
            "navigation_bar_width"
        )

        @JvmField
        val ISSUE = Issue.create(
            "InternalInsetResource",
            "Using internal inset dimension resource",
            """
                The internal inset dimension resources are not a supported way to retrieve the \
                relevant insets for your application. The insets are dynamic values that can \
                change while your app is visible, and your app's window may not intersect with \
                the system UI. To get the relevant value for your app and listen to updates, use \
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

    override fun getApplicableAttributes(): Collection<String>? = XmlScannerConstants.ALL

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value ?: return
        val url = ResourceUrl.parse(value) ?: return
        if (url.type != ResourceType.DIMEN || !url.framework) return
        val name = url.name ?: return
        if (name !in INSET_DIMENS) return

        context.report(
            ISSUE,
            attribute,
            context.getValueLocation(attribute),
            "Using internal inset dimension resource `$name`"
        )
    }

    override fun visitReference(
        context: JavaContext,
        reference: UReferenceExpression,
        referenced: PsiElement
    ) {
        if (referenced !is PsiField) return
        val cls = referenced.containingClass ?: return
        if (cls.qualifiedName != "android.R.dimen") return
        val name = referenced.name
        if (name !in INSET_DIMENS) return

        context.report(
            ISSUE,
            reference,
            context.getLocation(reference),
            "Using internal inset dimension resource `$name`"
        )
    }
}