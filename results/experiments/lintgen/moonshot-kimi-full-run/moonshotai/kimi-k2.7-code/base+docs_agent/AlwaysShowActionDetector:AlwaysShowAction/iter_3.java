package com.android.tools.lint.detector.api;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.google.common.annotations.Beta;
import lombok.ast.ConstructorInvocation;
import lombok.ast.MethodInvocation;
import lombok.ast.Node;
import lombok.ast.AstVisitor;

import java.util.Collection;
import java.util.List;

public abstract class Detector {
    ...
    public interface XmlScanner extends Scanner { ... }
    public interface JavaScanner extends Scanner {
        @Nullable
        List<Class<? extends Node>> getApplicableNodeTypes();
        @Nullable
        List<String> getApplicableMethodNames();
        @Nullable
        List<String> getApplicableReferenceNames();
        @Nullable
        List<String> getApplicableConstructorTypes();
        boolean appliesToResourceRefs();
        @Nullable
        List<String> applicableSuperClasses();

        void visitMethod(@NonNull JavaContext context, @Nullable AstVisitor visitor, @NonNull MethodInvocation node);
        void visitConstructor(@NonNull JavaContext context, @Nullable AstVisitor visitor, @NonNull ConstructorInvocation node);
        void visitReference(@NonNull JavaContext context, @Nullable AstVisitor visitor, @NonNull Node node);
        void visitResourceReference(@NonNull JavaContext context, @Nullable AstVisitor visitor, @NonNull Node node, @NonNull ResourceType type, @NonNull String name, boolean isFramework);
        void visitAnnotationUsage(@NonNull JavaContext context, @NonNull AnnotationUsage usage, @NonNull Node node, @NonNull AnnotationInfo annotation, @Nullable AnnotationInfo value);
    }
}