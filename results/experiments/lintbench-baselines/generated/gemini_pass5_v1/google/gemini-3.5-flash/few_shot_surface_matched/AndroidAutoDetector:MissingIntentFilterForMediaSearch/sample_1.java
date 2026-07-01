package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.w3c.dom.Element;

public class AndroidAutoDetector extends Detector implements XmlScanner, SourceCodeScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "MissingIntentFilterForMediaSearch",
                    "Missing MEDIA_PLAY_FROM_SEARCH intent-filter",
                    "To support voice searches on Android Auto, you should also register an "
                            + "`intent-filter` for the action `android.media.action.MEDIA_PLAY_FROM_SEARCH`. "
                            + "To do this, add\n"
                            + "`<intent-filter>`\n"
                            + "    `<action android:name=\"android.media.action.MEDIA_PLAY_FROM_SEARCH\" />`\n"
                            + "`</intent-filter>`\n"
                            + "to your `<activity>` or `<service>`.",
                    Category.CORRECTNESS,
                    5,
                    Severity.ERROR,
                    new Implementation(
                            AndroidAutoDetector.class,
                            Scope.MANIFEST_AND_JAVA_SCOPE));

    private boolean mHasMediaPlayFromSearch;
    private boolean mHasMediaBrowserService;
    private final List<MediaServiceDeclaration> mDeclaredMediaServices = new ArrayList<>();

    @Override
    public boolean appliesTo(@NonNull Context context, @NonNull File file) {
        return true;
    }

    @Override
    @Nullable
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("action");
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        mHasMediaPlayFromSearch = false;
        mHasMediaBrowserService = false;
        mDeclaredMediaServices.clear();
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if ("action".equals(element.getTagName())) {
            String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if ("android.media.action.MEDIA_PLAY_FROM_SEARCH".equals(name)) {
                mHasMediaPlayFromSearch = true;
            }
        }
    }

    @Override
    @Nullable
    public List<String> applicableSuperClasses() {
        return Arrays.asList(
                "android.service.media.MediaBrowserService",
                "android.support.v4.media.MediaBrowserServiceCompat",
                "androidx.media.MediaBrowserServiceCompat");
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        mHasMediaBrowserService = true;
        mDeclaredMediaServices.add(new MediaServiceDeclaration(context, context.getNameLocation(declaration)));
    }

    @Override
    public void visitMethod(
            @NonNull JavaContext context,
            @NonNull UMethod method,
            @NonNull List<? extends UMethod> superMethods) {
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (mHasMediaBrowserService && !mHasMediaPlayFromSearch) {
            for (MediaServiceDeclaration declaration : mDeclaredMediaServices) {
                declaration.context.report(
                        ISSUE,
                        declaration.location,
                        "Missing `android.media.action.MEDIA_PLAY_FROM_SEARCH` intent-filter");
            }
        }
    }

    private static class MediaServiceDeclaration {
        final JavaContext context;
        final Location location;

        MediaServiceDeclaration(JavaContext context, Location location) {
            this.context = context;
            this.location = location;
        }
    }
}