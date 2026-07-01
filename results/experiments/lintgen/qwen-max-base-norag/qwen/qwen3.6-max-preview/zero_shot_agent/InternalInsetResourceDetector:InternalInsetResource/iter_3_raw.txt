package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiField
import org.jetbrains.uast.*
import org.w3c.dom.Attr
import java.util.EnumSet

class InternalInsetResourceDetector : Detector(), Detector.UastScanner, Detector.XmlScanner {

    companion object {
        private val INSET_DIMEN_NAMES = setOf(
            "status_bar_height",
            "status_bar_height_landscape",
            "status_bar_height_portrait",
            "navigation_bar_height",
            "navigation_bar_height_landscape",
            "navigation_bar_width",
            "system_bar_height"
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "InternalInsetResource",
            briefDescription = "Using internal inset dimension resource",
            explanation = "The internal inset dimension resources are not a supported way to retrieve the relevant insets for your application. The insets are dynamic values that can change while your app is visible, and your app's window may not intersect with the system UI. To get the relevant value for your app and listen to updates, use `androidx.core.view.WindowInsetsCompat` and related APIs.",
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(InternalInsetResourceDetector::class.java, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE))
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UReferenceExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : AbstractUastVisitor() {
            override fun visitReferenceExpression(node: UReferenceExpression): Boolean {
                val name = node.referenceName ?: return false
                if (name in INSET_DIMEN_NAMES) {
                    val resolved = node.resolve()
                    if (resolved is PsiField) {
                        val containingClass = resolved.containingClass
                        if (containingClass?.qualifiedName == "android.R.dimen") {
                            context.report(
                                ISSUE,
                                node,
                                context.getLocation(node),
                                "Using internal inset dimension resource `$name`"
                            )
                        }
                    }
                }
                return false
            }
        }
    }

    override fun getApplicableAttributes(): Collection<String>? = ALL_ATTRIBUTES

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val value = attribute.value
        if (value.startsWith("@android:dimen/") || value.startsWith("@*android:dimen/")) {
            val resourceName = value.substringAfterLast('/')
            if (resourceName in INSET_DIMEN_NAMES) {
                context.report(
                    ISSUE,
                    attribute,
                    context.getLocation(attribute),
                    "Using internal inset dimension resource `$resourceName`"
                )
            }
        }
    }
}