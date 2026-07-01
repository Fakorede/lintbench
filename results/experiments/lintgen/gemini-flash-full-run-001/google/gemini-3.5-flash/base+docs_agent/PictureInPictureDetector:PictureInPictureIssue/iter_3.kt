package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiField
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiVariable
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.visitor.AbstractUastVisitor

class PictureInPictureDetector : Detector(), SourceCodeScanner {

    override fun getApplicableMethodNames(): List<String> {
        return listOf("build")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (isPictureInPictureBuildCall(node)) {
            analyzeBuilder(context, node)
        }
    }

    private fun isPictureInPictureBuildCall(node: UCallExpression): Boolean {
        if (node.methodName != "build") return false

        // 1. Check resolved method
        val method = node.resolve()
        if (method != null) {
            val containingClass = method.containingClass
            if (containingClass?.qualifiedName == "android.app.PictureInPictureParams.Builder") {
                return true
            }
        }

        // 2. Check receiver type
        val receiverType = node.receiverType
        if (receiverType != null) {
            val canonical = receiverType.canonicalText
            if (canonical.contains("PictureInPictureParams.Builder")) {
                return true
            }
        }

        // 3. Walk the receiver chain to find any reference to PictureInPictureParams
        var curr: UExpression? = node.receiver
        while (curr is UCallExpression) {
            val currMethod = curr.resolve()
            if (currMethod != null && currMethod.containingClass?.qualifiedName == "android.app.PictureInPictureParams.Builder") {
                return true
            }
            val classRef = curr.classReference?.asSourceString()
            if (classRef != null && classRef.contains("PictureInPictureParams")) {
                return true
            }
            val currText = curr.asSourceString()
            if (currText.contains("PictureInPictureParams")) {
                return true
            }
            curr = curr.receiver
        }

        if (curr != null) {
            val currText = curr.asSourceString()
            if (currText.contains("PictureInPictureParams")) {
                return true
            }
            if (curr is UReferenceExpression) {
                val resolved = curr.resolve()
                if (resolved is PsiLocalVariable || resolved is PsiParameter || resolved is PsiField) {
                    val type = (resolved as? PsiVariable)?.type
                    if (type != null && type.canonicalText.contains("PictureInPictureParams")) {
                        return true
                    }
                }
                val varName = curr.resolvedName ?: (curr as? USimpleNameReferenceExpression)?.identifier
                if (varName != null) {
                    val enclosingMethod = getEnclosingMethod(node)
                    if (enclosingMethod != null) {
                        var foundBuilder = false
                        enclosingMethod.accept(object : AbstractUastVisitor() {
                            override fun visitVariable(variable: UVariable): Boolean {
                                if (variable.name == varName) {
                                    val typeStr = variable.type.canonicalText
                                    val initStr = variable.uastInitializer?.asSourceString()
                                    if (typeStr.contains("PictureInPictureParams") || 
                                        initStr?.contains("PictureInPictureParams") == true) {
                                        foundBuilder = true
                                    }
                                }
                                return super.visitVariable(variable)
                            }
                        })
                        if (foundBuilder) return true
                    }
                }
            }
        }

        return false
    }

    private fun analyzeBuilder(context: JavaContext, buildCall: UCallExpression) {
        val builderCalls = mutableSetOf<UCallExpression>()

        // 1. Collect all calls in the chain of the build() call itself
        var curr: UExpression? = buildCall
        while (curr is UCallExpression) {
            builderCalls.add(curr)
            curr = curr.receiver
        }

        // 2. If the root receiver of the build() call is a reference, find all other calls on it
        val rootReceiver = getRootReceiver(buildCall)
        if (rootReceiver is UReferenceExpression) {
            val resolved = rootReceiver.resolve()
            val varName = rootReceiver.resolvedName ?: (rootReceiver as? USimpleNameReferenceExpression)?.identifier
            val enclosingMethod = getEnclosingMethod(buildCall)
            if (enclosingMethod != null) {
                enclosingMethod.accept(object : AbstractUastVisitor() {
                    override fun visitCallExpression(call: UCallExpression): Boolean {
                        val callRoot = getRootReceiver(call)
                        if (callRoot is UReferenceExpression) {
                            val callRootResolved = callRoot.resolve()
                            val callRootName = callRoot.resolvedName ?: (callRoot as? USimpleNameReferenceExpression)?.identifier
                            if ((resolved != null && callRootResolved == resolved) || 
                                (varName != null && callRootName == varName)) {
                                var c: UExpression? = call
                                while (c is UCallExpression) {
                                    builderCalls.add(c)
                                    c = c.receiver
                                }
                            }
                        }
                        return super.visitCallExpression(call)
                    }
                })
            }
        }

        var hasAutoEnterEnabledTrue = false
        var hasSourceRectHint = false

        for (call in builderCalls) {
            val methodName = call.methodName
            if (methodName == "setAutoEnterEnabled") {
                val args = call.valueArguments
                if (args.isNotEmpty()) {
                    val firstArg = args[0]
                    val evaluated = firstArg.evaluate()
                    if (evaluated != false) {
                        hasAutoEnterEnabledTrue = true
                    }
                }
            } else if (methodName == "setSourceRectHint") {
                hasSourceRectHint = true
            }
        }

        val missing = mutableListOf<String>()
        if (!hasAutoEnterEnabledTrue) {
            missing.add("setAutoEnterEnabled(true)")
        }
        if (!hasSourceRectHint) {
            missing.add("setSourceRectHint(...)")
        }

        if (missing.isNotEmpty()) {
            val missingStr = missing.joinToString(" and ")
            context.report(
                ISSUE,
                buildCall,
                context.getLocation(buildCall as UElement),
                "To support smooth Picture-in-Picture transitions, call $missingStr on the PictureInPictureParams.Builder."
            )
        }
    }

    private fun getRootReceiver(call: UCallExpression): UExpression? {
        var curr: UExpression = call
        while (curr is UCallExpression) {
            val rx = curr.receiver ?: return curr
            curr = rx
        }
        return curr
    }

    private fun getEnclosingMethod(element: UElement): UMethod? {
        var parent = element.uastParent
        while (parent != null) {
            if (parent is UMethod) {
                return parent
            }
            parent = parent.uastParent
        }
        return null
    }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "PictureInPictureIssue",
            briefDescription = "Picture In Picture best practices not followed",
            explanation = """
                Starting in Android 12, the recommended approach for enabling picture-in-picture (PiP) has changed. If your app does not use the new approach, your app's transition animations will be of poor quality compared to other apps. The new approach requires calling `setAutoEnterEnabled(true)` and `setSourceRectHint(...)`.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                PictureInPictureDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}