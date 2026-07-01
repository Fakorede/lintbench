package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.*
import com.intellij.psi.PsiModifier
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UElementHandler

class WrongConstructorDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (node.isConstructor) {
                    return
                }

                val methodName = node.name ?: return
                val containingClass = node.containingClass ?: return
                val className = containingClass.name ?: return

                if (methodName != className) {
                    return
                }

                if (node.hasModifierProperty(PsiModifier.STATIC)) {
                    return
                }

                if (containingClass.isInterface || containingClass.isAnnotationType) {
                    return
                }

                context.report(
                    ISSUE,
                    node,
                    context.getLocation(node.nameIdentifier ?: node),
                    "Method `$methodName` has the same name as its containing class, " +
                            "so it is not a constructor"
                )
            }
        }

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "NotConstructor",
            briefDescription = "Not a constructor",
            explanation = """
                A method whose name is the same as its containing class is only a constructor \
                if it has no return type. If a return type is present, it is an ordinary method, \
                which usually means the developer intended to write a constructor and made a mistake.
                """,
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = Implementation(
                WrongConstructorDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}