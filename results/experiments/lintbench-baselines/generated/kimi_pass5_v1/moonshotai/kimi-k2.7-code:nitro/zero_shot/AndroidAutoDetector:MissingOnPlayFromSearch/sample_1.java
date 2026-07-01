package com.android.tools.lint.checks;

import com.android.tools.lint.client.api.UElementHandler;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.SdkConstants;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class AndroidAutoDetector extends Detector
        implements Detector.XmlScanner, SourceCodeScanner {

    private static final String MEDIA_BROWSER_SERVICE_ACTION =
            "android.media.browse.MediaBrowserService";
    private static final String MEDIA_SESSION_CALLBACK_CLASS =
            "android.media.session.MediaSession$Callback";
    private static final String MEDIA_SESSION_CALLBACK_DOT =
            "android.media.session.MediaSession.Callback";
    private static final String MESSAGE =
            "To support voice searches on Android Auto, you must override "
                    + "MediaSession.Callback#onPlayFromSearch(String, Bundle)";

    public static final Issue ISSUE = Issue.create(
            "MissingOnPlayFromSearch",
            "Missing onPlayFromSearch",
            "To support voice searches on Android Auto, in addition to adding a "
                    + "MediaBrowserService intent filter, you must override and implement "
                    + "MediaSession.Callback#onPlayFromSearch(String, Bundle).",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(AndroidAutoDetector.class,
                    EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST)));

    private boolean mHasMediaBrowserService;
    private final List<CallbackInfo> mMissingCallbacks = new ArrayList<>();

    @Override
    @NotNull
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(SdkConstants.TAG_SERVICE);
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        NodeList filters = element.getElementsByTagName(SdkConstants.TAG_INTENT_FILTER);
        for (int i = 0; i < filters.getLength(); i++) {
            Element filter = (Element) filters.item(i);
            NodeList actions = filter.getElementsByTagName(SdkConstants.TAG_ACTION);
            for (int j = 0; j < actions.getLength(); j++) {
                Element action = (Element) actions.item(j);
                String name = getActionName(action);
                if (MEDIA_BROWSER_SERVICE_ACTION.equals(name)) {
                    mHasMediaBrowserService = true;
                    return;
                }
            }
        }
    }

    @Override
    @NotNull
    public List<Class<? extends UElement>> getApplicableUastTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    @NotNull
    public UElementHandler createUastHandler(@NotNull final JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NotNull UClass node) {
                PsiClass psiClass = (PsiClass) node.getJavaPsi();
                if (psiClass == null) return;
                if (!isExtendingMediaSessionCallback(psiClass)) return;
                if (overridesOnPlayFromSearch(psiClass)) return;

                Location location = context.getLocation(node);
                if (mHasMediaBrowserService) {
                    context.report(ISSUE, location, MESSAGE);
                } else {
                    mMissingCallbacks.add(new CallbackInfo(context, location));
                }
            }
        };
    }

    @Override
    public void beforeCheckProject(@NotNull Context context) {
        mHasMediaBrowserService = false;
        mMissingCallbacks.clear();
    }

    @Override
    public void afterCheckProject(@NotNull Context context) {
        if (!mHasMediaBrowserService) return;
        for (CallbackInfo info : mMissingCallbacks) {
            info.context.report(ISSUE, info.location, MESSAGE);
        }
    }

    private static String getActionName(Element element) {
        String name = element.getAttributeNS(SdkConstants.NS_RESOURCES,
                SdkConstants.ATTR_NAME);
        if (name == null || name.isEmpty()) {
            name = element.getAttribute(SdkConstants.ATTR_NAME);
        }
        return name;
    }

    private static boolean isExtendingMediaSessionCallback(PsiClass psiClass) {
        PsiClass current = psiClass.getSuperClass();
        while (current != null) {
            String qName = current.getQualifiedName();
            if (isMediaSessionCallback(qName)) return true;
            current = current.getSuperClass();
        }
        return false;
    }

    private static boolean isMediaSessionCallback(String qName) {
        return MEDIA_SESSION_CALLBACK_CLASS.equals(qName)
                || MEDIA_SESSION_CALLBACK_DOT.equals(qName);
    }

    private static boolean overridesOnPlayFromSearch(PsiClass psiClass) {
        PsiClass current = psiClass;
        while (current != null) {
            if (isMediaSessionCallback(current.getQualifiedName())) break;
            for (PsiMethod method : current.getMethods()) {
                if (isOnPlayFromSearchMethod(method)) return true;
            }
            current = current.getSuperClass();
        }
        return false;
    }

    private static boolean isOnPlayFromSearchMethod(PsiMethod method) {
        if (!"onPlayFromSearch".equals(method.getName())) return false;
        PsiParameterList list = method.getParameterList();
        if (list.getParametersCount() != 2) return false;
        PsiParameter[] params = list.getParameters();
        String t1 = params[0].getType().getCanonicalText();
        String t2 = params[1].getType().getCanonicalText();
        return ("java.lang.String".equals(t1) || "String".equals(t1))
                && ("android.os.Bundle".equals(t2) || "Bundle".equals(t2));
    }

    private static class CallbackInfo {
        final JavaContext context;
        final Location location;

        CallbackInfo(JavaContext context, Location location) {
            this.context = context;
            this.location = location;
        }
    }
}