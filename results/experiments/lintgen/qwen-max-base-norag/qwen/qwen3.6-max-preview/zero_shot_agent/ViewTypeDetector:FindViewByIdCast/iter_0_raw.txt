package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.*

class ViewTypeDetector : Detector(), UastScanner {
    override fun getApplicableMethodNames(): List<String> = listOf("findViewById")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (method.parameterList.parametersCount != 1) return
        val containingClass = method.containingClass ?: return
        if (!context.evaluator.extendsClass(containingClass, "android.app.Activity", false) &&
            !context.evaluator.extendsClass(containingClass, "android.view.View", false) &&
            !context.evaluator.extendsClass(containingClass, "android.app.Dialog", false) &&
            !context.evaluator.extendsClass(containingClass, "android.app.Fragment", false) &&
            !context.evaluator.extendsClass(containingClass, "androidx.fragment.app.Fragment", false)) {
            return
        }

        val parent = node.uastParent
        if (parent is UCastExpression) return

        var needsCast = false
        when (parent) {
            is UCallExpression -> {
                val argIndex = parent.valueArguments.indexOf(node)
                if (argIndex != -1) {
                    val resolved = parent.resolve()
                    if (resolved != null) {
                        val params = resolved.parameterList.parameters
                        if (argIndex < params.size) {
                            val type = params[argIndex].type.canonicalText
                            if (type == "android.view.View" || type == "java.lang.Object") {
                                needsCast = true
                            }
                        }
                    }
                }
            }
            is UReturnExpression -> {
                val methodParent = parent.getParentOfType(UMethod::class.java, true)
                val returnType = methodParent?.returnType?.canonicalText
                if (returnType == "android.view.View" || returnType == "java.lang.Object") {
                    needsCast = true
                }
            }
            is ULocalVariable -> {
                val varType = parent.type?.canonicalText
                if (varType == "android.view.View" || varType == "java.lang.Object") {
                    needsCast = true
                }
            }
            is UField -> {
                val fieldType = parent.type?.canonicalText
                if (fieldType == "android.view.View" || fieldType == "java.lang.Object") {
                    needsCast = true
                }
            }
        }

        if (needsCast) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Add explicit cast to `findViewById` to ensure compatibility with Java 8 type inference"
            )
        }
    }

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            "FindViewByIdCast",
            "Add Explicit Cast",
            "In Android O, the `findViewById` signature switched to using generics, which " +
            "means that most of the time you can leave out explicit casts and just assign " +
            "the result of the `findViewById` call to variables of specific view classes.\n\n" +
            "However, due to language changes between Java 7 and 8, this change may cause " +
            "code to not compile without explicit casts. This lint check looks for these " +
            "scenarios and suggests casts to be added now such that the code will " +
            "continue to compile if the language level is updated to 1.8.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            Implementation(ViewTypeDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
    }
}