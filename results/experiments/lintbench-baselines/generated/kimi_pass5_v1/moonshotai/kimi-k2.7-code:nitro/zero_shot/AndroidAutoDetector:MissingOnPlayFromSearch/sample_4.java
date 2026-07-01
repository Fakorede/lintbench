package com.android.tools.lint.checks;

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
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;

import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementHandler;
import org.jetbrains.uast.UMethod;
import org.jetbrains.uast.UParameter;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AndroidAutoDetector extends Detector
        implements Detector.XmlScanner, Detector.UastScanner {

    private static final String MEDIA_BROWSER_SERVICE_ACTION =
            "android.media.browse.MediaBrowserService";
    private static final String MEDIA_BROWSER_SERVICE_CLASS =
            "android.service.media.MediaBrowserService";
    private static final String MEDIA_BROWSER_SERVICE_COMPAT =
            "android.support.v4.media.MediaBrowserServiceCompat";
    private static final String ANDROIDX_MEDIA_BROWSER_SERVICE_COMPAT =
            "androidx.media.MediaBrowserServiceCompat";

    private static final String ON_PLAY_FROM_SEARCH = "onPlayFromSearch";
    private static final String TYPE_STRING = "java.lang.String";
    private static final String TYPE_BUNDLE = "android.os.Bundle";

    public static final Issue ISSUE = Issue.create(
            "MissingOnPlayFromSearch",
            "Missing onPlayFromSearch",
            "To support voice searches on Android Auto, in addition to adding an "
                    + "intent-filter for the action android.media.browse.MediaBrowserService, "
                    + "you also need to override and implement "
                    + "onPlayFromSearch(String query, Bundle bundle).",
            Category.USABILITY,
            6,
            Severity.WARNING,
            new Implementation(AndroidAutoDetector.class,
                    EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE))
    );

    private final Map<String, Location> mManifestMediaBrowserServices = new HashMap<>();
    private final Set<String> mMissingOnPlayFromSearch = new HashSet<>();

    @Override
    public void beforeCheckEachProject(@NonNull Context context) {
        mManifestMediaBrowserServices.clear();
        mMissingOnPlayFromSearch.clear();
    }

    @Override
    @NonNull
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_SERVICE);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!TAG_SERVICE.equals(element.getTagName())) {
            return;
        }

        String serviceName = element.getAttributeNS(ANDROID_URI, ATTR_NAME);
        if (serviceName == null || serviceName.isEmpty()) {
            return;
        }

        String packageName = element.getOwnerDocument().getDocumentElement().getAttribute("package");
        String fqcn = getFullClassName(packageName, serviceName);

        boolean isMediaBrowserService = false;
        NodeList intentFilters = element.getElementsByTagName(TAG_INTENT_FILTER);
        for (int i = 0; i < intentFilters.getLength(); i++) {
            Element intentFilter = (Element) intentFilters.item(i);
            NodeList actions = intentFilter.getElementsByTagName(TAG_ACTION);
            for (int j = 0; j < actions.getLength(); j++) {
                Element action = (Element) actions.item(j);
                String actionName = action.getAttributeNS(ANDROID_URI, ATTR_NAME);
                if (MEDIA_BROWSER_SERVICE_ACTION.equals(actionName)) {
                    isMediaBrowserService = true;
                    break;
                }
            }
            if (isMediaBrowserService) {
                break;
            }
        }

        if (isMediaBrowserService) {
            mManifestMediaBrowserServices.put(fqcn,
                    Location.create(context.file, element));
        }
    }

    @Override
    @NonNull
    public List<Class<? extends UElement>> getApplicableUElementTypes() {
        return Collections.singletonList(UClass.class);
    }

    @Override
    @NonNull
    public UElementHandler createUastHandler(@NonNull final JavaContext context) {
        return new UElementHandler() {
            @Override
            public void visitClass(@NonNull UClass node) {
                com.intellij.psi.PsiClass psiClass = node.getPsi();
                if (psiClass == null) {
                    return;
                }

                boolean isMediaBrowserService =
                        context.getEvaluator().extendsClass(psiClass,
                                MEDIA_BROWSER_SERVICE_CLASS, false)
                                || context.getEvaluator().extendsClass(psiClass,
                                MEDIA_BROWSER_SERVICE_COMPAT, false)
                                || context.getEvaluator().extendsClass(psiClass,
                                ANDROIDX_MEDIA_BROWSER_SERVICE_COMPAT, false);

                if (!isMediaBrowserService) {
                    return;
                }

                String fqcn = psiClass.getQualifiedName();
                if (fqcn == null) {
                    fqcn = psiClass.getName();
                }

                boolean hasOnPlayFromSearch = false;
                for (UMethod method : node.getMethods()) {
                    if (!ON_PLAY_FROM_SEARCH.equals(method.getName())) {
                        continue;
                    }

                    List<UParameter> parameters = method.getUastParameters();
                    if (parameters.size() != 2) {
                        continue;
                    }

                    String type1 = parameters.get(0).getType().getCanonicalText();
                    String type2 = parameters.get(1).getType().getCanonicalText();
                    if (TYPE_STRING.equals(type1) && TYPE_BUNDLE.equals(type2)) {
                        hasOnPlayFromSearch = true;
                        break;
                    }
                }

                if (!hasOnPlayFromSearch) {
                    mMissingOnPlayFromSearch.add(fqcn);
                }
            }
        };
    }

    @Override
    public void afterCheckProject(@NonNull Context context) {
        for (Map.Entry<String, Location> entry : mManifestMediaBrowserServices.entrySet()) {
            String serviceClass = entry.getKey();
            if (mMissingOnPlayFromSearch.contains(serviceClass)) {
                context.report(ISSUE, entry.getValue(),
                        "To support voice searches on Android Auto, "
                                + serviceClass
                                + " must override onPlayFromSearch(String, Bundle).");
            }
        }
    }

    private static String getFullClassName(String packageName, String className) {
        if (className.startsWith(".")) {
            return packageName + className;
        }
        return className;
    }
}