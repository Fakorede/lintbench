package com.android.tools.lint.checks;

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
import com.intellij.psi.PsiMethod;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String MEDIA_PLAY_FROM_SEARCH =
            "android.media.action.MEDIA_PLAY_FROM_SEARCH";

    public static final Issue ISSUE =
            Issue.create(
                            "MissingIntentFilterForMediaSearch",
                            "Missing MEDIA_PLAY_FROM_SEARCH intent-filter",
                            "To support voice searches on Android Auto, you should also register an"
                                    + " `<intent-filter>` for the action"
                                    + " `android.media.action.MEDIA_PLAY_FROM_SEARCH`. To do this,"
                                    + " add"
                                    + " `<intent-filter>\\n    <action"
                                    + " android:name=\\\"android.media.action.MEDIA_PLAY_FROM_SEARCH\\\""
                                    + " />\\n</intent-filter>` to your `<activity>` or `<service>`.",
                            Category.CORRECTNESS,
                            6,
                            Severity.ERROR,
                            new Implementation(
                                    AndroidAutoDetector.class,
                                    EnumSet.of(Scope.MANIFEST_SCOPE, Scope.JAVA_FILE_SCOPE)))
                    .setAndroidSpecific(true);

    private final Map<String, List<ComponentInfo>> mManifestComponents = new HashMap<>();
    private final Set<String> mMediaServices = new HashSet<>();

    @Override
    public boolean appliesTo(Context context, File file) {
        return context.getProject().isAndroidProject();
    }

    @Override
    public void beforeCheckRootProject(Context context) {
        mManifestComponents.clear();
        mMediaServices.clear();
    }

    @Override
    public List<String> getApplicableElements() {
        return Arrays.asList("activity", "service");
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String className = element.getAttribute("android:name");
        if (className.isEmpty()) {
            className = element.getAttribute("name");
        }
        if (className.isEmpty()) {
            return;
        }

        className = getQualifiedName(context, className);
        boolean hasAction = hasAction(element, MEDIA_PLAY_FROM_SEARCH);
        Location location = context.getLocation(element);

        List<ComponentInfo> list = mManifestComponents.get(className);
        if (list == null) {
            list = new ArrayList<>();
            mManifestComponents.put(className, list);
        }
        list.add(new ComponentInfo(element.getTagName(), hasAction, location));
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                "android.media.browse.MediaBrowserService",
                "android.support.v4.media.MediaBrowserServiceCompat",
                "androidx.media.MediaBrowserServiceCompat");
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName != null) {
            mMediaServices.add(qualifiedName);
        }
    }

    @Override
    public void visitMethod(JavaContext context, UCallExpression node, PsiMethod method) {
        // Not needed for this check.
    }

    @Override
    public void afterCheckRootProject(Context context) {
        for (Map.Entry<String, List<ComponentInfo>> entry : mManifestComponents.entrySet()) {
            if (!mMediaServices.contains(entry.getKey())) {
                continue;
            }
            for (ComponentInfo info : entry.getValue()) {
                if (!info.hasAction) {
                    context.report(
                            ISSUE,
                            info.location,
                            "Missing intent-filter for action "
                                    + MEDIA_PLAY_FROM_SEARCH);
                }
            }
        }
    }

    private static String getQualifiedName(XmlContext context, String name) {
        String pkg = context.getMainProject().getPackage();
        if (pkg == null || pkg.isEmpty()) {
            return name;
        }
        if (name.startsWith(".")) {
            return pkg + name;
        }
        if (name.indexOf('.') < 0) {
            return pkg + "." + name;
        }
        return name;
    }

    private static boolean hasAction(Element component, String actionName) {
        NodeList filters = component.getElementsByTagName("intent-filter");
        for (int i = 0; i < filters.getLength(); i++) {
            Element filter = (Element) filters.item(i);
            NodeList actions = filter.getElementsByTagName("action");
            for (int j = 0; j < actions.getLength(); j++) {
                Element action = (Element) actions.item(j);
                String name = action.getAttribute("android:name");
                if (name.isEmpty()) {
                    name = action.getAttribute("name");
                }
                if (actionName.equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static class ComponentInfo {
        final String tag;
        final boolean hasAction;
        final Location location;

        ComponentInfo(String tag, boolean hasAction, Location location) {
            this.tag = tag;
            this.hasAction = hasAction;
            this.location = location;
        }
    }
}