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
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String TAG_SERVICE = "service";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_ACTION = "action";
    private static final String ATTR_NAME = "name";
    private static final String ACTION_MEDIA_BROWSER_SERVICE =
            "android.media.browse.MediaBrowserService";
    private static final String CLASS_MEDIA_SESSION_CALLBACK =
            "android.media.session.MediaSession.Callback";
    private static final String CLASS_MEDIA_SESSION_COMPAT_CALLBACK =
            "android.support.v4.media.session.MediaSessionCompat.Callback";

    private static final String EXPLANATION =
            "To support voice searches on Android Auto, in addition to adding an intent-filter "
                    + "for the action android.media.browse.MediaBrowserService, you also need to "
                    + "override and implement onPlayFromSearch(String, Bundle) in your "
                    + "MediaSession.Callback.";

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    AndroidAutoDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "MissingOnPlayFromSearch",
                    "Missing `onPlayFromSearch`",
                    EXPLANATION,
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private boolean mHasMediaBrowserService;

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return false;
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
        if (mHasMediaBrowserService) {
            return;
        }
        if (TAG_SERVICE.equals(element.getTagName()) && isMediaBrowserService(element)) {
            mHasMediaBrowserService = true;
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                CLASS_MEDIA_SESSION_CALLBACK, CLASS_MEDIA_SESSION_COMPAT_CALLBACK);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (!mHasMediaBrowserService || implementsOnPlayFromSearch(declaration)) {
            return;
        }

        String message =
                "MediaSession.Callback must override onPlayFromSearch(String, Bundle) to support "
                        + "voice search on Android Auto";
        context.report(ISSUE, declaration, context.getNameLocation(declaration), message);
    }

    private static boolean isMediaBrowserService(@NonNull Element service) {
        NodeList intentFilters = service.getElementsByTagName(TAG_INTENT_FILTER);
        for (int i = 0; i < intentFilters.getLength(); i++) {
            Element intentFilter = (Element) intentFilters.item(i);
            NodeList actions = intentFilter.getElementsByTagName(TAG_ACTION);
            for (int j = 0; j < actions.getLength(); j++) {
                Element action = (Element) actions.item(j);
                String name = action.getAttributeNS(ANDROID_URI, ATTR_NAME);
                if (ACTION_MEDIA_BROWSER_SERVICE.equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean implementsOnPlayFromSearch(@NonNull UClass cls) {
        for (UMethod method : cls.getMethods()) {
            PsiMethod psiMethod = method.getJavaPsi();
            if (psiMethod == null) {
                continue;
            }
            if (!"onPlayFromSearch".equals(psiMethod.getName())) {
                continue;
            }
            PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
            if (parameters.length != 2) {
                continue;
            }
            String first = parameters[0].getType().getCanonicalText();
            String second = parameters[1].getType().getCanonicalText();
            if ("java.lang.String".equals(first) && "android.os.Bundle".equals(second)) {
                return true;
            }
        }
        return false;
    }
}