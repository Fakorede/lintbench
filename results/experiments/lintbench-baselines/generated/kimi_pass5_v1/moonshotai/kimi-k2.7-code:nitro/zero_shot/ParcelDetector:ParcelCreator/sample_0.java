package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.ClassContext;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

import java.util.Collections;
import java.util.List;

public class ParcelDetector extends Detector implements Detector.ClassScanner {

    private static final String PARCELABLE = "android/os/Parcelable";
    private static final String CREATOR = "CREATOR";
    private static final String CREATOR_TYPE = "Landroid/os/Parcelable$Creator;";

    public static final Issue ISSUE = Issue.create(
            "ParcelCreator",
            "Missing `Parcelable.CREATOR` field",
            "Classes implementing the `Parcelable` interface must also have a static field "
                    + "called `CREATOR`, which is an object implementing the "
                    + "`Parcelable.Creator` interface.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(ParcelDetector.class, Scope.CLASS_FILE_SCOPE)
    );

    @Override
    public List<String> getApplicableSuperClasses() {
        return Collections.singletonList(PARCELABLE);
    }

    @Override
    public void visitClass(ClassContext context, ClassNode classNode) {
        int access = classNode.access;
        if ((access & Opcodes.ACC_INTERFACE) != 0
                || (access & Opcodes.ACC_ABSTRACT) != 0) {
            return;
        }

        if (classNode.fields != null) {
            for (FieldNode field : classNode.fields) {
                if (CREATOR.equals(field.name)
                        && (field.access & Opcodes.ACC_STATIC) != 0
                        && CREATOR_TYPE.equals(field.desc)) {
                    return;
                }
            }
        }

        context.report(
                ISSUE,
                classNode,
                null,
                "This class implements `Parcelable` but does not define a static `CREATOR` "
                        + "field of type `Parcelable.Creator`"
        );
    }
}