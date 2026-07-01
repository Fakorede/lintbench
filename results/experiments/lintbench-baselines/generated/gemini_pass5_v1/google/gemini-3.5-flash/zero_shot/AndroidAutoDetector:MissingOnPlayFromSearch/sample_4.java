package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Project;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NonNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingOnPlayFromSearch",
            "Missing `onPlayFromSearch`",
            "To support voice searches on Android Auto, in addition to adding an `intent-filter` " +
            "for the action `android.media.action.MEDIA_PLAY_FROM_SEARCH`, you also need to " +
            "override and implement `onPlayFromSearch(String query, Bundle bundle)`.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(
                    AndroidAutoDetector.class,
                    Scope.JAVA_FILE_SCOPE
            )
    );

    private Boolean mHasSearchIntentCached = null;

    @Override
    public void beforeCheckProject(@NonNull Context context) {
        mHasSearchIntentCached = null;
    }

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
            "android.media.session.MediaSession.Callback",
            "android.support.v4.media.session.MediaSessionCompat.Callback",
            "androidx.media3.session.MediaSession.Callback"
        );
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (context.getEvaluator().isAbstract(declaration)) {
            return;
        }

        if (!hasPlayFromSearchIntent(context.getProject())) {
            return;
        }

        if (!overridesOnPlayFromSearch(declaration)) {
            context.report(
                ISSUE,
                declaration,
                context.getNameLocation(declaration),
                "This `MediaSession.Callback` should override `onPlayFromSearch` to support Android Auto voice search"
            );
        }
    }

    private boolean hasPlayFromSearchIntent(Project project) {
        if (mHasSearchIntentCached != null) {
            return mHasSearchIntentCached;
        }
        mHasSearchIntentCached = false;
        Document document = project.getMergedManifest();
        if (document != null) {
            NodeList actions = document.getElementsByTagName("action");
            for (int i = 0; i < actions.getLength(); i++) {
                Element action = (Element) actions.item(i);
                String name = action.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
                if (name.isEmpty()) {
                    name = action.getAttribute("android:name");
                }
                if ("android.media.action.MEDIA_PLAY_FROM_SEARCH".equals(name) || "onPlayFromSearch".equals(name)) {
                    mHasSearchIntentCached = true;
                    break;
                }
            }
        }
        return mHasSearchIntentCached;
    }

    private boolean overridesOnPlayFromSearch(UClass clazz) {
        UClass current = clazz;
        while (current != null) {
            String qName = current.getQualifiedName();
            if ("android.media.session.MediaSession.Callback".equals(qName) ||
                "android.support.v4.media.session.MediaSessionCompat.Callback".equals(qName) ||
                "androidx.media3.session.MediaSession.Callback".equals(qName)) {
                break;
            }
            for (UMethod method : current.getMethods()) {
                if ("onPlayFromSearch".equals(method.getName())) {
                    UParameter[] parameters = method.getUastParameters();
                    if (parameters.length == 2) {
                        String type1 = parameters[0].getType().getCanonicalText();
                        String type2 = parameters[1].getType().getCanonicalText();
                        if ("java.lang.String".equals(type1) &&
                            ("android.os.Bundle".equals(type2) || "Bundle".equals(type2))) {
                            return true;
                        }
                    }
                }
            }
            PsiClass superPsi = current.getSuperClass();
            if (superPsi instanceof UClass) {
                current = (UClass) superPsi;
            } else if (superPsi != null) {
                PsiClass psiCurrent = superPsi;
                while (psiCurrent != null) {
                    String psiQName = psiCurrent.getQualifiedName();
                    if ("android.media.session.MediaSession.Callback".equals(psiQName) ||
                        "android.support.v4.media.session.MediaSessionCompat.Callback".equals(psiQName) ||
                        "androidx.media3.session.MediaSession.Callback".equals(psiQName)) {
                        break;
                    }
                    for (PsiMethod method : psiCurrent.getMethods()) {
                        if ("onPlayFromSearch".equals(method.getName())) {
                            PsiParameter[] parameters = method.getParameterList().getParameters();
                            if (parameters.length == 2) {
                                String type1 = parameters[0].getType().getCanonicalText();
                                String type2 = parameters[1].getType().getCanonicalText();
                                if ("java.lang.String".equals(type1) &&
                                    ("android.os.Bundle".equals(type2) || "Bundle".equals(type2))) {
                                    return true;
                                }
                            }
                        }
                    }
                    psiCurrent = psiCurrent.getSuperClass();
                }
                break;
            } else {
                break;
            }
        }
        return false;
    }
}