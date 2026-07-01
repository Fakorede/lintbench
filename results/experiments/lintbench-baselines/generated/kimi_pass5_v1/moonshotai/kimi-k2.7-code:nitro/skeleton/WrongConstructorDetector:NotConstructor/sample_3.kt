package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiModifier
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod

class WrongConstructorDetector : Detector(), SourceCodeScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            WrongConstructorDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
        )

        @JvmField
        val ISSUE = Issue.create(
            id = "NotConstructor",
            briefDescription = "Not a Constructor",
            explanation = """
                A method that has the same name as its surrounding class looks like a
                constructor, but if it declares a return type it is not a constructor.
                This is usually a mistake: remove the return type to make it a constructor,
                or rename the method to avoid confusion.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 4,
            severity = Severity.WARNING,
            implementation = IMPLEMENTATION,
        )
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>>? =
        listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                if (node.isConstructor) return

                val containingClass = node.containingClass ?: return
                if (containingClass.isInterface) return
                if (node.hasModifierProperty(PsiModifier.STATIC)) return

                val className = containingClass.name ?: return
                val methodName = node.name ?: return

                if (methodName == className) {
                    context.report(
                        ISSUE,
                        node,
                        context.getLocation(node),
                        "Method `$methodName` has the same name as its class but is not a constructor"
                    )
                }
            }
        }
}