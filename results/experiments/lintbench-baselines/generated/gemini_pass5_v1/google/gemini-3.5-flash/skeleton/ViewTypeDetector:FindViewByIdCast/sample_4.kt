package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.uast.UAsExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpressionList
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UTypeCastExpression
import org.w3c.dom.Attr

class ViewTypeDetector : ResourceXmlDetector(), SourceCodeScanner {

    private val idToViewMap = HashMap<String, String>()

    companion object {
        private val IMPLEMENTATION = Implementation(
            ViewTypeDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE),
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "FindViewByIdCast",
            briefDescription = "Add Explicit Cast",
            explanation = "In Android O, the `findViewById` signature switched to using generics, which " +
                    "means that most of the time you can leave out explicit casts and just assign " +
                    "the result of the `findViewById` call to variables of specific view classes.\n\n" +
                    "However, due to language changes between Java 7 and 8, this change may cause " +
                    "code to not compile without explicit casts. This lint check looks for these " +
                    "scenarios and suggests casts to be added now such that the code will " +
                    "continue to compile if the language level is updated to 1.8.",
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
        val id = attribute.value
        if (id.startsWith("@+id/") || id.startsWith("@id/")) {
            val idName = id.substring(id.indexOf('/') + 1)
            val tagName = attribute.ownerElement.tagName
            idToViewMap[idName] = tagName
        }
    }

    override fun getApplicableMethodNames(): List<String>? {
        return listOf("findViewById")
    }

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        if (method.name != "findViewById") return

        val parent = skipParenthesizedExprUp(node.uastParent) ?: return
        if (parent is UTypeCastExpression || parent is UAsExpression) {
            return
        }

        val args = node.valueArguments
        if (args.isEmpty()) return
        val firstArg = args[0]
        val argString = firstArg.asSourceString()
        val idName = argString.substringAfterLast('.')

        val viewType = idToViewMap[idName] ?: "android.view.View"
        if (viewType == "android.view.View" || viewType == "View") return

        var shouldReport = false

        if (parent is UQualifiedReferenceExpression) {
            if (parent.receiver == node) {
                val resolved = parent.resolve()
                if (resolved is PsiMethod) {
                    val containingClass = resolved.containingClass
                    if (containingClass != null) {
                        val className = containingClass.qualifiedName
                        if (className != null && className != "android.view.View") {
                            shouldReport = true
                        }
                    }
                } else {
                    shouldReport = true
                }
            }
        } else if (parent is UCallExpression) {
            val methodResolved = parent.resolve()
            if (methodResolved != null) {
                val index = parent.valueArguments.indexOf(node)
                if (index >= 0 && index < methodResolved.parameterList.parametersCount) {
                    val parameter = methodResolved.parameterList.parameters[index]
                    val paramType = parameter.type.canonicalText
                    if (paramType != "android.view.View" && paramType != "java.lang.Object") {
                        shouldReport = true
                    }
                }
            }
        } else if (parent is UExpressionList || parent is UIfExpression) {
            shouldReport = true
        }

        if (shouldReport) {
            val simpleName = viewType.substringAfterLast('.')
            val suggestedCast = "($simpleName) "
            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                "Add explicit cast here; the generic `findViewById` method can choose the wrong type under Java 8 compilation",
                context.fix().name("Cast to $simpleName").replace().with("$suggestedCast${node.asSourceString()}").build()
            )
        }
    }

    private fun skipParenthesizedExprUp(element: UElement?): UElement? {
        var current = element
        while (current is UParenthesizedExpression) {
            current = current.uastParent
        }
        return current
    }
}