package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UBinaryExpressionWithType
import org.jetbrains.uast.UastBinaryExpressionWithTypeKind
import org.jetbrains.uast.UQualifiedReferenceExpression

class ViewTypeDetector : ResourceXmlDetector(), SourceCodeScanner, XmlScanner {

    private val idToView = java.util.HashMap<String, String>()

    override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean {
        return folderType == com.android.resources.ResourceFolderType.LAYOUT
    }

    override fun getApplicableAttributes(): java.util.Collection<String>? {
        return java.util.Collections.singletonList("id")
    }

    override fun visitAttribute(context: XmlContext, attribute: org.w3c.dom.Attr) {
        val id = attribute.value
        val idName = id.substringAfter('/')
        if (idName.isNotEmpty()) {
            val view = attribute.ownerElement.tagName
            idToView[idName] = view
        }
    }

    override fun getApplicableMethodNames(): java.util.List<String>? {
        return java.util.Collections.singletonList("findViewById")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.evaluator.isMemberInSubClassOf(method, "android.view.View") &&
            !context.evaluator.isMemberInSubClassOf(method, "android.app.Activity") &&
            !context.evaluator.isMemberInSubClassOf(method, "android.app.Dialog")
        ) {
            return
        }

        val args = node.valueArguments
        if (args.isEmpty()) return
        val firstArg = args[0]
        val argString = firstArg.asSourceString()
        val idName = argString.substringAfterLast('.')

        val xmlType = idToView[idName] ?: return

        val parent = node.uastParent
        val hasCast = parent is UBinaryExpressionWithType && 
                parent.operationKind == UastBinaryExpressionWithTypeKind.TYPE_CAST

        if (!hasCast) {
            if (parent is UQualifiedReferenceExpression && parent.receiver == node) {
                val selector = parent.selector
                if (selector is UCallExpression) {
                    val calledMethod = selector.resolve()
                    if (calledMethod != null) {
                        val containingClass = calledMethod.containingClass
                        if (containingClass != null) {
                            val qualifiedName = containingClass.qualifiedName
                            if (qualifiedName != "android.view.View" && 
                                qualifiedName != "java.lang.Object"
                            ) {
                                context.report(
                                    ISSUE,
                                    node,
                                    context.getLocation(node),
                                    "Add explicit cast here; calling a method on `findViewById` " +
                                            "without a cast is ambiguous or fails to compile in Java 8 " +
                                            "if the method is not defined on `View`."
                                )
                            }
                        }
                    }
                }
            }
        }
    }

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
                """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                ViewTypeDetector::class.java,
                Scope.JAVA_AND_RESOURCE_FILES
            )
        )
    }
}