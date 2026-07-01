package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
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
import com.android.tools.lint.detector.api.XmlScanner
import com.android.tools.lint.detector.api.interprocedural.CallGraphResult
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UBinaryExpressionWithType
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UField
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UReferenceExpression
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.util.HashMap

class ViewTypeDetector : Detector(), SourceCodeScanner, XmlScanner {
    private val idToTag = HashMap<String, String>()

    override fun appliesTo(folderType: ResourceFolderType): Boolean {
        return folderType == ResourceFolderType.LAYOUT
    }

    override fun getApplicableAttributes(): Collection<String>? {
        return listOf("id")
    }

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        val id = attribute.value
        val cleanId = id.substringAfter("@+id/").substringAfter("@id/")
        if (cleanId.isNotEmpty()) {
            var tag = attribute.ownerElement.tagName
            if (tag == "view") {
                val classAttr = attribute.ownerElement.getAttribute("class")
                if (!classAttr.isNullOrEmpty()) {
                    tag = classAttr
                }
            }
            idToTag[cleanId] = tag
        }
    }

    override fun getApplicableMethodNames(): List<String>? {
        return listOf("findViewById", "requireViewById")
    }

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        checkCast(context, node, method)
        checkWrongCast(context, node, method)
    }

    private fun checkCast(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val languageLevel = context.project.javaLanguageLevel
        if (languageLevel != null && languageLevel.isAtLeast(LanguageLevel.JDK_1_8)) {
            return
        }
        if (!context.file.name.endsWith(".java")) {
            return
        }
        val returnType = method.returnType
        val resolved = (returnType as? PsiClassType)?.resolve()
        if (resolved !is PsiTypeParameter) {
            return
        }

        val parent = skipParentheses(node.uastParent)
        if (parent is UBinaryExpressionWithType) {
            if (parent.operationKind.name == "type_cast") {
                return
            }
        }

        var curr = node.uastParent
        while (curr != null) {
            if (curr is UIfExpression) {
                if (curr.isTernary) {
                    val message = "Add explicit cast here; generic inference for ternary expressions changed in Java 8 and will otherwise fail to compile"
                    context.report(FIND_VIEW_BY_ID_CAST, node, context.getLocation(node), message)
                    return
                }
            } else if (curr is ULocalVariable || curr is UField || curr is UBinaryExpression) {
                break
            } else if (curr is UCallExpression) {
                break
            }
            curr = curr.uastParent
        }
    }

    private fun checkWrongCast(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val firstArg = node.valueArguments.firstOrNull() ?: return
        val idName = getIdName(firstArg) ?: return
        val tag = idToTag[idName] ?: return
        if (tag == "fragment" || tag == "include") return

        val parent = skipParentheses(node.uastParent)
        var targetType: PsiType? = null
        if (parent is UBinaryExpressionWithType && parent.operationKind.name == "type_cast") {
            targetType = parent.type
        } else if (parent is ULocalVariable) {
            targetType = parent.type
        } else if (parent is UField) {
            targetType = parent.type
        } else if (parent is UBinaryExpression && parent.operator.text == "=") {
            targetType = parent.leftOperand.getExpressionType()
        }

        if (targetType == null) return
        val castClass = getPsiClass(targetType) ?: return
        val tagFqName = getFqNameForTag(tag)
        val tagClass = context.evaluator.findClass(tagFqName) ?: return

        if (tagClass != castClass) {
            val castFqName = castClass.qualifiedName ?: return
            if (!context.evaluator.inheritsFrom(tagClass, castFqName, false)) {
                val message = "Unexpected cast to `${castClass.name}`: layout file says the view is `$tag`"
                context.report(WRONG_VIEW_CAST, node, context.getLocation(node), message)
            }
        }
    }

    private fun skipParentheses(element: UElement?): UElement? {
        var curr = element
        while (curr is UParenthesizedExpression) {
            curr = curr.expression
        }
        return curr
    }

    private fun getPsiClass(type: PsiType): PsiClass? {
        if (type is PsiClassType) {
            return type.resolve()
        }
        return null
    }

    private fun getFqNameForTag(tag: String): String {
        if (tag.contains('.')) {
            return tag
        }
        if (tag == "View" || tag == "ViewGroup" || tag == "ViewStub") {
            return "android.view.$tag"
        }
        if (tag == "WebView") {
            return "android.webkit.$tag"
        }
        return "android.widget.$tag"
    }

    private fun getIdName(expression: UElement): String? {
        val source = expression.asSourceString()
        val index = source.indexOf("R.id.")
        if (index != -1) {
            val idPart = source.substring(index + 5)
            val sb = StringBuilder()
            for (char in idPart) {
                if (char.isLetterOrDigit() || char == '_') {
                    sb.append(char)
                } else {
                    break
                }
            }
            if (sb.isNotEmpty()) {
                return sb.toString()
            }
        }
        return null
    }

    companion object {
        @JvmField
        val WRONG_VIEW_CAST = Issue.create(
            id = "WrongViewCast",
            briefDescription = "Unexpected cast",
            explanation = """
                Keeps track of casts...
                """,
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.ERROR,
            implementation = Implementation(
                ViewTypeDetector::class.java,
                Scope.JAVA_AND_RESOURCE_FILES
            )
        )

        @JvmField
        val FIND_VIEW_BY_ID_CAST = Issue.create(
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
            priority = 5,
            severity = Severity.WARNING,
            implementation = Implementation(
                ViewTypeDetector::class.java,
                Scope.JAVA_AND_RESOURCE_FILES
            )
        )
    }
}