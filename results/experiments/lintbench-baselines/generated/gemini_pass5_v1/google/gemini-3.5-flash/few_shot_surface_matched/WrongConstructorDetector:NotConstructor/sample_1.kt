package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiClass
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UElement

class WrongConstructorDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "NotConstructor",
            briefDescription = "Method name matches class name",
            explanation = """
                Constructors do not have a return type, not even void. If you specify a return \
                type for a method with the same name as its containing class, it is treated as \
                a regular method, not a constructor.
            """.trimIndent(),
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(
                WrongConstructorDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        }
    }

    override fun getApplicableUastTypes(): List<Class<out UElement>> {
        return listOf(UMethod::class.java)
    }

    override fun createUastHandler(context: JavaContext): UElementHandler {
        return object : UElementHandler() {
            override fun visitMethod(node: UMethod) {
                val psiMethod = node.javaPsi
                if (psiMethod.isConstructor) {
                    return
                }
                val psiClass = psiMethod.containingClass ?: return
                val className = psiClass.name ?: return
                if (psiMethod.name == className) {
                    val location = context.getNameLocation(node)
                    context.report(
                        ISSUE,
                        node,
                        location,
                        "This method has the same name as the class; did you mean to make it a constructor?"
                    )
                }
            }
        }
    }
}