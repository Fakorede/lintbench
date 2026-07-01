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
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Attr

class ViewTypeDetector : ResourceXmlDetector(), Detector.UastScanner {

    private val idToViewType = mutableMapOf<String, String>()

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
            val idName = id.substringAfter('/')
            var viewType = attribute.ownerElement.tagName
            if (viewType == "view") {
                val classAttr = attribute.ownerElement.getAttribute("class")
                if (!classAttr.isNullOrEmpty()) {
                    viewType = classAttr
                }
            }
            idToViewType[idName] = viewType
        }
    }

    override fun getApplicableMethodNames(): List<String>? {
        return listOf("findViewById")
    }

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        val psiFile = context.psiFile ?: return
        if (psiFile.language.id != "JAVA") {
            return
        }

        val languageLevel = context.project.javaLanguageLevel?.toString() ?: ""
        if (languageLevel.startsWith("JDK_1_8") || 
            languageLevel.startsWith("JDK_1_9") || 
            (!languageLevel.startsWith("JDK_1_") && languageLevel.isNotEmpty())) {
            return
        }

        val name = method.name
        if (name != "findViewById" || node.valueArgumentCount != 1) {
            return
        }

        val argument = node.valueArguments[0]
        val idName = getResourceId(argument) ?: return
        val viewType = idToViewType[idName] ?: return

        if (viewType == "include" || viewType == "merge" || viewType == "fragment" || viewType == "View" || viewType == "view") {
            return
        }

        // Check if already cast
        var parent = node.uastParent
        while (parent is org.jetbrains.uast.UParenthesizedExpression) {
            parent = parent.uastParent
        }

        val hasCast = parent is org.jetbrains.uast.UCastExpression || 
                      parent is org.jetbrains.uast.UBinaryExpressionWithType ||
                      node.typeArguments.isNotEmpty()

        if (!hasCast) {
            val simpleName = viewType.substringAfterLast('.')
            val message = "Add explicit cast to `$simpleName`"
            val fix = fix()
                .name("Cast to $simpleName")
                .replace()
                .beginning()
                .with("($simpleName) ")
                .build()

            context.report(
                ISSUE,
                node,
                context.getLocation(node),
                message,
                fix
            )
        }
    }

    private fun getResourceId(expression: org.jetbrains.uast.UExpression): String? {
        val str = expression.asSourceString().replace("\\s".toRegex(), "")
        val match = Regex("""(?:.*\.)?R\.id\.(\w+)""").matchEntire(str)
        return match?.groupValues?.get(1)
    }
}