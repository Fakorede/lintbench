package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.isKotlin
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import com.intellij.psi.util.InheritanceUtil
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UTypeCastExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.getParentOfType

class ViewTypeDetector : Detector(), SourceCodeScanner {

    companion object {
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
            priority = 5,
            severity = Severity.WARNING,
            androidSpecific = true,
            implementation = Implementation(
                ViewTypeDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf("findViewById")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (isKotlin(node.sourcePsi)) {
            return
        }

        val languageLevel = context.project.languageLevel
        if (languageLevel != null && languageLevel.isAtLeast(LanguageLevel.JDK_1_8)) {
            return
        }

        if (!method.hasTypeParameters()) {
            return
        }

        val expectedType = getExpectedType(node) ?: return
        val typeName = expectedType.canonicalText
        if (typeName != "android.view.View" && InheritanceUtil.isInheritor(expectedType, "android.view.View")) {
            val castType = expectedType.presentableText
            val message = "Add explicit cast to `$castType` so that the code will continue to compile if the language level is updated to 1.8"
            
            val sourceText = node.sourcePsi?.text
            val fix = if (sourceText != null) {
                fix()
                    .name("Cast to $castType")
                    .replace()
                    .with("(($castType) $sourceText)")
                    .build()
            } else {
                null
            }

            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                message,
                fix
            )
        }
    }

    private fun getExpectedType(node: UExpression): PsiType? {
        var current: UElement = node
        while (true) {
            val parent = current.uastParent ?: return null
            if (parent is UParenthesizedExpression) {
                current = parent
                continue
            }
            if (parent is UTypeCastExpression) {
                return null
            }
            if (parent is UVariable) {
                return null
            }
            if (parent is UBinaryExpression) {
                if (parent.operator == UastBinaryOperator.ASSIGN) {
                    return null
                }
            }
            if (parent is UQualifiedReferenceExpression) {
                if (parent.receiver == current) {
                    val selector = parent.selector
                    if (selector is UCallExpression) {
                        val method = selector.resolve()
                        if (method != null) {
                            val containingClass = method.containingClass
                            if (containingClass != null) {
                                return JavaPsiFacade.getElementFactory(containingClass.project)
                                    .createType(containingClass)
                            }
                        }
                    } else if (selector is USimpleNameReferenceExpression) {
                        val resolved = selector.resolve()
                        if (resolved is PsiField) {
                            val containingClass = resolved.containingClass
                            if (containingClass != null) {
                                return JavaPsiFacade.getElementFactory(containingClass.project)
                                    .createType(containingClass)
                            }
                        }
                    }
                }
                return null
            }
            if (parent is UCallExpression) {
                val method = parent.resolve() ?: return null
                val arguments = parent.valueArguments
                val index = arguments.indexOf(current as? UExpression ?: current)
                if (index >= 0) {
                    val parameters = method.parameterList.parameters
                    if (index < parameters.size) {
                        return parameters[index].type
                    } else if (method.isVarArgs && parameters.isNotEmpty()) {
                        val lastParam = parameters.last()
                        val lastParamType = lastParam.type
                        if (lastParamType is PsiArrayType) {
                            return lastParamType.componentType
                        }
                        return lastParamType
                    }
                }
                return null
            }
            if (parent is UReturnExpression) {
                val method = parent.getParentOfType<UMethod>()
                return method?.returnType
            }
            return null
        }
    }
}