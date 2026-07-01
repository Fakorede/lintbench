package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.*
import org.jetbrains.uast.*

class ViewTypeDetector : ResourceXmlDetector(), Detector.SourceCodeScanner {

    private val idToViewClasses = mutableMapOf<String, MutableSet<String>>()

    override fun beforeCheckEachProject(context: Context) {
        idToViewClasses.clear()
    }

    override fun appliesTo(folderType: com.android.resources.ResourceFolderType): Boolean =
        folderType == com.android.resources.ResourceFolderType.LAYOUT

    override fun getApplicableAttributes(): Collection<String>? = listOf("id")

    override fun visitAttribute(context: XmlContext, attribute: org.w3c.dom.Attr) {
        if (attribute.localName != "id") return
        val value = attribute.value ?: return
        val idName = getIdName(value) ?: return
        val tag = attribute.ownerElement?.tagName ?: return
        val viewClass = getViewClass(tag) ?: return
        idToViewClasses.getOrPut(idName) { mutableSetOf() }.add(viewClass)
    }

    override fun getApplicableMethodNames(): List<String>? = listOf("findViewById")

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        if (node.valueArguments.size != 1) return
        val argument = node.valueArguments[0]
        val idName = getResourceName(argument) ?: return
        val viewClasses = idToViewClasses[idName] ?: return
        val targetType = getTargetType(node) ?: return
        val targetClass = targetType.resolve() ?: return
        val compatible = viewClasses.any { viewClass ->
            viewClass == targetType.canonicalText ||
                context.evaluator.findClass(viewClass)?.isInheritor(targetClass, true) == true
        }
        if (compatible) return

        val viewClassName = viewClasses.first()
        context.report(
            ISSUE,
            node,
            context.getLocation(node),
            "Add explicit cast to $viewClassName: the view for id '$idName' is a $viewClassName, " +
                "but the expression type is ${targetType.canonicalText}",
        )
    }

    companion object {
        private const val EXPLANATION =
            "In Android O, the findViewById signature switched to using generics, which " +
            "means that most of the time you can leave out explicit casts and just assign " +
            "the result of the findViewById call to variables of specific view classes.\n\n" +
            "However, due to language changes between Java 7 and 8, this change may cause " +
            "code to not compile without explicit casts. This lint check looks for these " +
            "scenarios and suggests casts to be added now such that the code will continue " +
            "to compile if the language level is updated to 1.8."

        private val IMPLEMENTATION = Implementation(
            ViewTypeDetector::class.java,
            java.util.EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE),
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "FindViewByIdCast",
            briefDescription = "Add Explicit Cast",
            explanation = EXPLANATION,
            category = Category.CORRECTNESS,
            priority = 9,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    private fun getIdName(value: String): String? {
        if (value.startsWith("@+id/") ||
            value.startsWith("@id/") ||
            value.startsWith("@android:id/") ||
            value.startsWith("@*android:id/")) {
            return value.substring(value.indexOf('/') + 1)
        }
        return null
    }

    private fun getViewClass(tagName: String): String? {
        if (tagName.isEmpty() ||
            tagName == "include" ||
            tagName == "fragment" ||
            tagName == "merge") {
            return null
        }
        return when {
            '.' in tagName -> tagName
            tagName == "View" -> "android.view.View"
            tagName == "ViewGroup" -> "android.view.ViewGroup"
            tagName == "ViewStub" -> "android.view.ViewStub"
            tagName == "SurfaceView" -> "android.view.SurfaceView"
            tagName == "TextureView" -> "android.view.TextureView"
            tagName == "WebView" -> "android.webkit.WebView"
            else -> "android.widget.$tagName"
        }
    }

    private fun getResourceName(argument: UExpression): String? {
        var expr: UExpression? = argument
        while (expr is UQualifiedReferenceExpression) {
            val selector = expr.selector
            if (selector is USimpleNameReferenceExpression) {
                val receiver = expr.receiver
                if (receiver is UQualifiedReferenceExpression) {
                    val idSelector = receiver.selector
                    if (idSelector is USimpleNameReferenceExpression && idSelector.identifier == "id") {
                        val rReceiver = receiver.receiver
                        if (rReceiver is USimpleNameReferenceExpression && rReceiver.identifier == "R") {
                            return selector.identifier
                        }
                    }
                }
            }
            expr = expr.receiver
        }
        return null
    }

    private fun getTargetType(node: UCallExpression): PsiClassReferenceType? {
        val variable = UastUtils.getParentOfType(node, UVariable::class.java, false)
        if (variable != null) {
            return variable.typeReference?.type as? PsiClassReferenceType
        }
        val binary = UastUtils.getParentOfType(node, UBinaryExpression::class.java, false)
        if (binary != null && binary.operator == UastBinaryOperator.ASSIGN) {
            return binary.leftOperand.getExpressionType() as? PsiClassReferenceType
        }
        return null
    }
}