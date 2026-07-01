package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.UElementHandler
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.intellij.psi.PsiField
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UReferenceExpression
import org.w3c.dom.Attr
import org.w3c.dom.Element
import org.w3c.dom.Node

class InternalInsetResourceDetector : Detector(), XmlScanner, Detector.UastScanner {

    companion object {
        private val INSET_DIMENS = setOf(
            "status_bar_height",
            "navigation_bar_height",
            "navigation_bar_width",
            "status_bar_height_landscape",
            "navigation_bar_height_landscape",
            "status_bar_height_portrait",
            "navigation_bar_height_portrait",
            "navigation_bar_height_car",
            "navigation_bar_width_car"
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
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
                setOf(Scope.JAVA_FILE_SCOPE, Scope.RESOURCE_FILE_SCOPE)
            )
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.VALUES || folderType == ResourceFolderType.LAYOUT
    }

    override fun getApplicableAttributes(): Collection<String>? = null

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        checkXmlValue(context, attribute.value, attribute)
    }

    override fun getApplicableElements(): Collection<String>? = null

    override fun visitElement(context: XmlContext, element: Element) {
        val text = element.textContent?.trim() ?: return
        checkXmlValue(context, text, element)
    }

    private fun checkXmlValue(context: XmlContext, value: String, node: Node) {
        if (value.startsWith("@android:dimen/") || value.startsWith("@*android:dimen/")) {
            val name = value.substringAfterLast('/')
            if (name in INSET_DIMENS) {
                context.report(
                    ISSUE,
                    context.getLocation(node),
                    "Use `WindowInsetsCompat` instead of internal inset dimension resources"
                )
            }
        }
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? {
        return listOf(UReferenceExpression::class.java, UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler? {
        return object : UElementHandler() {
            override fun visitReferenceExpression(node: UReferenceExpression) {
                val resolved = node.resolve()
                if (resolved is PsiField) {
                    val qName = resolved.containingClass?.qualifiedName
                    if (qName == "com.android.internal.R.dimen" || qName == "android.R.dimen") {
                        val name = resolved.name
                        if (name in INSET_DIMENS) {
                            context.report(
                                ISSUE,
                                node,
                                context.getLocation(node),
                                "Use `WindowInsetsCompat` instead of internal inset dimension resources"
                            )
                        }
                    }
                }
            }

            override fun visitCallExpression(node: UCallExpression) {
                val method = node.resolve()
                if (method?.name == "getIdentifier" && method.containingClass?.qualifiedName == "android.content.res.Resources") {
                    val args = node.valueArguments
                    if (args.size >= 3) {
                        val nameArg = args[0]
                        val typeArg = args[1]
                        val pkgArg = args[2]
                        if (nameArg is ULiteralExpression && typeArg is ULiteralExpression && pkgArg is ULiteralExpression) {
                            val name = nameArg.value as? String
                            val type = typeArg.value as? String
                            val pkg = pkgArg.value as? String
                            if (type == "dimen" && (pkg == "android" || pkg == "com.android.internal") && name in INSET_DIMENS) {
                                context.report(
                                    ISSUE,
                                    node,
                                    context.getLocation(node),
                                    "Use `WindowInsetsCompat` instead of internal inset dimension resources"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}