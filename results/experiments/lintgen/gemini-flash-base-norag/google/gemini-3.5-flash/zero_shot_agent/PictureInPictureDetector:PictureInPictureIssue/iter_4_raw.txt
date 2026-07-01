package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiVariable
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.visitor.AbstractUastVisitor

class PictureInPictureDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UCallExpression::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                if (isPictureInPictureBuildCall(node)) {
                    checkBuilder(context, node)
                }
            }
        }
    }

    private fun isPictureInPictureBuildCall(node: UCallExpression): Boolean {
        if (node.methodName != "build") return false
        
        // 1. Check containing class of the method
        val method = node.resolve()
        if (method != null) {
            val containingClass = method.containingClass?.qualifiedName
            if (containingClass == "android.app.PictureInPictureParams.Builder") {
                return true
            }
        }
        
        // 2. Check return type of the build() call
        val returnType = node.returnType?.canonicalText
        if (returnType != null && returnType.contains("PictureInPictureParams")) {
            return true
        }
        
        // 3. Check receiver type
        val receiverType = node.receiverType?.canonicalText
        if (receiverType != null && receiverType.contains("PictureInPictureParams")) {
            return true
        }
        
        // 4. Check receiver expression text
        val receiver = node.receiver
        if (receiver != null) {
            val receiverText = receiver.asSourceString()
            if (receiverText.contains("PictureInPictureParams")) {
                return true
            }
            
            // If the receiver is a local variable/field/parameter, resolve it
            if (receiver is UReferenceExpression) {
                val resolved = receiver.resolve()
                if (resolved != null) {
                    // Check resolved variable type
                    val type = (resolved as? PsiVariable)?.type?.canonicalText
                    if (type != null && type.contains("PictureInPictureParams")) {
                        return true
                    }
                }
                // Even if unresolved, search for its declaration in the surrounding method/class
                val name = receiverText.substringAfterLast('.')
                var found = false
                val surrounding = node.getParentOfType<UMethod>(UMethod::class.java)
                    ?: node.getParentOfType<UClass>(UClass::class.java)
                surrounding?.accept(object : AbstractUastVisitor() {
                    override fun visitVariable(variable: UVariable): Boolean {
                        if (variable.name == name) {
                            val typeStr = variable.type?.canonicalText ?: ""
                            val initStr = variable.uastInitializer?.asSourceString() ?: ""
                            if (typeStr.contains("PictureInPictureParams") || initStr.contains("PictureInPictureParams")) {
                                found = true
                            }
                        }
                        return super.visitVariable(variable)
                    }
                })
                if (found) return true
            }
        }
        
        return false
    }

    private fun checkBuilder(context: JavaContext, buildCall: UCallExpression) {
        var hasAutoEnter = false
        var hasSourceRect = false

        // 1. Check the fluent chain of the buildCall
        var current: UExpression? = buildCall.receiver
        while (current is UCallExpression) {
            val name = current.methodName
            if (name == "setAutoEnterEnabled") {
                val args = current.valueArguments
                if (args.isNotEmpty()) {
                    val argVal = ConstantEvaluator.evaluate(context, args[0])
                    if (argVal == true || args[0].asSourceString() == "true") {
                        hasAutoEnter = true
                    }
                }
            } else if (name == "setSourceRectHint") {
                hasSourceRect = true
            }
            current = current.receiver
        }

        // 2. If not found in the fluent chain, check if the receiver is a variable and search for calls on it
        if (!hasAutoEnter || !hasSourceRect) {
            val receiver = buildCall.receiver
            if (receiver != null) {
                val receiverResolved = (receiver as? UReferenceExpression)?.resolve()
                val receiverStr = receiver.asSourceString()

                val surroundingScope = buildCall.getParentOfType<UMethod>(UMethod::class.java)
                    ?: buildCall.getParentOfType<UClass>(UClass::class.java)

                surroundingScope?.accept(object : AbstractUastVisitor() {
                    override fun visitCallExpression(node: UCallExpression): Boolean {
                        val name = node.methodName
                        if (name == "setAutoEnterEnabled" || name == "setSourceRectHint") {
                            val callReceiver = node.receiver
                            if (callReceiver != null) {
                                val callReceiverResolved = (callReceiver as? UReferenceExpression)?.resolve()
                                val callReceiverStr = callReceiver.asSourceString()

                                val isSameReceiver = (receiverResolved != null && callReceiverResolved == receiverResolved) ||
                                        (receiverStr == callReceiverStr)

                                if (isSameReceiver) {
                                    if (name == "setAutoEnterEnabled") {
                                        val args = node.valueArguments
                                        if (args.isNotEmpty()) {
                                            val argVal = ConstantEvaluator.evaluate(context, args[0])
                                            if (argVal == true || args[0].asSourceString() == "true") {
                                                hasAutoEnter = true
                                            }
                                        }
                                    } else if (name == "setSourceRectHint") {
                                        hasSourceRect = true
                                    }
                                }
                            }
                        }
                        return super.visitCallExpression(node)
                    }
                })
            }
        }

        if (!hasAutoEnter || !hasSourceRect) {
            val missing = mutableListOf<String>()
            if (!hasAutoEnter) missing.add("setAutoEnterEnabled(true)")
            if (!hasSourceRect) missing.add("setSourceRectHint(...)")

            val message = "To support smoother PiP transitions on Android 12 and higher, " +
                    "you should call ${missing.joinToString(" and ")} on the PictureInPictureParams.Builder."

            context.report(
                ISSUE,
                buildCall,
                context.getLocation(buildCall),
                message
            )
        }
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