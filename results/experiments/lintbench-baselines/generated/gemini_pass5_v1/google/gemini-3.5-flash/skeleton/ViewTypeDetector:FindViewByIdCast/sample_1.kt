package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiTypeParameterType
import com.intellij.psi.PsiClassType
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UBinaryExpressionWithType
import org.jetbrains.uast.UastBinaryExpressionWithTypeKind
import org.jetbrains.uast.UParenthesizedExpression
import org.w3c.dom.Attr

class ViewTypeDetector : ResourceXmlDetector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            ViewTypeDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE),
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "FindViewByIdCast",
            briefDescription = "Add Explicit Cast",
            explanation = """
                In Android O, the `findViewById` signature switched to using generics, which \
                means that most of the time you can leave out explicit casts and just assign \
                the result of the `findViewById` call to variables of specific view classes.

                However, due to language changes between Java 7 and 8, this change may cause \
                code to not compile without explicit casts. This lint check looks for these \
                scenarios and suggests casts to be added now such that the code will \
                continue to compile if the language level is updated to 1.8.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT
    }

    override fun getApplicableAttributes(): Collection<String>? {
        return listOf("id")
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        // No action required here for FindViewByIdCast
    }

    override fun getApplicableMethodNames(): List<String>? {
        return listOf("findViewById")
    }

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        val languageLevel = context.project.javaLanguageLevel
        if (languageLevel != null && languageLevel.isAtLeast(com.intellij.pom.java.LanguageLevel.JDK_1_8)) {
            return
        }

        val returnType = method.returnType
        if (returnType !is PsiTypeParameterType) {
            return
        }

        var parent = node.uastParent
        while (parent is UParenthesizedExpression) {
            parent = parent.uastParent
        }
        if (parent is UBinaryExpressionWithType) {
            if (parent.operationKind == UastBinaryExpressionWithTypeKind.TYPE_CAST) {
                return
            }
        }

        val type = node.getExpressionType() ?: return
        if (type is PsiClassType) {
            val resolvedClass = type.resolve() ?: return
            val evaluator = context.evaluator
            if (evaluator.inheritsFrom(resolvedClass, "android.view.View", false) &&
                resolvedClass.qualifiedName != "android.view.View"
            ) {
                val viewType = resolvedClass.name ?: "View"
                val source = node.sourcePsi?.text
                if (source != null) {
                    val fix = context.fix().replace().with("($viewType) $source").build()
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Add explicit cast to `$viewType`",
                        fix
                    )
                } else {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Add explicit cast to `$viewType`"
                    )
                }
            }
        }
    }
}