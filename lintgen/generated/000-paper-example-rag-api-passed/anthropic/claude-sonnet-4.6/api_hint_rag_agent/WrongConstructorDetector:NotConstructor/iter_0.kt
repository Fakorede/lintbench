package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod

/**
 * Detector that finds methods that look like constructors (same name as
 * the enclosing class, no return type declared in source) but are actually
 * regular methods because they have a return type declared in bytecode /
 * PSI (i.e. the developer forgot to omit the return type, or mis-capitalised
 * the class name).
 */
class WrongConstructorDetector : Detector(), Detector.UastScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            WrongConstructorDetector::class.java,
            Scope.JAVA_FILE_SCOPE
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "NotConstructor",
            briefDescription = "Not a Constructor",
            explanation = """
                This check catches methods that look like they were intended to be \
                constructors but aren't. A method has the same name as its enclosing \
                class but also declares a return type, which means it is treated as a \
                regular method rather than a constructor. This is almost always a \
                mistake — either the return type should be removed, or the method \
                name should be changed.
                """,
            category = Category.CORRECTNESS,
            priority = 7,
            severity = Severity.ERROR,
            implementation = IMPLEMENTATION
        )
    }

    // We want to visit every method declaration inside every class.
    override fun getApplicableUastTypes(): List<Class<out UElement>> =
        listOf(UClass::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler =
        object : UElementHandler() {
            override fun visitClass(node: UClass) {
                val className = node.name ?: return

                for (method in node.methods) {
                    checkMethod(context, method, className)
                }
            }
        }

    private fun checkMethod(context: JavaContext, method: UMethod, className: String) {
        // Real constructors are already marked as constructors by the compiler/PSI.
        // We are looking for methods that are NOT constructors but share the class name.
        val psiMethod: PsiMethod = method.javaPsi

        if (psiMethod.isConstructor) {
            // This is a legitimate constructor — nothing to report.
            return
        }

        val methodName = method.name

        // Check whether the method name matches the enclosing class name exactly.
        if (methodName != className) {
            return
        }

        // At this point we have a non-constructor method whose name equals the class
        // name. This is almost certainly a mistake (e.g. the developer wrote a return
        // type by accident, or capitalised the method name to match the class).
        val location = context.getNameLocation(method)
        context.report(
            ISSUE,
            method,
            location,
            "Method `$methodName` looks like a constructor but has a return type; " +
                "did you mean to declare a constructor (remove the return type)?"
        )
    }
}