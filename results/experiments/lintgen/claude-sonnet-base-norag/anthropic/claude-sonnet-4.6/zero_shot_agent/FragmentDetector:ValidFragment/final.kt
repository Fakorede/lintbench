package com.android.tools.lint.checks

import com.android.SdkConstants.CLASS_FRAGMENT
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ClassContext
import com.android.tools.lint.detector.api.ClassScanner
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodNode
import java.util.EnumSet

/**
 * Checks that Fragment subclasses can be instantiated via reflection,
 * i.e. they have a public no-argument constructor and are not inner classes.
 */
class FragmentDetector : Detector(), ClassScanner {

    companion object {
        private val IMPLEMENTATION = Implementation(
            FragmentDetector::class.java,
            EnumSet.of(Scope.CLASS_FILE)
        )

        @JvmField
        val ISSUE: Issue = Issue.create(
            id = "ValidFragment",
            briefDescription = "Fragment not instantiatable",
            explanation = """
                From the Fragment documentation:
                **Every** fragment must have an empty constructor, so it can be instantiated \
                when restoring its activity's state. It is strongly recommended that subclasses \
                do not have other constructors with parameters, since these constructors will \
                not be called when the fragment is re-instantiated; instead, arguments can be \
                supplied by the caller with `setArguments(Bundle)` and later retrieved by the \
                Fragment with `getArguments()`.

                Note that this is no longer true when you are using \
                `androidx.fragment.app.Fragment`; with the `FragmentFactory` you can supply \
                any arguments you want (as of version androidx version 1.1).
                """,
            category = Category.CORRECTNESS,
            priority = 6,
            severity = Severity.ERROR,
            moreInfo = "https://developer.android.com/reference/android/app/Fragment.html#Fragment()",
            implementation = IMPLEMENTATION
        )

        private const val CLASS_V4_FRAGMENT = "android/support/v4/app/Fragment"
        private const val ANDROIDX_FRAGMENT_CLASS = "androidx/fragment/app/Fragment"
        private const val CONSTRUCTOR_NAME = "<init>"
    }

    override fun applicableSuperClasses(): List<String> {
        return listOf(CLASS_FRAGMENT, CLASS_V4_FRAGMENT, ANDROIDX_FRAGMENT_CLASS)
    }

    override fun checkClass(context: ClassContext, classNode: ClassNode) {
        // Skip abstract classes - they don't need to be instantiated directly
        if (classNode.access and Opcodes.ACC_ABSTRACT != 0) {
            return
        }

        // Skip interfaces
        if (classNode.access and Opcodes.ACC_INTERFACE != 0) {
            return
        }

        // Check if this is a non-static inner class
        val outerClass = classNode.outerClass
        if (outerClass != null) {
            val isStatic = classNode.access and Opcodes.ACC_STATIC != 0
            if (!isStatic) {
                context.report(
                    ISSUE,
                    context.getLocation(classNode),
                    "This fragment inner class should be static (${classNode.name.replace('/', '.')})"
                )
                return
            }
        }

        // Also check for anonymous inner classes (they have a $ in the name and a numeric suffix)
        val name = classNode.name
        if (name.contains('$')) {
            val lastPart = name.substringAfterLast('$')
            if (lastPart.all { it.isDigit() }) {
                // Anonymous inner class
                context.report(
                    ISSUE,
                    context.getLocation(classNode),
                    "Fragments should be static inner classes or top-level classes, not anonymous inner classes (${name.replace('/', '.')})"
                )
                return
            }
        }

        // Look for a public no-argument constructor
        @Suppress("UNCHECKED_CAST")
        val methods = classNode.methods as? List<MethodNode> ?: return

        var hasExplicitConstructor = false
        var hasDefaultConstructor = false

        for (method in methods) {
            if (method.name == CONSTRUCTOR_NAME) {
                hasExplicitConstructor = true
                // Check if it's a no-argument constructor
                if (method.desc == "()V") {
                    // Check if it's public
                    if (method.access and Opcodes.ACC_PUBLIC != 0) {
                        hasDefaultConstructor = true
                    }
                }
            }
        }

        if (hasExplicitConstructor && !hasDefaultConstructor) {
            context.report(
                ISSUE,
                context.getLocation(classNode),
                "This fragment should provide a default constructor (a public constructor " +
                    "with no arguments) (`${classNode.name.replace('/', '.')}`)"
            )
        }
    }
}