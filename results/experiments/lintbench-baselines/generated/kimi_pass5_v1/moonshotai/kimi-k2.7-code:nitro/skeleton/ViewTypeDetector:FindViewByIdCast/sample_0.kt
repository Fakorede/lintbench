package com.android.tools.lint.checks

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import java.util.EnumSet
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UCastExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UastBinaryOperator
import org.w3c.dom.Attr

class ViewTypeDetector : ResourceXmlDetector(), SourceCodeScanner {

    companion object {
        private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
        private const val FIND_VIEW_BY_ID = "findViewById"
        private const val VIEW_CLASS = "android.view.View"

        private val IMPLEMENTATION = Implementation(
            ViewTypeDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE),
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "FindViewByIdCast",
            briefDescription = "Add Explicit Cast",
            explanation = """
                In Android O the `findViewById` signature became generic. In Java 8 the compiler can \
                usually infer the target View type, but there are contexts (such as using the result \
                as a receiver for a subclass method or assigning it to a typed field in Java 7) where \
                an explicit cast is required to keep the code compiling. Add an explicit cast to the \
                expected View subclass.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    private val idToClass = mutableMapOf<String, String>()

    override fun appliesTo(folderType: ResourceFolderType): Boolean =
        folderType == ResourceFolderType.LAYOUT

    override fun getApplicableAttributes(): Collection<String>? = listOf("id")

    override fun visitAttribute(context: XmlContext, attribute: Attr) {
        if (attribute.localName != "id") return
        if (attribute.namespaceURI != ANDROID_URI) return

        val value = attribute.value ?: return
        val id = value.substringAfterLast('/', "")
        if (id.isEmpty()) return

        val element = attribute.ownerElement ?: return
        val className = resolveTagToClass(element.tagName) ?: return
        idToClass[id] = className
    }

    override fun getApplicableMethodNames(): List<String>? = listOf(FIND_VIEW_BY_ID)

    override fun visitMethodCall(
        context: JavaContext,
        node: UCallExpression,
        method: PsiMethod,
    ) {
        if (method.name != FIND_VIEW_BY_ID) return
        if (node.valueArguments.isEmpty()) return

        val parent = skipParenthesized(node.uastParent)
        if (parent is UCastExpression) return

        val castType = findRequiredCastType(context, node) ?: return
        val idName = getResourceIdName(node)
        val actualType = idName?.let { idToClass[it] }
        val typeToUse = actualType ?: castType

        if (typeToUse == VIEW_CLASS) return

        val message = "Add explicit cast to $typeToUse for this `findViewById` call"
        val fix = LintFix.create()
            .name("Add explicit cast")
            .replace()
            .range(context.getLocation(node))
            .with("($typeToUse) ${node.asSourceString()}")
            .build()

        context.report(ISSUE, node, context.getLocation(node), message, fix)
    }

    private fun findRequiredCastType(context: JavaContext, node: UCallExpression): String? {
        val parent = skipParenthesized(node.uastParent) ?: return null

        if (parent is UQualifiedReferenceExpression &&
            skipParenthesized(parent.receiver) == node
        ) {
            val selector = parent.selector
            if (selector is UCallExpression) {
                val resolved = selector.resolve() as? PsiMethod ?: return null
                return viewSubclassName(context, resolved.containingClass)
            } else if (selector is UReferenceExpression) {
                val resolved = selector.resolve()
                if (resolved is PsiField) {
                    return viewSubclassName(context, resolved.containingClass)
                }
            }
            return null
        }

        if (parent is UBinaryExpression &&
            parent.operator == UastBinaryOperator.ASSIGN &&
            skipParenthesized(parent.rightOperand) == node
        ) {
            return typeName(context, parent.leftOperand.getExpressionType())
        }

        if (parent is UVariable) {
            return typeName(context, parent.type)
        }

        return null
    }

    private fun viewSubclassName(context: JavaContext, psiClass: PsiClass?): String? {
        if (psiClass == null) return null
        val name = psiClass.qualifiedName ?: return null
        if (name == VIEW_CLASS) return null
        if (!context.evaluator.extendsClass(psiClass, VIEW_CLASS, false)) return null
        return name
    }

    private fun typeName(context: JavaContext, type: PsiType?): String? {
        val classType = type as? PsiClassType ?: return null
        val psiClass = classType.resolve() ?: return null
        return viewSubclassName(context, psiClass)
    }

    private fun getResourceIdName(node: UCallExpression): String? {
        val arg = node.valueArguments.firstOrNull() ?: return null
        val source = arg.asSourceString()
        return source.substringAfterLast('.').takeIf {
            source.startsWith("R.id.") || source.contains(".id.")
        }
    }

    private fun skipParenthesized(element: UElement?): UElement? {
        var e = element
        while (e is UParenthesizedExpression) {
            e = e.uastParent
        }
        return e
    }

    private fun resolveTagToClass(tag: String): String? {
        if (tag.isEmpty() ||
            tag == "include" ||
            tag == "merge" ||
            tag == "fragment" ||
            tag == "view"
        ) {
            return null
        }

        if (tag.contains('.')) return tag

        return when (tag) {
            "TextView" -> "android.widget.TextView"
            "Button" -> "android.widget.Button"
            "EditText" -> "android.widget.EditText"
            "ImageView" -> "android.widget.ImageView"
            "View" -> "android.view.View"
            "ViewGroup" -> "android.view.ViewGroup"
            "LinearLayout" -> "android.widget.LinearLayout"
            "RelativeLayout" -> "android.widget.RelativeLayout"
            "FrameLayout" -> "android.widget.FrameLayout"
            "GridLayout" -> "android.widget.GridLayout"
            "ConstraintLayout" -> "androidx.constraintlayout.widget.ConstraintLayout"
            else -> null
        }
    }
}