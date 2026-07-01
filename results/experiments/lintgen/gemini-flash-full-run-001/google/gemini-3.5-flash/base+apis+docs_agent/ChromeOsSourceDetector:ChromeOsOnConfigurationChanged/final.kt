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
import org.w3c.dom.Node

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String>? {
        return listOf("recreate", "finish", "setContentView", "requestLayout", "invalidate")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        var current = node.uastParent
        var containingMethod: UMethod? = null
        while (current != null) {
            if (current is UMethod) {
                containingMethod = current
                break
            }
            current = current.uastParent
        }
        if (containingMethod == null) return
        if (containingMethod.name != "onConfigurationChanged") return
        val parameters = containingMethod.uastParameters
        if (parameters.size != 1) return
        val paramType = parameters[0].type.canonicalText
        if (!paramType.endsWith("Configuration")) return

        val methodName = node.methodName ?: return
        val containingClass = containingMethod.javaPsi.containingClass

        val message = when (methodName) {
            "recreate" -> {
                if (isActivityCall(context, node, method, containingClass)) {
                    "Avoid calling `recreate()` inside `onConfigurationChanged()` as it triggers expensive activity recreation."
                } else null
            }
            "finish" -> {
                if (isActivityCall(context, node, method, containingClass)) {
                    "Avoid calling `finish()` inside `onConfigurationChanged()` as it triggers expensive activity recreation."
                } else null
            }
            "setContentView" -> {
                if (isActivityOrWindowCall(context, node, method, containingClass)) {
                    "Avoid calling `setContentView()` inside `onConfigurationChanged()` as it triggers expensive layout inflation and redraw."
                } else null
            }
            "requestLayout" -> {
                if (isViewCall(context, node, method, containingClass)) {
                    "Avoid calling `requestLayout()` inside `onConfigurationChanged()` as it triggers a full layout pass."
                } else null
            }
            "invalidate" -> {
                if (isViewCall(context, node, method, containingClass)) {
                    "Avoid calling `invalidate()` inside `onConfigurationChanged()` as it triggers unnecessary redraws."
                } else null
            }
            else -> null
        }

        if (message != null) {
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                message
            )
        }
    }

    private fun isActivityCall(context: JavaContext, node: UCallExpression, method: PsiMethod, containingClass: PsiClass?): Boolean {
        val evaluator = context.evaluator
        if (evaluator.isMemberInSubClassOf(method, "android.app.Activity", false)) {
            return true
        }
        if (node.receiver == null && containingClass != null) {
            if (evaluator.inheritsFrom(containingClass, "android.app.Activity", false) || 
                containingClass.qualifiedName?.endsWith("Activity") == true) {
                return true
            }
        }
        val receiverType = node.receiverType
        if (receiverType != null) {
            val receiverClass = evaluator.getTypeClass(receiverType)
            if (receiverClass != null && evaluator.inheritsFrom(receiverClass, "android.app.Activity", false)) {
                return true
            }
            if (receiverType.canonicalText.endsWith("Activity")) {
                return true
            }
        }
        return false
    }

    private fun isActivityOrWindowCall(context: JavaContext, node: UCallExpression, method: PsiMethod, containingClass: PsiClass?): Boolean {
        val evaluator = context.evaluator
        if (evaluator.isMemberInSubClassOf(method, "android.app.Activity", false) ||
            evaluator.isMemberInSubClassOf(method, "android.view.Window", false)) {
            return true
        }
        if (node.receiver == null && containingClass != null) {
            if (evaluator.inheritsFrom(containingClass, "android.app.Activity", false) || 
                evaluator.inheritsFrom(containingClass, "android.view.Window", false) ||
                containingClass.qualifiedName?.endsWith("Activity") == true ||
                containingClass.qualifiedName?.endsWith("Window") == true) {
                return true
            }
        }
        val receiverType = node.receiverType
        if (receiverType != null) {
            val receiverClass = evaluator.getTypeClass(receiverType)
            if (receiverClass != null && (evaluator.inheritsFrom(receiverClass, "android.app.Activity", false) ||
                                          evaluator.inheritsFrom(receiverClass, "android.view.Window", false))) {
                return true
            }
            val fqName = receiverType.canonicalText
            if (fqName.endsWith("Activity") || fqName.endsWith("Window")) {
                return true
            }
        }
        return false
    }

    private fun isViewCall(context: JavaContext, node: UCallExpression, method: PsiMethod, containingClass: PsiClass?): Boolean {
        val evaluator = context.evaluator
        if (evaluator.isMemberInSubClassOf(method, "android.view.View", false)) {
            return true
        }
        if (node.receiver == null && containingClass != null) {
            if (evaluator.inheritsFrom(containingClass, "android.view.View", false) || 
                containingClass.qualifiedName?.endsWith("View") == true) {
                return true
            }
        }
        val receiverType = node.receiverType
        if (receiverType != null) {
            val receiverClass = evaluator.getTypeClass(receiverType)
            if (receiverClass != null && evaluator.inheritsFrom(receiverClass, "android.view.View", false)) {
                return true
            }
            if (receiverType.canonicalText.endsWith("View")) {
                return true
            }
        }
        return false
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "ChromeOsOnConfigurationChanged",
            briefDescription = "Poor performance with APIs inside `onConfigurationChanged()`",
            explanation = """
                When users resize the Android emulator in Android 13 and Chrome OS, an `onConfigurationChanged()` 
                API call occurs. If your `onConfigurationChanged()` method contains any code that can cause a redraw, 
                your app might take a performance hit on large screens. To fix the issue, ensure your 
                `onConfigurationChanged()` method does not contain any calls to UI redraw logic for specific elements.
            """.trimIndent(),
            category = Category.PERFORMANCE,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                ChromeOsSourceDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}