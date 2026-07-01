package com.android.tools.lint.checks

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ClassContext
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode

class FragmentDetector : Detector(), Detector.ClassScanner {

    companion object {
        private const val ANDROID_APP_FRAGMENT = "android/app/Fragment"
        private const val ANDROID_SUPPORT_FRAGMENT = "android/support/v4/app/Fragment"

        val ISSUE: Issue = Issue.create(
            id = "ValidFragment",
            briefDescription = "Fragment not instantiatable",
            explanation = """
                Every fragment must have an empty constructor, so it can be instantiated \
                when restoring its activity's state. It is strongly recommended that \
                subclasses do not have other constructors with parameters, since these \
                constructors will not be called when the fragment is re-instantiated; \
                instead, arguments can be supplied by the caller with `setArguments(Bundle)` \
                and later retrieved by the Fragment with `getArguments()`.

                Note that this is no longer true when you are using \
                `androidx.fragment.app.Fragment`; with the `FragmentFactory` you can supply \
                any arguments you want (as of version androidx version 1.1).
            """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            implementation = Implementation(
                FragmentDetector::class.java,
                Scope.CLASS_FILE_SCOPE
            )
        )
    }

    override fun applicableSuperClasses(): List<String> = listOf(
        ANDROID_APP_FRAGMENT,
        ANDROID_SUPPORT_FRAGMENT
    )

    override fun visitClass(context: ClassContext, classNode: ClassNode) {
        if (classNode.isInterface || classNode.isAbstract) {
            return
        }

        val constructors = classNode.methods.filter { it.name == "<init>" }

        var hasPublicDefaultConstructor = false
        var firstParameterizedConstructor: MethodNode? = null

        for (constructor in constructors) {
            if (constructor.desc == "()V") {
                if (constructor.isPublic) {
                    hasPublicDefaultConstructor = true
                }
            } else if (firstParameterizedConstructor == null) {
                firstParameterizedConstructor = constructor
            }
        }

        val location = context.getLocation(classNode)

        if (!hasPublicDefaultConstructor) {
            context.report(
                ISSUE,
                location,
                "This fragment class should provide a public default no-arg constructor"
            )
        } else if (firstParameterizedConstructor != null) {
            context.report(
                ISSUE,
                location,
                "This fragment class should not have constructors with parameters; use `setArguments(Bundle)` instead"
            )
        }
    }

    private val ClassNode.isInterface: Boolean
        get() = access and Opcodes.ACC_INTERFACE != 0

    private val ClassNode.isAbstract: Boolean
        get() = access and Opcodes.ACC_ABSTRACT != 0

    private val MethodNode.isPublic: Boolean
        get() = access and Opcodes.ACC_PUBLIC != 0
}