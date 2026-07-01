package com.android.tools.lint.checks

import com.android.resources.ResourceType
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.interprocedural.CallGraphResult
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.visitor.AbstractUastVisitor
import org.w3c.dom.Node

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ChromeOsOnConfigurationChanged",
            briefDescription = "Poor performance with APIs inside `onConfigurationChanged()`",
            explanation = """
                When users resize the Android emulator in Android 13 and Chrome OS, an `onConfigurationChanged()` \
                API call occurs. If your `onConfigurationChanged()` method contains any code that can cause a redraw, \
                your app might take a performance hit on large screens. To fix the issue, ensure your \
                `onConfigurationChanged()` method does not contain any calls to UI redraw logic for specific elements.
            """,
            category = Category.PERFORMANCE,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                ChromeOsSourceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UMethod::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (node.name == "onConfigurationChanged" && node.uastParameters.size == 1) {
                    val firstParam = node.uastParameters[0]
                    val typeName = firstParam.type.canonicalText
                    if (typeName == "android.content.res.Configuration" || 
                        typeName == "Configuration" || 
                        typeName.endsWith(".Configuration") ||
                        context.evaluator.typeMatches(firstParam.type, "android.content.res.Configuration")
                    ) {
                        node.accept(ConfigurationChangedVisitor(context))
                    }
                }
            }
        }
    }

    private class ConfigurationChangedVisitor(private val context: JavaContext) : AbstractUastVisitor() {
        override fun visitCallExpression(node: UCallExpression): Boolean {
            val methodName = node.methodName ?: return super.visitCallExpression(node)
            if (isForbiddenMethod(methodName, node)) {
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Avoid calling `$methodName` inside `onConfigurationChanged` as it can cause performance hits during resizes."
                )
            }
            return super.visitCallExpression(node)
        }

        private fun isForbiddenMethod(name: String, node: UCallExpression): Boolean {
            val forbiddenNames = setOf("recreate", "setContentView", "requestLayout", "invalidate", "inflate", "finish")
            if (name !in forbiddenNames) return false

            val method = node.resolve() ?: return true
            val containingClass = method.containingClass ?: return true

            return when (name) {
                "recreate", "setContentView", "finish" -> {
                    isSubclassOf(containingClass, "android.app.Activity")
                }
                "requestLayout", "invalidate" -> {
                    isSubclassOf(containingClass, "android.view.View")
                }
                "inflate" -> {
                    isSubclassOf(containingClass, "android.view.LayoutInflater") || 
                    isSubclassOf(containingClass, "android.view.View")
                }
                else -> false
            }
        }

        private fun isSubclassOf(cls: PsiClass, targetSuper: String): Boolean {
            if (context.evaluator.inheritsFrom(cls, targetSuper, false)) {
                return true
            }
            val targetSimple = targetSuper.substringAfterLast('.')
            var current: PsiClass? = cls
            val visited = mutableSetOf<String>()
            while (current != null) {
                val qName = current.qualifiedName
                if (qName != null) {
                    if (qName == targetSuper || qName.endsWith(".$targetSimple")) {
                        return true
                    }
                    if (!visited.add(qName)) {
                        break
                    }
                } else {
                    val name = current.name
                    if (name != null) {
                        if (name == targetSimple) {
                            return true
                        }
                        if (!visited.add(name)) {
                            break
                        }
                    }
                }
                current = current.superClass
            }
            return false
        }
    }
}