package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiMethod;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String ANDROID_MEDIA_ACTION_MEDIA_PLAY_FROM_SEARCH =
            "android.media.action.MEDIA_PLAY_FROM_SEARCH";

    private static final String MEDIA_BROWSER_SERVICE_COMPAT =
            "android.support.v4.media.MediaBrowserServiceCompat";
    private static final String MEDIA_BROWSER_SERVICE =
            "android.media.browse.MediaBrowserService";

    private static final String ATTR_NAME = "android:name";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_ACTION = "action";
    private static final String TAG_SERVICE = "service";
    private static final String TAG_ACTIVITY = "activity";

    public static final Issue MISSING_MEDIA_SEARCH_INTENT_FILTER =
            Issue.create(
                            "MissingIntentFilterForMediaSearch",
                            "Missing MEDIA_PLAY_FROM_SEARCH intent-filter",
                            "To support voice searches on Android Auto, you should also register an "
                                    + "`intent-filter` for the action "
                                    + "`android.media.action.MEDIA_PLAY_FROM_SEARCH`.\n\n"
                                    + "To do this, add\n"
                                    + "```xml\n"
                                    + "<intent-filter>\n"
                                    + "    <action android:name=\"android.media.action.MEDIA_PLAY_FROM_SEARCH\" />\n"
                                    + "</intent-filter>\n"
                                    + "```\n"
                                    + "to your `<activity>` or `<service>`.",
                            Category.CORRECTNESS,
                            6,
                            Severity.ERROR,
                            new Implementation(
                                    AndroidAutoDetector.class,
                                    EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE)))
                    .addMoreInfo(
                            "https://developer.android.com/training/auto/audio/index.html#support_voice");

    /** Whether we found the MEDIA_PLAY_FROM_SEARCH action in the manifest */
    private boolean mFoundMediaSearchIntentFilter;

    /** The media browser service class name found in the manifest (if any) */
    private String mMediaBrowserServiceClass;

    /** Whether we've already reported the issue */
    private boolean mReported;

    public AndroidAutoDetector() {}

    @Override
    public boolean appliesTo(@NonNull com.android.tools.lint.detector.api.Context context,
            @NonNull com.android.tools.lint.detector.api.Project project) {
        return true;
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mFoundMediaSearchIntentFilter = false;
        mMediaBrowserServiceClass = null;
        mReported = false;
    }

    // ---- XmlScanner ----

    @Override
    public Collection<String> getApplicableElements() {
        return Arrays.asList(TAG_SERVICE, TAG_ACTIVITY);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        // Look for intent-filter children that contain the MEDIA_PLAY_FROM_SEARCH action
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (TAG_INTENT_FILTER.equals(childElement.getTagName())) {
                    if (intentFilterHasMediaPlayFromSearch(childElement)) {
                        mFoundMediaSearchIntentFilter = true;
                        return;
                    }
                }
            }
        }

        // Track the service class name for later checking
        if (TAG_SERVICE.equals(element.getTagName())) {
            String name = element.getAttribute(ATTR_NAME);
            if (name != null && !name.isEmpty()) {
                mMediaBrowserServiceClass = name;
            }
        }
    }

    private boolean intentFilterHasMediaPlayFromSearch(@NonNull Element intentFilter) {
        NodeList children = intentFilter.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                if (TAG_ACTION.equals(childElement.getTagName())) {
                    String actionName = childElement.getAttribute(ATTR_NAME);
                    if (ANDROID_MEDIA_ACTION_MEDIA_PLAY_FROM_SEARCH.equals(actionName)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // ---- SourceCodeScanner ----

    @Nullable
    @Override
    public List<String> applicableSuperClasses() {
        return Arrays.asList(MEDIA_BROWSER_SERVICE_COMPAT, MEDIA_BROWSER_SERVICE);
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (mFoundMediaSearchIntentFilter || mReported) {
            return;
        }

        // Check if this class is a MediaBrowserService subclass
        boolean isMediaBrowserService = false;
        for (String superClass : applicableSuperClasses()) {
            if (context.getEvaluator().extendsClass(
                    declaration.getJavaPsi(), superClass, true)) {
                isMediaBrowserService = true;
                break;
            }
        }

        if (isMediaBrowserService && !mFoundMediaSearchIntentFilter) {
            context.report(
                    MISSING_MEDIA_SEARCH_INTENT_FILTER,
                    declaration,
                    context.getNameLocation(declaration),
                    "To support voice searches on Android Auto, register an "
                            + "`intent-filter` for the action "
                            + "`android.media.action.MEDIA_PLAY_FROM_SEARCH`");
            mReported = true;
        }
    }

    @Nullable
    @Override
    public List<String> getApplicableMethodNames() {
        return Collections.singletonList("onLoadChildren");
    }

    @Override
    public void visitMethod(
            @NonNull JavaContext context,
            @NonNull UMethod method,
            @NonNull PsiMethod resolvedMethod) {
        if (mFoundMediaSearchIntentFilter || mReported) {
            return;
        }

        UClass containingClass = (UClass) method.getUastParent();
        if (containingClass == null) {
            return;
        }

        boolean isMediaBrowserService = false;
        for (String superClass : applicableSuperClasses()) {
            if (context.getEvaluator().extendsClass(
                    containingClass.getJavaPsi(), superClass, true)) {
                isMediaBrowserService = true;
                break;
            }
        }

        if (isMediaBrowserService && !mFoundMediaSearchIntentFilter) {
            context.report(
                    MISSING_MEDIA_SEARCH_INTENT_FILTER,
                    method,
                    context.getNameLocation(method),
                    "To support voice searches on Android Auto, register an "
                            + "`intent-filter` for the action "
                            + "`android.media.action.MEDIA_PLAY_FROM_SEARCH`");
            mReported = true;
        }
    }
}