package com.android.tools.lint.checks;

import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.FileType;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.JavaContext;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.SourceCodeScanner;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import com.intellij.psi.PsiMethod;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UClass;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final String ACTION_MEDIA_PLAY_FROM_SEARCH =
            "android.media.action.MEDIA_PLAY_FROM_SEARCH";
    private static final String MEDIA_BROWSER_SERVICE =
            "android.media.browse.MediaBrowserService";

    private static final String TAG_ACTION = "action";
    private static final String TAG_INTENT_FILTER = "intent-filter";
    private static final String TAG_ACTIVITY = "activity";
    private static final String TAG_SERVICE = "service";
    private static final String ATTR_NAME = "name";
    private static final String ATTR_NAME_PREFIXED = "android:name";

    private boolean mHasMediaBrowserService;
    private boolean mHasMediaPlayFromSearchFilter;
    private UClass mMediaBrowserServiceClass;
    private JavaContext mMediaBrowserServiceContext;

    public static final Issue ISSUE =
            Issue.create(
                    "MissingIntentFilterForMediaSearch",
                    "Missing MEDIA_PLAY_FROM_SEARCH Intent Filter",
                    "To support voice searches on Android Auto, you should register an "
                            + "`<intent-filter>` for the action "
                            + "`android.media.action.MEDIA_PLAY_FROM_SEARCH`. Add the intent filter "
                            + "to an `<activity>` or `<service>` in your manifest.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    new Implementation(
                            AndroidAutoDetector.class,
                            Scope.JAVA_FILE_SCOPE.and(Scope.MANIFEST_SCOPE)));

    @Override
    public boolean appliesTo(FileType fileType) {
        return fileType == FileType.RESOURCE_FILE || fileType == FileType.JAVA_FILE;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(TAG_ACTION);
    }

    @Override
    public void beforeCheckRootProject(Context context) {
        mHasMediaBrowserService = false;
        mHasMediaPlayFromSearchFilter = false;
        mMediaBrowserServiceClass = null;
        mMediaBrowserServiceContext = null;
    }

    @Override
    public void visitElement(XmlContext context, Element element) {
        if (!TAG_ACTION.equals(element.getTagName())) {
            return;
        }

        String actionName = element.getAttribute(ATTR_NAME_PREFIXED);
        if (actionName == null || actionName.isEmpty()) {
            actionName = element.getAttribute(ATTR_NAME);
        }
        if (!ACTION_MEDIA_PLAY_FROM_SEARCH.equals(actionName)) {
            return;
        }

        Node parent = element.getParentNode();
        if (parent == null || !TAG_INTENT_FILTER.equals(getTagName(parent))) {
            return;
        }

        Node grandparent = parent.getParentNode();
        if (grandparent == null) {
            return;
        }

        String grandparentTag = getTagName(grandparent);
        if (TAG_ACTIVITY.equals(grandparentTag) || TAG_SERVICE.equals(grandparentTag)) {
            mHasMediaPlayFromSearchFilter = true;
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return Collections.singletonList(MEDIA_BROWSER_SERVICE);
    }

    @Override
    public void visitClass(JavaContext context, UClass declaration) {
        if (MEDIA_BROWSER_SERVICE.equals(declaration.getQualifiedName())) {
            return;
        }
        mHasMediaBrowserService = true;
        mMediaBrowserServiceClass = declaration;
        mMediaBrowserServiceContext = context;
    }

    @Override
    public void visitMethod(JavaContext context, UCallExpression node, PsiMethod method) {
        // No method-call analysis required.
    }

    @Override
    public void afterCheckRootProject(Context context) {
        if (mHasMediaBrowserService
                && !mHasMediaPlayFromSearchFilter
                && mMediaBrowserServiceClass != null
                && mMediaBrowserServiceContext != null) {
            mMediaBrowserServiceContext.report(
                    ISSUE,
                    mMediaBrowserServiceClass,
                    mMediaBrowserServiceContext.getNameLocation(mMediaBrowserServiceClass),
                    "Missing intent filter for `android.media.action.MEDIA_PLAY_FROM_SEARCH`; "
                            + "add this intent filter to an `<activity>` or `<service>` to support "
                            + "voice searches on Android Auto.");
        }
    }

    private static String getTagName(Node node) {
        if (node instanceof Element) {
            return ((Element) node).getTagName();
        }
        return null;
    }
}