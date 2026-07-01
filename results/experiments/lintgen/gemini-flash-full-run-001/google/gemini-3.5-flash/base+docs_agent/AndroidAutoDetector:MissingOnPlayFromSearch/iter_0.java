package com.android.tools.lint.checks;

import com.android.SdkConstants;
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
import com.intellij.psi.PsiMethod;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.annotations.NonNull;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Element;

public class AndroidAutoDetector extends Detector implements XmlScanner, SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
        "MissingOnPlayFromSearch",
        "Missing `onPlayFromSearch`",
        "To support voice searches on Android Auto, in addition to adding an `intent-filter` " +
        "for the action `onPlayFromSearch`, you also need to override and implement " +
        "`onPlayFromSearch(String query, Bundle bundle)`",
        Category.CORRECTNESS,
        6,
        Severity.ERROR,
        new Implementation(
            AndroidAutoDetector.class,
            EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE)
        )
    );

    private static class CallbackClassInfo {
        final UClass clazz;
        final Location location;

        CallbackClassInfo(UClass clazz, Location location) {
            this.clazz = clazz;
            this.location = location;
        }
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("action");
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String name = element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME);
        if ("android.media.action.MEDIA_PLAY_FROM_SEARCH".equals(name)) {
            context.getMainProject().putProperty("AndroidAutoDetector.has_play_from_search_intent", Boolean.TRUE);
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
            "android.media.session.MediaSession.Callback",
            "android.support.v4.media.session.MediaSessionCompat.Callback"
        );
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (declaration.isInterface() || context.getEvaluator().isAbstract(declaration)) {
            return;
        }

        PsiMethod[] methods = declaration.findMethodsByName("onPlayFromSearch", true);
        boolean implementsPlayFromSearch = false;
        for (PsiMethod method : methods) {
            PsiClass containingClass = method.getContainingClass();
            if (containingClass != null) {
                String qualifiedName = containingClass.getQualifiedName();
                if ("android.media.session.MediaSession.Callback".equals(qualifiedName) ||
                    "android.support.v4.media.session.MediaSessionCompat.Callback".equals(qualifiedName)) {
                    continue;
                }
            }
            if (method.getParameterList().getParametersCount() == 2) {
                implementsPlayFromSearch = true;
                break;
            }
        }

        if (!implementsPlayFromSearch) {
            @SuppressWarnings("unchecked")
            List<CallbackClassInfo> list = (List<CallbackClassInfo>) context.getMainProject().getProperty("AndroidAutoDetector.callback_classes");
            if (list == null) {
                list = new ArrayList<>();
                context.getMainProject().putProperty("AndroidAutoDetector.callback_classes", list);
            }
            list.add(new CallbackClassInfo(declaration, context.getNameLocation(declaration)));
        }
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        Boolean hasIntent = (Boolean) context.getMainProject().getProperty("AndroidAutoDetector.has_play_from_search_intent");
        if (hasIntent != null && hasIntent) {
            @SuppressWarnings("unchecked")
            List<CallbackClassInfo> list = (List<CallbackClassInfo>) context.getMainProject().getProperty("AndroidAutoDetector.callback_classes");
            if (list != null) {
                for (CallbackClassInfo info : list) {
                    context.report(
                        ISSUE,
                        info.location,
                        "This class extends `MediaSession.Callback` but does not override `onPlayFromSearch`. " +
                        "To support voice searches on Android Auto, you must override and implement `onPlayFromSearch` " +
                        "when your manifest declares the `MEDIA_PLAY_FROM_SEARCH` intent filter."
                    );
                }
            }
        }
    }
}