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
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UVariable

class ViewTypeDetector : ResourceXmlDetector(), SourceCodeScanner, XmlScanner {

    private val idToType = HashMap<String, String>()

    override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean {
        return folderType == com.android.resources.ResourceFolderType.LAYOUT
    }

    override fun getApplicableAttributes(): Collection<String> {
        return listOf("id")
    }

    override fun visitAttribute(context: XmlContext, attribute: org.w3c.dom.Attr) {
        val idValue = attribute.value
        val id = idValue.substringAfter('/')
        val tagName = attribute.ownerElement.tagName
        idToType[id] = tagName
    }

    override fun getApplicableMethodNames(): List<String> {
        return listOf("findViewById")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        if (!context.file.name.endsWith(".java")) {
            return
        }

        if (!context.evaluator.isMemberInSubClassOf(method, "android.view.View") &&
            !context.evaluator.isMemberInSubClassOf(method, "android.app.Activity") &&
            !context.evaluator.isMemberInSubClassOf(method, "android.app.Dialog") &&
            !context.evaluator.isMemberInSubClassOf(method, "android.view.Window")
        ) {
            return
        }

        val hasCast = node.uastParent is org.jetbrains.uast.UBinaryExpressionWithType

        if (!hasCast) {
            val languageLevel = context.project.javaLanguageLevel
            if (languageLevel != null && !languageLevel.isAtLeast(com.intellij.pom.java.LanguageLevel.JDK_1_8)) {
                val destinationType = getDestinationType(node)
                if (destinationType != null && destinationType != "android.view.View" && destinationType != "java.lang.Object") {
                    val id = getResultId(node)
                    val xmlType = id?.let { idToType[it] }
                    val castType = xmlType ?: destinationType.substringAfterLast('.')
                    val message = "Add explicit cast to `$castType` so that the code compiles with Java 7 and 8"
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        message
                    )
                }
            }
        }
    }

    private fun getDestinationType(node: UCallExpression): String? {
        val parent = node.uastParent ?: return null
        if (parent is UVariable) {
            return parent.type.canonicalText
        }
        if (parent is UBinaryExpression) {
            val left = parent.leftOperand
            val type = left.getExpressionType()
            return type?.canonicalText
        }
        return null
    }

    private fun getResultId(call: UCallExpression): String? {
        val args = call.valueArguments
        if (args.isEmpty()) return null
        val first = args[0]
        val source = first.asSourceString()
        val index = source.lastIndexOf('.')
        if (index != -1 && source.contains("R.id.")) {
            return source.substring(index + 1).trim()
        }
        return null
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