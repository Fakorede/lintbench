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
import com.intellij.psi.PsiModifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UMethod;
import org.w3c.dom.Element;

public class AndroidAutoDetector extends Detector implements SourceCodeScanner, XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(AndroidAutoDetector.class, EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST));

    public static final Issue ISSUE =
            Issue.create(
                    "MissingOnPlayFromSearch",
                    "Missing `onPlayFromSearch`",
                    "To support voice searches on Android Auto, in addition to adding an `intent-filter` for the action `onPlayFromSearch`, you also need to override and implement `onPlayFromSearch(String query, Bundle bundle)`.",
                    Category.CORRECTNESS,
                    6,
                    Severity.ERROR,
                    IMPLEMENTATION);

    private final List<Violation> violations = new ArrayList<>();
    private boolean hasMediaPlayFromSearchIntent = false;

    private static class Violation {
        final JavaContext context;
        final Location location;

        Violation(JavaContext context, Location location) {
            this.context = context;
            this.location = location;
        }
    }

    @Override
    public boolean appliesTo(@NonNull ResourceFolderType folderType) {
        return false;
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList("action");
    }

    @Override
    public void beforeCheckRootProject(@NonNull Context context) {
        hasMediaPlayFromSearchIntent = false;
        violations.clear();
    }

    @Override
    public void afterCheckRootProject(@NonNull Context context) {
        if (hasMediaPlayFromSearchIntent) {
            for (Violation violation : violations) {
                violation.context.report(
                        ISSUE,
                        violation.location,
                        "Missing `onPlayFromSearch` implementation to support voice searches"
                );
            }
        }
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if ("action".equals(element.getTagName())) {
            String name = element.getAttributeNS("http://schemas.android.com/apk/res/android", "name");
            if ("android.media.action.MEDIA_PLAY_FROM_SEARCH".equals(name)) {
                hasMediaPlayFromSearchIntent = true;
            }
        }
    }

    @Override
    public List<String> applicableSuperClasses() {
        return java.util.Arrays.asList(
                "android.media.session.MediaSession.Callback",
                "android.support.v4.media.session.MediaSessionCompat.Callback"
        );
    }

    @Override
    public void visitClass(@NonNull JavaContext context, @NonNull UClass declaration) {
        if (declaration.isInterface() || declaration.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return;
        }

        boolean overridesOnPlayFromSearch = false;
        for (UMethod method : declaration.getMethods()) {
            if ("onPlayFromSearch".equals(method.getName())) {
                if (method.getUastParameters().size() == 2) {
                    overridesOnPlayFromSearch = true;
                    break;
                }
            }
        }

        if (!overridesOnPlayFromSearch) {
            Location location = context.getNameLocation(declaration);
            violations.add(new Violation(context, location));
        }
    }
}