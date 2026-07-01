package com.android.tools.lint.checks

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod

class WrongConstructorDetector : Detector(), SourceCodeScanner {

    companion object {
        @JvmField
        val ISSUE = Issue.create(
            id = "NotConstructor",
            briefDescription = "Method looks like a constructor but is not",
            explanation = """
                A method has been defined with the same name as the containing class, but with a return \
                type. This is likely a mistake where a constructor was intended but a return type (such \
                as `void`) was accidentally specified.
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.WARNING,
            implementation = Implementation(WrongConstructorDetector::class.java, Scope.JAVA_FILE_SCOPE)
        )
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

                val containingClass = psiMethod.containingClass ?: return
                if (containingClass.isAnnotationType) {
                    return
                }

                val className = containingClass.name ?: return
                val methodName = psiMethod.name

                if (methodName == className) {
                    val location = context.getNameLocation(node)
                    val message = "Method has the same name as its class, but is not a constructor (did you specify a return type?)"
                    context.report(Incident(ISSUE, node, location, message))
                }
            }
        }
    }
}