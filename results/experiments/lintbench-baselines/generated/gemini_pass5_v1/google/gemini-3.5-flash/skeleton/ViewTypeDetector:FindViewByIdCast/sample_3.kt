package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UBinaryExpressionWithType
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UElement
import org.w3c.dom.Attr

class ViewTypeDetector : ResourceXmlDetector(), Detector.SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            ViewTypeDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE),
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "FindViewByIdCast",
            briefDescription = "Add Explicit Cast",
            explanation = """
                In Android O, the `findViewById` signature switched to using generics, which 
                means that most of the time you can leave out explicit casts and just assign 
                the result of the `findViewById` call to variables of specific view classes.

                However, due to language changes between Java 7 and 8, this change may cause 
                code to not compile without explicit casts. This lint check looks for these 
                scenarios and suggests casts to be added now such that the code will 
                continue to compile if the language level is updated to 1.8.
                """,
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
        // No-op: XML scanning is not needed for the FindViewByIdCast issue,
        // which specifically targets Java 8 compilation compatibility.
    }

    override fun getApplicableMethodNames(): List<String>? {
        return listOf("findViewById")
    }

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        if (context.project.languageLevel.isAtLeast(com.intellij.pom.java.LanguageLevel.JDK_1_8)) {
            return
        }

        val psiFile = context.psiFile
        if (psiFile != null && !psiFile.name.endsWith(".java")) {
            return
        }

        var parent = node.uastParent
        while (parent is UParenthesizedExpression) {
            parent = parent.uastParent
        }
        if (parent is UBinaryExpressionWithType) {
            return
        }

        if (parent is UCallExpression) {
            val target = parent.resolve() ?: return
            val containingClass = target.containingClass ?: return
            val methodName = target.name
            val methods = containingClass.findMethodsByName(methodName, true)
            if (methods.size > 1) {
                val arguments = parent.valueArguments
                var argumentIndex = -1
                for (i in arguments.indices) {
                    var arg: UElement? = arguments[i]
                    while (arg is UParenthesizedExpression) {
                        arg = arg.uastParent
                    }
                    if (arg == node || arg?.sourcePsi == node.sourcePsi) {
                        argumentIndex = i
                        break
                    }
                }
                if (argumentIndex != -1) {
                    val parameters = target.parameterList.parameters
                    if (argumentIndex < parameters.size) {
                        val parameter = parameters[argumentIndex]
                        val paramType = parameter.type.presentableText
                        val castType = if (paramType == "T") "View" else paramType
                        
                        val message = "Add explicit cast to `$castType` to force compiled type"
                        val fix = context.fix()
                            .name("Cast to $castType")
                            .replace()
                            .beginning()
                            .with("($castType) ")
                            .build()
                        
                        context.report(ISSUE, node, context.getLocation(node), message, fix)
                    }
                }
            }
        }
    }
}