package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.w3c.dom.Element;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String ACTION_ON_PLAY_FROM_SEARCH = "onPlayFromSearch";
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    private static final String MEDIA_BROWSER_SERVICE = "android.service.media.MediaBrowserService";
    private static final String MEDIA_BROWSER_SERVICE_COMPAT =
            "android.support.v4.media.MediaBrowserServiceCompat";
    private static final String MEDIA_BROWSER_SERVICE_COMPATX =
            "androidx.media.MediaBrowserServiceCompat";

    public static final Issue ISSUE =
            Issue.create(
                    "MissingOnPlayFromSearch",
                    "Missing onPlayFromSearch Implementation",
                    "To support voice searches on Android Auto, in addition to adding an"
                            + " `intent-filter` for the action `onPlayFromSearch`, you must also"
                            + " override and implement `onPlayFromSearch(String query, Bundle bundle)`"
                            + " in your media browser service.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            AndroidAutoDetector.class,
                            EnumSet.of(Scope.MANIFEST_SCOPE, Scope.JAVA_FILE_SCOPE)));

    private boolean mHasVoiceSearchAction;
    private XmlContext mActionContext;
    private Element mActionElement;
    private final List<Candidate> mCandidates = new ArrayList<>();

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        return true;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("action");
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        super.beforeCheckRootProject(context);
        mHasVoiceSearchAction = false;
        mActionContext = null;
        mActionElement = null;
        mCandidates.clear();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(ANDROID_NS, "name");
        if (ACTION_ON_PLAY_FROM_SEARCH.equals(name)) {
            mHasVoiceSearchAction = true;
            mActionContext = context;
            mActionElement = element;
        }
    }

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                MEDIA_BROWSER_SERVICE,
                MEDIA_BROWSER_SERVICE_COMPAT,
                MEDIA_BROWSER_SERVICE_COMPATX);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        String qualifiedName = declaration.getQualifiedName();
        if (MEDIA_BROWSER_SERVICE.equals(qualifiedName)
                || MEDIA_BROWSER_SERVICE_COMPAT.equals(qualifiedName)
                || MEDIA_BROWSER_SERVICE_COMPATX.equals(qualifiedName)) {
            return;
        }
        mCandidates.add(new Candidate(context, declaration));
    }

    @Nullable
    @Override
    public List<String> getApplicableMethods() {
        return Collections.singletonList("onPlayFromSearch");
    }

    @Override
    public void visitMethod(
            @NonNull JavaContext context,
            @NonNull UMethod node,
            @NonNull PsiMethod method) {
        if (!"onPlayFromSearch".equals(method.getName())) {
            return;
        }
        PsiParameterList parameterList = method.getParameterList();
        if (parameterList.getParametersCount() != 2) {
            return;
        }
        PsiParameter[] parameters = parameterList.getParameters();
        if (!"java.lang.String".equals(parameters[0].getType().getCanonicalText())) {
            return;
        }
        if (!"android.os.Bundle".equals(parameters[1].getType().getCanonicalText())) {
            return;
        }
        UClass containingClass = node.getContainingClass();
        if (containingClass == null) {
            return;
        }
        String containingName = containingClass.getQualifiedName();
        for (Candidate candidate : mCandidates) {
            if (containingName != null
                    && containingName.equals(candidate.mClass.getQualifiedName())) {
                candidate.mHasImplementation = true;
                break;
            }
        }
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        if (!mHasVoiceSearchAction) {
            return;
        }
        boolean reportedClass = false;
        for (Candidate candidate : mCandidates) {
            if (!candidate.mHasImplementation) {
                reportedClass = true;
                candidate.mContext.report(
                        ISSUE,
                        candidate.mClass,
                        candidate.mContext.getNameLocation(candidate.mClass),
                        "To support voice searches on Android Auto, override"
                                + " `onPlayFromSearch(String, Bundle)`");
            }
        }
        if (!reportedClass && mActionContext != null && mActionElement != null) {
            mActionContext.report(
                    ISSUE,
                    mActionElement,
                    mActionContext.getLocation(mActionElement),
                    "To support voice searches on Android Auto, override"
                            + " `onPlayFromSearch(String, Bundle)` in a media browser service");
        }
    }

    private static class Candidate {
        final JavaContext mContext;
        final UClass mClass;
        boolean mHasImplementation;

        Candidate(@NonNull JavaContext context, @NonNull UClass clazz) {
            mContext = context;
            mClass = clazz;
        }
    }
}