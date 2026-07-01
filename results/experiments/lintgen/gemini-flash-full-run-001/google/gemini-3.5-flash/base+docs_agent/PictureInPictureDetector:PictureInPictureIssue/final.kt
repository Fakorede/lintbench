package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
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
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.visitor.AbstractUastVisitor

class PictureInPictureDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UClass::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitClass(node: UClass) {
                val visitor = PiPVisitor()
                node.accept(visitor)

                val buildCalls = visitor.buildCalls
                val enterPiPCalls = visitor.enterPiPCalls
                val setParamsCalls = visitor.setParamsCalls

                var hasCorrectBuilder = false
                val incorrectBuilders = mutableListOf<Pair<UCallExpression, String>>()

                for (buildCall in buildCalls) {
                    val enclosingMethod = getEnclosingMethod(buildCall)
                    val builderCalls = getBuilderCalls(buildCall, enclosingMethod)

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

                    if (hasAutoEnterEnabledTrue && hasSourceRectHint) {
                        hasCorrectBuilder = true
                    } else {
                        val missing = mutableListOf<String>()
                        if (!hasAutoEnterEnabledTrue) {
                            missing.add("setAutoEnterEnabled(true)")
                        }
                        if (!hasSourceRectHint) {
                            missing.add("setSourceRectHint(...)")
                        }
                        val missingStr = missing.joinToString(" and ")
                        incorrectBuilders.add(buildCall to missingStr)
                    }
                }

                // Report on incorrect builders
                for (pair in incorrectBuilders) {
                    val (buildCall, missingStr) = pair
                    context.report(
                        ISSUE,
                        buildCall,
                        context.getLocation(buildCall),
                        "To support smooth Picture-in-Picture transitions, call $missingStr on the PictureInPictureParams.Builder."
                    )
                }

                // Report on enterPictureInPictureMode() with 0 arguments or when no builder is found
                for (call in enterPiPCalls) {
                    if (call.valueArgumentCount == 0) {
                        context.report(
                            ISSUE,
                            call,
                            context.getLocation(call),
                            "To support smooth Picture-in-Picture transitions, call setAutoEnterEnabled(true) and setSourceRectHint(...) on the PictureInPictureParams.Builder."
                        )
                    } else if (buildCalls.isEmpty()) {
                        context.report(
                            ISSUE,
                            call,
                            context.getLocation(call),
                            "To support smooth Picture-in-Picture transitions, call setAutoEnterEnabled(true) and setSourceRectHint(...) on the PictureInPictureParams.Builder."
                        )
                    }
                }

                // Report on setPictureInPictureParams if no builder found
                if (buildCalls.isEmpty()) {
                    for (call in setParamsCalls) {
                        context.report(
                            ISSUE,
                            call,
                            context.getLocation(call),
                            "To support smooth Picture-in-Picture transitions, call setAutoEnterEnabled(true) and setSourceRectHint(...) on the PictureInPictureParams.Builder."
                        )
                    }
                }
            }
        }
    }

    private inner class PiPVisitor : AbstractUastVisitor() {
        val enterPiPCalls = mutableListOf<UCallExpression>()
        val setParamsCalls = mutableListOf<UCallExpression>()
        val buildCalls = mutableListOf<UCallExpression>()

        override fun visitCallExpression(node: UCallExpression): Boolean {
            val name = node.methodName
            if (name == "enterPictureInPictureMode") {
                enterPiPCalls.add(node)
            } else if (name == "setPictureInPictureParams") {
                setParamsCalls.add(node)
            } else if (name == "build") {
                if (isPiPBuilderCall(node)) {
                    buildCalls.add(node)
                }
            }
            return super.visitCallExpression(node)
        }
    }

    private fun isPiPBuilderCall(node: UCallExpression): Boolean {
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
        if (receiverType != null && receiverType.canonicalText.contains("PictureInPictureParams")) {
            return true
        }

        // 3. Check receiver chain text
        var curr: UExpression? = node.receiver
        while (curr != null) {
            val text = curr.asSourceString()
            if (text.contains("PictureInPictureParams")) {
                return true
            }
            if (curr is UCallExpression) {
                curr = curr.receiver
            } else {
                break
            }
        }

        // 4. If receiver is a reference, find its declaration
        val receiver = node.receiver
        if (receiver is UReferenceExpression) {
            val resolved = receiver.resolve()
            if (resolved is PsiVariable) {
                val typeText = resolved.type.canonicalText
                if (typeText.contains("PictureInPictureParams")) {
                    return true
                }
            }
            val varName = receiver.resolvedName ?: (receiver as? USimpleNameReferenceExpression)?.identifier
            if (varName != null) {
                val enclosingMethod = getEnclosingMethod(node)
                if (enclosingMethod != null) {
                    var found = false
                    val fileText = node.sourcePsi?.containingFile?.text ?: ""
                    val containsPiP = fileText.contains("PictureInPictureParams")
                    enclosingMethod.accept(object : AbstractUastVisitor() {
                        override fun visitVariable(variable: UVariable): Boolean {
                            if (variable.name == varName) {
                                val typeStr = variable.type.canonicalText
                                val initStr = variable.uastInitializer?.asSourceString()
                                if (typeStr.contains("PictureInPictureParams") || 
                                    initStr?.contains("PictureInPictureParams") == true ||
                                    (containsPiP && (typeStr.contains("Builder") || initStr?.contains("Builder") == true))) {
                                    found = true
                                }
                            }
                            return super.visitVariable(variable)
                        }
                    })
                    if (found) return true
                }
            }
        }

        return false
    }

    private fun getBuilderCalls(buildCall: UCallExpression, enclosingMethod: UMethod?): List<UCallExpression> {
        val calls = mutableListOf<UCallExpression>()
        // 1. Chain calls
        var curr: UExpression? = buildCall.receiver
        while (curr is UCallExpression) {
            calls.add(curr)
            curr = curr.receiver
        }
        // 2. If the root of the chain is a local variable reference, find other calls on that variable
        val root = curr
        if (root is UReferenceExpression) {
            val resolved = root.resolve()
            val name = root.resolvedName ?: (root as? USimpleNameReferenceExpression)?.identifier
            if (enclosingMethod != null) {
                enclosingMethod.accept(object : AbstractUastVisitor() {
                    override fun visitCallExpression(node: UCallExpression): Boolean {
                        val nodeRoot = getRootReceiver(node)
                        if (nodeRoot is UReferenceExpression) {
                            val nodeResolved = nodeRoot.resolve()
                            val nodeName = nodeRoot.resolvedName ?: (nodeRoot as? USimpleNameReferenceExpression)?.identifier
                            if ((resolved != null && nodeResolved == resolved) || (name != null && nodeName == name)) {
                                var c: UExpression? = node
                                while (c is UCallExpression) {
                                    calls.add(c)
                                    c = c.receiver
                                }
                            }
                        }
                        return super.visitCallExpression(node)
                    }
                })
            }
        }
        return calls
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