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
import com.android.tools.lint.detector.api.XmlContext
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import java.util.EnumSet
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UBinaryExpressionWithType
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UastBinaryExpressionWithTypeKind
import org.jetbrains.uast.UastBinaryOperator
import org.w3c.dom.Attr

class ViewTypeDetector : ResourceXmlDetector(), Detector.UastScanner {

    private val idToView = HashMap<String, String>()

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
                """.trimIndent(),
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
                viewType = attribute.ownerElement.getAttribute("class") ?: viewType
            }
            idToView[idName] = viewType
        }
    }

    override fun getApplicableMethodNames(): List<String>? {
        return listOf("findViewById")
    }

    override fun visitMethodCall(
        context: JavaContext, node: UCallExpression, method: PsiMethod,
    ) {
        val args = node.valueArguments
        if (args.isEmpty()) return
        val firstArg = args[0]
        val argString = firstArg.asSourceString()
        val idName = argString.substringAfterLast('.')

        val expectedType = idToView[idName] ?: return

        var parent = node.uastParent
        var hasCast = false
        var castType: PsiType? = null

        while (parent != null) {
            if (parent is UBlockExpression || parent is UMethod || parent is UClass) {
                break
            }
            if (parent is UBinaryExpressionWithType) {
                if (parent.operationKind == UastBinaryExpressionWithTypeKind.TYPE_CAST) {
                    hasCast = true
                    castType = parent.type
                    break
                }
            }
            parent = parent.uastParent
        }

        if (hasCast && castType != null) {
            if (!isCompatible(context, castType, expectedType)) {
                val castTypeName = castType.presentableText
                val expectedSimpleName = expectedType.substringAfterLast('.')
                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node),
                    "Unexpected cast to `$castTypeName`: layout tag was `$expectedSimpleName`"
                )
            }
        } else {
            val languageLevel = context.project.javaLanguageLevel
            val isJava7OrBelow = languageLevel != null && !languageLevel.isAtLeast(com.intellij.pom.java.LanguageLevel.JDK_1_8)

            if (isJava7OrBelow) {
                var assignedType: PsiType? = null
                var assignParent = node.uastParent
                while (assignParent != null) {
                    if (assignParent is UBlockExpression || assignParent is UMethod || assignParent is UClass) {
                        break
                    }
                    if (assignParent is ULocalVariable) {
                        assignedType = assignParent.type
                        break
                    }
                    if (assignParent is UBinaryExpression && assignParent.operator == UastBinaryOperator.ASSIGN) {
                        assignedType = assignParent.leftOperand.getExpressionType()
                        break
                    }
                    assignParent = assignParent.uastParent
                }

                if (assignedType != null) {
                    val assignedTypeName = assignedType.canonicalText
                    if (assignedTypeName != "android.view.View" && assignedTypeName != "java.lang.Object") {
                        val expectedSimpleName = expectedType.substringAfterLast('.')
                        context.report(
                            ISSUE,
                            node,
                            context.getLocation(node),
                            "Add explicit cast to `$expectedSimpleName`"
                        )
                    }
                }
            }
        }
    }

    private fun isCompatible(context: JavaContext, castType: PsiType, xmlType: String): Boolean {
        val typeName = castType.canonicalText
        if (typeName == "android.view.View" || typeName == "java.lang.Object") {
            return true
        }

        val castClass = (castType as? PsiClassType)?.resolve()
        val xmlClass = context.evaluator.findClass(xmlType)
            ?: context.evaluator.findClass("android.widget.$xmlType")
            ?: context.evaluator.findClass("android.view.$xmlType")

        if (castClass != null && xmlClass != null) {
            return castClass.isInheritor(xmlClass, true) || xmlClass.isInheritor(castClass, true) || castClass.equivalentTo(xmlClass)
        }

        val castSimple = castType.presentableText.substringAfterLast('.')
        val xmlSimple = xmlType.substringAfterLast('.')

        if (castSimple == xmlSimple || castSimple == "View" || castSimple == "Object" || xmlSimple == "View") {
            return true
        }

        val commonWidgets = mapOf(
            "Button" to listOf("TextView", "View"),
            "TextView" to listOf("View"),
            "EditText" to listOf("TextView", "View"),
            "ImageView" to listOf("View"),
            "ImageButton" to listOf("ImageView", "View"),
            "CheckBox" to listOf("Button", "TextView", "View"),
            "RadioButton" to listOf("Button", "TextView", "View"),
            "LinearLayout" to listOf("ViewGroup", "View"),
            "RelativeLayout" to listOf("ViewGroup", "View"),
            "FrameLayout" to listOf("ViewGroup", "View")
        )

        val castAncestors = commonWidgets[castSimple] ?: emptyList()
        val xmlAncestors = commonWidgets[xmlSimple] ?: emptyList()

        if (castSimple in xmlAncestors || xmlSimple in castAncestors) {
            return true
        }

        return false
    }
}