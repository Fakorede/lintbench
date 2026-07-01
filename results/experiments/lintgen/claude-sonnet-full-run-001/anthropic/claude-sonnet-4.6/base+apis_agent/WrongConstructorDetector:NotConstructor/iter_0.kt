package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UMethod

class WrongConstructorDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "NotConstructor",
            briefDescription = "Not a Constructor",
            explanation = """
                This check catches methods that look like they were intended to be constructors, \
                but aren't.

                A method that has the same name as its containing class but has a return type is \
                not a constructor — it is a regular method. This is a common mistake, especially \
                when converting code or when the return type is accidentally added.
            """,
            category = Category.CORRECTNESS,
            priority = 7,
            severity = Severity.WARNING,
            implementation = Implementation(
                WrongConstructorDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }

    override fun getApplicableMethodNames(): List<String>? = null

    override fun applicableSuperClasses(): List<String>? = null

    override fun getApplicableUastTypes() = listOf(UMethod::class.java)

    override fun createUastHandler(context: JavaContext) = object : org.jetbrains.uast.visitor.AbstractUastVisitor() {

        override fun visitMethod(node: UMethod): Boolean {
            // If it's a real constructor, skip it
            if (node.isConstructor) {
                return false
            }

            val psiMethod = node.javaPsi as? PsiMethod ?: return false

            // Get the containing class name
            val containingClass = psiMethod.containingClass ?: return false
            val className = containingClass.name ?: return false

            // Check if the method name matches the class name (looks like a constructor)
            if (node.name == className) {
                context.report(
                    ISSUE,
                    node,
                    context.getNameLocation(node),
                    "Method `${node.name}` looks like a constructor but has a return type; " +
                            "this is not a constructor"
                )
            }

            return false
        }
    }
}