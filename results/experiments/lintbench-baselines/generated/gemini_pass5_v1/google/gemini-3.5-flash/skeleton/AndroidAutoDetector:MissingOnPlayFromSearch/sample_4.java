package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlScanner;
import com.android.tools.lint.detector.api.Location;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Element;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidAutoDetector.class, EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "MissingOnPlayFromSearch",
                    "Missing `onPlayFromSearch`",
                    "To support voice searches on Android Auto, in addition to adding an intent-filter for the action `onPlayFromSearch`, you also need to override and implement `onPlayFromSearch(String query, Bundle bundle)`",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private static class CallbackClass {
        @NonNull final JavaContext context;
        @NonNull final UClass declaration;
        @NonNull final Location location;

        CallbackClass(@NonNull JavaContext context, @NonNull UClass declaration, @NonNull Location location) {
            this.context = context;
            this.declaration = declaration;
            this.location = location;
        }
    }

    private final List<CallbackClass> mCallbackClasses = new ArrayList<>();
    private boolean mHasSearchIntentFilter = false;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return false;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("action");
    }

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        mHasSearchIntentFilter = false;
        mCallbackClasses.clear();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
        if ("android.media.action.MEDIA_PLAY_FROM_SEARCH".equals(name) || "onPlayFromSearch".equals(name)) {
            mHasSearchIntentFilter = true;
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return java.util.Arrays.asList(
                "android.media.session.MediaSession.Callback",
                "android.support.v4.media.session.MediaSessionCompat.Callback",
                "androidx.media3.session.MediaSession.Callback"
        );
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        boolean implemented = false;
        PsiMethod[] methods = declaration.findMethodsByName("onPlayFromSearch", true);
        for (PsiMethod method : methods) {
            PsiClass containingClass = method.getContainingClass();
            if (containingClass != null) {
                String qualifiedName = containingClass.getQualifiedName();
                if (qualifiedName != null) {
                    String normName = qualifiedName.replace('$', '.');
                    if (!normName.equals("android.media.session.MediaSession.Callback")
                            && !normName.equals("android.support.v4.media.session.MediaSessionCompat.Callback")
                            && !normName.equals("androidx.media3.session.MediaSession.Callback")) {
                        implemented = true;
                        break;
                    }
                }
            }
        }

        if (!implemented) {
            mCallbackClasses.add(new CallbackClass(context, declaration, context.getNameLocation(declaration)));
        }
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        if (mHasSearchIntentFilter) {
            for (CallbackClass callback : mCallbackClasses) {
                callback.context.report(
                        ISSUE,
                        callback.location,
                        "This MediaSession.Callback should override `onPlayFromSearch` to support voice control"
                );
            }
        }
        mCallbackClasses.clear();
    }
}