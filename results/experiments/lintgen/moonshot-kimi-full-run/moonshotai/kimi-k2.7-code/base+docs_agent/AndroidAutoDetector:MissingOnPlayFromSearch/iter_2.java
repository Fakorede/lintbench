package com.android.tools.lint.checks;

import com.android.SdkConstants;
import com.android.tools.lint.client.api.JavaEvaluator;
import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;

import com.intellij.psi.PsiClass;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;
import org.w3c.dom.Element;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public class AndroidAutoDetector extends Detector implements XmlScanner, SourceCodeScanner {

    private static final String ACTION_PLAY_FROM_SEARCH = "android.media.action.PLAY_FROM_SEARCH";
    private static final String METHOD_ON_PLAY_FROM_SEARCH = "onPlayFromSearch";

    private static final String MEDIA_SESSION_CALLBACK =
            "android.media.session.MediaSession$Callback";
    private static final String MEDIA_SESSION_COMPAT_CALLBACK =
            "android.support.v4.media.session.MediaSessionCompat$Callback";
    private static final String MEDIA_SESSION_COMPAT_CALLBACK_ANDROIDX =
            "androidx.media.session.MediaSessionCompat$Callback";

    public static final Issue ISSUE = Issue.create(
            "MissingOnPlayFromSearch",
            "Missing onPlayFromSearch implementation",
            "To support voice searches on Android Auto, in addition to adding an intent-filter "
                    + "for the action `" + ACTION_PLAY_FROM_SEARCH + "`, you also need to override "
                    + "and implement `" + METHOD_ON_PLAY_FROM_SEARCH
                    + "(String query, Bundle bundle)`.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(
                    AndroidAutoDetector.class,
                    EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE)
            )
    );

    private boolean mHasPlayFromSearchAction;
    private boolean mHasOnPlayFromSearchImplementation;
    private Location mActionLocation;

    @Override
    public void beforeCheckEachProject(@NotNull Context context) {
        mHasPlayFromSearchAction = false;
        mHasOnPlayFromSearchImplementation = false;
        mActionLocation = null;
    }

    @Override
    public void afterCheckEachProject(@NotNull Context context) {
        if (mHasPlayFromSearchAction
                && !mHasOnPlayFromSearchImplementation
                && mActionLocation != null) {
            context.report(
                    ISSUE,
                    mActionLocation,
                    "The manifest declares the `" + ACTION_PLAY_FROM_SEARCH
                            + "` action, but no `" + METHOD_ON_PLAY_FROM_SEARCH
                            + "(String, Bundle)` override was found."
            );
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("action");
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        String actionName = element.getAttributeNS(SdkConstants.ANDROID_URI, "name");
        if (ACTION_PLAY_FROM_SEARCH.equals(actionName)) {
            mHasPlayFromSearchAction = true;
            mActionLocation = context.getLocation(element);
        }
    }

    @Override
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UMethod.class);
    }

    @Override
    public UElementHandler createUastHandler(@NotNull JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitMethod(@NotNull UMethod node) {
                if (!METHOD_ON_PLAY_FROM_SEARCH.equals(node.getName())) {
                    return;
                }

                List<UParameter> parameters = node.getUastParameters();
                if (parameters.size() != 2) {
                    return;
                }

                if (!"java.lang.String".equals(parameters.get(0).getType().getCanonicalText())
                        || !"android.os.Bundle".equals(parameters.get(1).getType().getCanonicalText())) {
                    return;
                }

                PsiClass containingClass = node.getContainingClass();
                if (containingClass == null) {
                    return;
                }

                JavaEvaluator evaluator = context.getEvaluator();
                if (evaluator.extendsClass(containingClass, MEDIA_SESSION_CALLBACK, false)
                        || evaluator.extendsClass(containingClass, MEDIA_SESSION_COMPAT_CALLBACK, false)
                        || evaluator.extendsClass(containingClass, MEDIA_SESSION_COMPAT_CALLBACK_ANDROIDX, false)) {
                    mHasOnPlayFromSearchImplementation = true;
                }
            }
        };
    }
}