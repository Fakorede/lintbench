package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;

import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;
import org.jetbrains.uast.USimpleNameReferenceExpression;

import java.util.Collections;
import java.util.List;

public class AndroidAutoDetector extends Detector implements Detector.JavaContextScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingOnPlayFromSearch",
            "To support voice searches on Android Auto, you need to override and implement `onPlayFromSearch(String query, Bundle bundle)`.",
            "Supporting voice search in your app requires implementing the `onPlayFromSearch` method. This is necessary for compatibility with Android Auto.",
            Category.CORRECTNESS,
            5,
            Severity.WARNING,
            new Implementation(
                    AndroidAutoDetector.class,
                    Scope.JAVA_FILE_SCOPE
            )
    );

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("onPlayFromSearch");
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @NonNull UMethod method) {
        if (method.getName().equals("onPlayFromSearch")) {
            // Check the method signature to ensure it matches onPlayFromSearch(String query, Bundle extras)
            List<UParameter> parameters = method.getUastParameters();
            if (parameters.size() == 2 &&
                    "java.lang.String".equals(parameters.get(0).getType().getPresentableText()) &&
                    "android.os.Bundle".equals(parameters.get(1).getType().getPresentableText())) {
                return;
            }
        }

        UClass uClass = method.getContainingClass();
        if (uClass != null && isMediaBrowserService(uClass)) {
            Location location = context.getLocation(method);
            context.report(ISSUE, method, location,
                    "Missing implementation of onPlayFromSearch(String query, Bundle extras)");
        }
    }

    private boolean isMediaBrowserService(UClass uClass) {
        String qualifiedName = uClass.getQualifiedName();
        return qualifiedName != null && (qualifiedName.endsWith("MediaBrowserService") || qualifiedName.endsWith("MediaBrowserServiceCompat"));
    }
}