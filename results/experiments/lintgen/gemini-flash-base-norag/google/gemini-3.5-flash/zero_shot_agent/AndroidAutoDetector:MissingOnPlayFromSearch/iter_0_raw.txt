package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.*;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NonNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public class AndroidAutoDetector extends Detector implements Detector.XmlScanner, Detector.SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingOnPlayFromSearch",
            "Missing `onPlayFromSearch`",
            "To support voice searches on Android Auto, in addition to adding an " +
                    "`intent-filter` for the action `onPlayFromSearch`, you also need to " +
                    "override and implement `onPlayFromSearch(String query, Bundle bundle)`",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(
                    AndroidAutoDetector.class,
                    EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE)
            )
    );

    private boolean mHasSearchIntentFilter = false;
    private Location mIntentFilterLocation = null;
    private boolean mHasOnPlayFromSearch = false;

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        mHasSearchIntentFilter = false;
        mIntentFilterLocation = null;
        mHasOnPlayFromSearch = false;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("action");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
        if ("android.media.action.MEDIA_PLAY_FROM_SEARCH".equals(name)) {
            mHasSearchIntentFilter = true;
            mIntentFilterLocation = context.getLocation(element);
        }
    }

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                "android.media.session.MediaSession.Callback",
                "android.support.v4.media.session.MediaSessionCompat.Callback",
                "androidx.media.MediaSessionCompat.Callback"
        );
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        PsiMethod[] methods = declaration.findMethodsByName("onPlayFromSearch", true);
        for (PsiMethod method : methods) {
            PsiParameterList parameterList = method.getParameterList();
            if (parameterList.getParametersCount() == 2) {
                PsiParameter[] parameters = parameterList.getParameters();
                PsiType type1 = parameters[0].getType();
                PsiType type2 = parameters[1].getType();
                if (isStringType(type1) && isBundleType(type2)) {
                    mHasOnPlayFromSearch = true;
                    break;
                }
            }
        }
    }

    private boolean isStringType(@Nullable PsiType type) {
        if (type == null) return false;
        String canonical = type.getCanonicalText();
        return "java.lang.String".equals(canonical) || "String".equals(canonical);
    }

    private boolean isBundleType(@Nullable PsiType type) {
        if (type == null) return false;
        String canonical = type.getCanonicalText();
        return "android.os.Bundle".equals(canonical) || "Bundle".equals(canonical);
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        if (mHasSearchIntentFilter && !mHasOnPlayFromSearch && mIntentFilterLocation != null) {
            context.report(
                    ISSUE,
                    mIntentFilterLocation,
                    "Missing `onPlayFromSearch` implementation in a `MediaSession.Callback` to support voice searches"
            );
        }
    }
}