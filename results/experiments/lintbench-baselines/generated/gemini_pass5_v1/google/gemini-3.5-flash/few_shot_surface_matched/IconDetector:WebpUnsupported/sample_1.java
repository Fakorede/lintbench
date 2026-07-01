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
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.USimpleNameReferenceExpression;
import org.w3c.dom.Element;

public class IconDetector extends Detector implements SourceCodeScanner, XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "WebpUnsupported",
                    "WebP Unsupported",
                    "The WebP format requires Android 4.0 (API 15). Certain features, "
                            + "such as lossless encoding and transparency, requires Android 4.2.1 "
                            + "(API 18; API 17 is 4.2.0.)",
                    Category.ICONS,
                    6,
                    Severity.ERROR,
                    new Implementation(IconDetector.class, Scope.RESOURCE_AND_SOURCE_FILES_SCOPE));

    public IconDetector() {}

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {}

    @Override
    public void afterCheckEachProject(@NonNull Context context) {}

    @Override
    public void filterIncident(@NonNull Incident incident) {}

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
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {}

    @Nullable
    @Override
    public UElementHandler createUastHandler(@NonNull JavaContext context) {
        return null;
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @NonNull UMethod method) {}

    @Override
    public void visitCallExpression(@NonNull JavaContext context, @NonNull UCallExpression node) {}

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {}

    @Override
    public void visitSimpleNameReferenceExpression(
            @NonNull JavaContext context, @NonNull USimpleNameReferenceExpression node) {}

    @Nullable
    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return null;
    }
}