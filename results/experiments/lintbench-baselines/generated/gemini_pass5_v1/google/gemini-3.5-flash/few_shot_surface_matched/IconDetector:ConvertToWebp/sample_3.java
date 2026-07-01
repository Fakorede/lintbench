package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Incident;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiMethod;
import java.io.File;
import java.util.Collection;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ConvertToWebp",
                    "Convert to WebP",
                    "The WebP format is typically more compact than PNG and JPEG. As of Android 4.2.1 "
                            + "it supports transparency and lossless conversion as well. Note that there is a "
                            + "quickfix in the IDE which lets you perform conversion. Previously, launcher icons "
                            + "were required to be in the PNG format but that restriction is no longer there, "
                            + "so lint now flags these.",
                    Category.ICONS,
                    5,
                    Severity.WARNING,
                    new Implementation(IconDetector.class, Scope.JAVA_AND_RESOURCE_FILES));

    public IconDetector() {}

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        super.beforeCheckRootProject(context);
    }

    @Override
    public void afterCheckEachProject(@NonNull Context context) {
        super.afterCheckEachProject(context);
    }

    @Override
    public boolean filterIncident(@NonNull Incident incident, @NonNull Context context) {
        return super.filterIncident(incident, context);
    }

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        return true;
    }

    @Nullable
    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
    }

    @Nullable
    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return null;
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @NonNull PsiMethod method) {
    }

    @Override
    public void visitCallExpression(@NonNull JavaContext context, @NonNull UCallExpression node, @NonNull PsiMethod method) {
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
    }

    @Override
    public void visitSimpleNameReferenceExpression(@NonNull JavaContext context, @NonNull USimpleNameReferenceExpression node) {
    }

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return null;
    }
}