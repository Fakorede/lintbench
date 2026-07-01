package com.android.tools.lint.checks;

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
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AndroidAutoDetector extends Detector implements XmlScanner, SourceCodeScanner {

    public static final Issue ISSUE = Issue.create(
            "MissingIntentFilterForMediaSearch",
            "Missing MEDIA_PLAY_FROM_SEARCH intent-filter",
            "To support voice searches on Android Auto, you should also register an "
                    + "`intent-filter` for the action `android.media.action.MEDIA_PLAY_FROM_SEARCH`. "
                    + "Add it to your `<activity>` or `<service>`.",
            Category.CORRECTNESS,
            6,
            Severity.ERROR,
            new Implementation(AndroidAutoDetector.class, Scope.MANIFEST_SCOPE, Scope.JAVA_FILE_SCOPE));

    private static final String MEDIA_BROWSER_SERVICE = "android.service.media.MediaBrowserService";
    private static final String MEDIA_BROWSER_SERVICE_COMPAT = "android.support.v4.media.MediaBrowserServiceCompat";
    private static final String ANDROIDX_MEDIA_BROWSER_SERVICE_COMPAT = "androidx.media.MediaBrowserServiceCompat";
    private static final String ACTION_MEDIA_PLAY_FROM_SEARCH = "android.media.action.MEDIA_PLAY_FROM_SEARCH";
    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_SERVICE = "service";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_ACTION = "action";
    private static final String ATTR_NAME = "android:name";

    private final Set<String> mRelevantClasses = new HashSet<>();

    @Override
    public boolean appliesTo(com.android.resources.ResourceFolderType folderType) {
        return true;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_ACTIVITY, TAG_SERVICE);
    }

    @Override
    public void beforeCheckRootProject(Context context) {
        mRelevantClasses.clear();
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        String className = element.getAttribute(ATTR_NAME);
        if (className.isEmpty()) {
            return;
        }

        String packageName = context.getMainProject().getPackageName();
        if (className.startsWith(".")) {
            className = packageName + className;
        } else if (!className.contains(".")) {
            className = packageName + "." + className;
        }

        if (!mRelevantClasses.contains(className)) {
            return;
        }

        NodeList intentFilters = element.getElementsByTagName(TAG_INTENT_FILTER);
        for (int i = 0; i < intentFilters.getLength(); i++) {
            Element filter = (Element) intentFilters.item(i);
            NodeList actions = filter.getElementsByTagName(TAG_ACTION);
            for (int j = 0; j < actions.getLength(); j++) {
                Element action = (Element) actions.item(j);
                if (ACTION_MEDIA_PLAY_FROM_SEARCH.equals(action.getAttribute(ATTR_NAME))) {
                    return;
                }
            }
        }

        context.report(ISSUE, element, context.getLocation(element),
                "To support voice searches on Android Auto, register an intent-filter for "
                        + "`android.media.action.MEDIA_PLAY_FROM_SEARCH`");
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(MEDIA_BROWSER_SERVICE, MEDIA_BROWSER_SERVICE_COMPAT, ANDROIDX_MEDIA_BROWSER_SERVICE_COMPAT);
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        String qualifiedName = declaration.getQualifiedName();
        if (qualifiedName != null) {
            mRelevantClasses.add(qualifiedName);
        }
    }

    @Override
    public void visitMethod(JavaContext context, UCallExpression call, PsiMethod method) {
        // Reserved for method-level analysis if required by future specifications
    }
}