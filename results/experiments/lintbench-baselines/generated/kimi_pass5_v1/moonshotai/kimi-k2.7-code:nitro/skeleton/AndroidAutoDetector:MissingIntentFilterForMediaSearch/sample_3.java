package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.resources.ResourceFolderType;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String MEDIA_PLAY_FROM_SEARCH_ACTION =
            "android.media.action.MEDIA_PLAY_FROM_SEARCH";
    private static final String ANDROID_URI = "http://schemas.android.com/apk/res/android";

    private static final List<String> MEDIA_BROWSER_SERVICE_CLASSES =
            Arrays.asList(
                    "android.service.media.MediaBrowserService",
                    "android.media.browse.MediaBrowserService",
                    "android.support.v4.media.MediaBrowserServiceCompat",
                    "androidx.media.MediaBrowserServiceCompat");

    private static final Implementation IMPLEMENTATION =
            new Implementation(
                    AndroidAutoDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.RESOURCE_FILE));

    public static final Issue ISSUE =
            Issue.create(
                    "MissingIntentFilterForMediaSearch",
                    "Missing MEDIA_PLAY_FROM_SEARCH intent-filter",
                    "To support voice searches on Android Auto, you should register an "
                            + "intent-filter for the action "
                            + "android.media.action.MEDIA_PLAY_FROM_SEARCH in the relevant "
                            + "activity or service. Add an `<intent-filter>` containing "
                            + "`<action android:name=\"android.media.action.MEDIA_PLAY_FROM_SEARCH\" />` "
                            + "to your `<activity>` or `<service>` in the manifest.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private final Set<String> mMediaBrowserServices = new HashSet<>();
    private final Set<String> mHasMediaPlayFromSearchFilter = new HashSet<>();
    private final Map<String, Location> mComponentLocations = new HashMap<>();
    private final Map<String, Location> mClassLocations = new HashMap<>();

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return folderType == ResourceFolderType.MANIFEST;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList("activity", "service");
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mMediaBrowserServices.clear();
        mHasMediaPlayFromSearchFilter.clear();
        mComponentLocations.clear();
        mClassLocations.clear();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (!"activity".equals(tag) && !"service".equals(tag)) {
            return;
        }

        String name = element.getAttributeNS(ANDROID_URI, "name");
        if (name == null || name.isEmpty()) {
            return;
        }

        String fqName = getFullClassName(context, name);
        if (!mMediaBrowserServices.contains(fqName)) {
            return;
        }

        mComponentLocations.put(fqName, context.getLocation(element));

        if (hasMediaPlayFromSearchFilter(element)) {
            mHasMediaPlayFromSearchFilter.add(fqName);
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return MEDIA_BROWSER_SERVICE_CLASSES;
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        String fqName = declaration.getQualifiedName();
        if (fqName == null) {
            return;
        }

        PsiClass psiClass = declaration.getPsi();
        if (psiClass == null) {
            return;
        }

        for (String superClass : MEDIA_BROWSER_SERVICE_CLASSES) {
            if (context.getEvaluator().extendsClass(psiClass, superClass, false)) {
                mMediaBrowserServices.add(fqName);
                mClassLocations.put(fqName, context.getLocation(psiClass));
                break;
            }
        }
    }

    @Override
    public void visitMethod(@NonNull JavaContext context, @NonNull UMethod method) {
        // No method-level checks are required for this issue.
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        for (String fqName : mMediaBrowserServices) {
            if (mHasMediaPlayFromSearchFilter.contains(fqName)) {
                continue;
            }

            Location location = mComponentLocations.get(fqName);
            if (location == null) {
                location = mClassLocations.get(fqName);
            }

            if (location != null) {
                context.report(
                        ISSUE,
                        location,
                        "Missing MEDIA_PLAY_FROM_SEARCH intent-filter");
            }
        }
    }

    private static boolean hasMediaPlayFromSearchFilter(@NonNull Element element) {
        NodeList intentFilters = element.getElementsByTagName("intent-filter");
        for (int i = 0; i < intentFilters.getLength(); i++) {
            Node filterNode = intentFilters.item(i);
            if (!(filterNode instanceof Element)) {
                continue;
            }
            Element intentFilter = (Element) filterNode;

            NodeList actions = intentFilter.getElementsByTagName("action");
            for (int j = 0; j < actions.getLength(); j++) {
                Node actionNode = actions.item(j);
                if (!(actionNode instanceof Element)) {
                    continue;
                }
                Element action = (Element) actionNode;
                String name = action.getAttributeNS(ANDROID_URI, "name");
                if (MEDIA_PLAY_FROM_SEARCH_ACTION.equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    @NonNull
    private static String getFullClassName(@NonNull XmlContext context, @NonNull String className) {
        String packageName = context.getMainProject().getPackage();
        if (packageName == null || packageName.isEmpty()) {
            packageName = context.getDocument().getDocumentElement().getAttribute("package");
        }
        if (packageName == null) {
            packageName = "";
        }

        if (className.startsWith(".")) {
            return packageName + className;
        } else if (className.contains(".")) {
            return className;
        } else {
            return packageName + "." + className;
        }
    }
}