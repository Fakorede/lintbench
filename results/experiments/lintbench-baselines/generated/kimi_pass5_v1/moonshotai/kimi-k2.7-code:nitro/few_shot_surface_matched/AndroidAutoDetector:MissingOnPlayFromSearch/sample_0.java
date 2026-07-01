package com.android.tools.lint.checks;

import static com.android.SdkConstants.ANDROID_MANIFEST_XML;
import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.TAG_ACTION;
import static com.android.SdkConstants.TAG_INTENT_FILTER;
import static com.android.SdkConstants.TAG_SERVICE;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Lint;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.w3c.dom.Element;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String MEDIA_BROWSER_SERVICE =
            "android.media.browse.MediaBrowserService";

    public static final Issue MISSING_ON_PLAY_FROM_SEARCH =
            Issue.create(
                    "MissingOnPlayFromSearch",
                    "Missing onPlayFromSearch",
                    "To support voice searches on Android Auto, in addition to declaring a"
                            + " MediaBrowserService with the `android.media.browse.MediaBrowserService`"
                            + " intent filter, you must override `onPlayFromSearch(String, Bundle)`"
                            + " in your service.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            AndroidAutoDetector.class, Scope.MANIFEST_SCOPE, Scope.JAVA_FILE_SCOPE));

    private boolean mHasMediaBrowserService;

    public AndroidAutoDetector() {}

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        String name = file.getName();
        return ANDROID_MANIFEST_XML.equals(name)
                || name.endsWith(".java")
                || name.endsWith(".kt");
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_SERVICE);
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mHasMediaBrowserService = false;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        for (Element child : Lint.getChildren(element)) {
            if (TAG_INTENT_FILTER.equals(child.getTagName())) {
                for (Element action : Lint.getChildren(child)) {
                    if (TAG_ACTION.equals(action.getTagName())) {
                        String name = action.getAttributeNS(ANDROID_URI, ATTR_NAME);
                        if (MEDIA_BROWSER_SERVICE.equals(name)) {
                            mHasMediaBrowserService = true;
                            return;
                        }
                    }
                }
            }
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                "android.service.media.MediaBrowserService",
                "android.support.v4.media.MediaBrowserServiceCompat",
                "androidx.media.MediaBrowserServiceCompat");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (!mHasMediaBrowserService) {
            return;
        }
        for (PsiMethod method : declaration.getMethods()) {
            if (isOnPlayFromSearch(method)) {
                return;
            }
        }
        context.report(
                MISSING_ON_PLAY_FROM_SEARCH,
                declaration,
                context.getNameLocation(declaration),
                "To support voice searches on Android Auto, override onPlayFromSearch(String, Bundle)");
    }

    @Override
    public void visitMethod(
            @NonNull JavaContext context, @NonNull UMethod node, @NonNull PsiMethod method) {
        // Method-level analysis is performed by iterating the class methods in visitClass.
    }

    private static boolean isOnPlayFromSearch(@NonNull PsiMethod method) {
        if (!"onPlayFromSearch".equals(method.getName())) {
            return false;
        }
        if (method.getParameterList().getParametersCount() != 2) {
            return false;
        }
        PsiParameter[] parameters = method.getParameterList().getParameters();
        return parameters[0].getType().equalsToText("java.lang.String")
                && parameters[1].getType().equalsToText("android.os.Bundle");
    }
}