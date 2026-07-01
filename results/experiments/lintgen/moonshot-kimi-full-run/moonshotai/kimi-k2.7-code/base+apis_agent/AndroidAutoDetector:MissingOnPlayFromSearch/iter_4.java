package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
import com.android.tools.lint.client.api.JavaElementVisitor;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AndroidAutoDetector extends Detector implements Detector.XmlScanner, Detector.JavaPsiScanner {

    public static final Issue MISSING_ON_PLAY_FROM_SEARCH = Issue.create(
            "MissingOnPlayFromSearch",
            "Missing onPlayFromSearch",
            "To support voice searches on Android Auto, in addition to adding an "
                    + "intent-filter for the action android.media.action.PLAY_FROM_SEARCH, "
                    + "you also need to override and implement onPlayFromSearch(String query, Bundle bundle).",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(AndroidAutoDetector.class, EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE)),
            "https://developer.android.com/training/auto/audio/index.html#support_voice"
    );

    private static final String MEDIA_BROWSER_SERVICE = "android.media.browse.MediaBrowserService";
    private static final String PLAY_FROM_SEARCH_ACTION = "android.media.action.PLAY_FROM_SEARCH";
    private static final String ON_PLAY_FROM_SEARCH = "onPlayFromSearch";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";
    private static final String TAG_SERVICE = "service";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_ACTION = "action";
    private static final String ATTR_NAME = "name";
    private static final String FQCN_STRING = "java.lang.String";
    private static final String FQCN_BUNDLE = "android.os.Bundle";

    private final Map<String, Element> mPlayFromSearchServices = new HashMap<>();
    private final Map<String, PsiClass> mServiceClasses = new HashMap<>();
    private final Map<String, JavaContext> mServiceClassContexts = new HashMap<>();
    private final Set<String> mReported = new HashSet<>();

    @Override
    public void beforeCheckEachProject(@NonNull Context context) {
        mPlayFromSearchServices.clear();
        mServiceClasses.clear();
        mServiceClassContexts.clear();
        mReported.clear();
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_SERVICE, TAG_ACTION);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if (TAG_SERVICE.equals(tagName)) {
            String name = resolveServiceName(context,
                    element.getAttributeNS(ANDROID_URI, ATTR_NAME));
            if (name != null) {
                mPlayFromSearchServices.put(name, element);
                PsiClass cls = mServiceClasses.get(name);
                if (cls != null && !mReported.contains(name) && !hasOnPlayFromSearch(cls)) {
                    reportMissing(mServiceClassContexts.get(name), cls);
                }
            }
        } else if (TAG_ACTION.equals(tagName)) {
            String actionName = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
            if (PLAY_FROM_SEARCH_ACTION.equals(actionName)) {
                Element intentFilter = getParent(element, TAG_INTENT_FILTER);
                if (intentFilter != null) {
                    Element service = getParent(intentFilter, TAG_SERVICE);
                    if (service != null) {
                        String name = resolveServiceName(context,
                                service.getAttributeNS(ANDROID_URI, ATTR_NAME));
                        if (name != null) {
                            mPlayFromSearchServices.put(name, service);
                            PsiClass cls = mServiceClasses.get(name);
                            if (cls != null && !mReported.contains(name) && !hasOnPlayFromSearch(cls)) {
                                reportMissing(mServiceClassContexts.get(name), cls);
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public List<Class<? extends PsiElement>> getApplicableNodeTypes() {
        return Collections.<Class<? extends PsiElement>>singletonList(PsiClass.class);
    }

    @Override
    public JavaElementVisitor createPsiVisitor(@NonNull final JavaContext context) {
        return new JavaElementVisitor() {
            @Override
            public void visitClass(PsiClass node) {
                String qualifiedName = node.getQualifiedName();
                if (qualifiedName == null) {
                    return;
                }
                if (context.getEvaluator().extendsClass(node, MEDIA_BROWSER_SERVICE, false)) {
                    mServiceClasses.put(qualifiedName, node);
                    mServiceClassContexts.put(qualifiedName, context);
                    Element element = mPlayFromSearchServices.get(qualifiedName);
                    if (element != null && !mReported.contains(qualifiedName)
                            && !hasOnPlayFromSearch(node)) {
                        reportMissing(context, node);
                    }
                }
            }
        };
    }

    private boolean hasOnPlayFromSearch(@NonNull PsiClass cls) {
        for (PsiMethod method : cls.getMethods()) {
            if (ON_PLAY_FROM_SEARCH.equals(method.getName())
                    && method.getParameterList().getParametersCount() == 2
                    && method.getContainingClass() == cls
                    && !method.hasModifierProperty(PsiModifier.PRIVATE)
                    && !method.hasModifierProperty(PsiModifier.STATIC)) {
                PsiParameter[] params = method.getParameterList().getParameters();
                if (isType(params[0].getType(), FQCN_STRING)
                        && isType(params[1].getType(), FQCN_BUNDLE)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isType(@NonNull PsiType type, @NonNull String fqcn) {
        return fqcn.equals(type.getCanonicalText());
    }

    private void reportMissing(@NonNull JavaContext context, @NonNull PsiClass cls) {
        String name = cls.getQualifiedName();
        if (name != null) {
            mReported.add(name);
        }
        context.report(MISSING_ON_PLAY_FROM_SEARCH, cls, context.getLocation(cls),
                "Service " + name + " should override onPlayFromSearch");
    }

    @NonNull
    private static Element getParent(@NonNull Element element, @NonNull String tagName) {
        Node parent = element.getParentNode();
        if (parent instanceof Element && tagName.equals(((Element) parent).getTagName())) {
            return (Element) parent;
        }
        return null;
    }

    private static String resolveServiceName(@NonNull XmlContext context, @NonNull String name) {
        if (name.isEmpty()) {
            return null;
        }
        String pkg = context.getMainProject().getPackage();
        if (pkg == null || pkg.isEmpty()) {
            return name;
        }
        if (name.startsWith(".")) {
            return pkg + name;
        }
        if (name.indexOf('.') == -1) {
            return pkg + "." + name;
        }
        return name;
    }
}