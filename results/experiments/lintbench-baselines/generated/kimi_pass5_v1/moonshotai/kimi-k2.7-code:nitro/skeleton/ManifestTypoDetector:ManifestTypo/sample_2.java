package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.w3c.dom.Element;

public class ManifestTypoDetector extends Detector implements Detector.XmlScanner {

    private static final String ANDROID_MANIFEST = "AndroidManifest.xml";

    private static final Implementation IMPLEMENTATION =
            new Implementation(ManifestTypoDetector.class, Scope.RESOURCE_FILE_SCOPE);

    public static final Issue ISSUE =
            Issue.create(
                    "ManifestTypo",
                    "Typos in manifest tags",
                    "This check looks through the manifest, and if it finds any tags that look like likely misspellings, they are flagged.",
                    Category.CORRECTNESS,
                    5,
                    Severity.FATAL,
                    IMPLEMENTATION);

    private static final Set<String> VALID_TAGS =
            new HashSet<>(
                    Arrays.asList(
                            "action",
                            "activity",
                            "activity-alias",
                            "adopt-permissions",
                            "application",
                            "category",
                            "compatible-screens",
                            "data",
                            "eat-comment",
                            "grant-uri-permission",
                            "header",
                            "instrumentation",
                            "intent-filter",
                            "key-sets",
                            "layout",
                            "manifest",
                            "meta-data",
                            "module",
                            "original-package",
                            "overlay",
                            "package",
                            "path-permission",
                            "permission",
                            "permission-group",
                            "permission-tree",
                            "profile",
                            "provider",
                            "public-key",
                            "receiver",
                            "restrict-update",
                            "screen",
                            "service",
                            "supports-gl-texture",
                            "supports-screens",
                            "upgrade-key-set",
                            "uses-configuration",
                            "uses-feature",
                            "uses-library",
                            "uses-permission",
                            "uses-permission-sdk-23",
                            "uses-sdk"));

    @Override
    public Collection<String> getApplicableElements() {
        return Detector.XmlScanner.ALL;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        if (!ANDROID_MANIFEST.equals(context.file.getName())) {
            return;
        }

        String tag = element.getTagName();
        if (VALID_TAGS.contains(tag)) {
            return;
        }

        String closest = findClosestValidTag(tag);
        if (closest != null) {
            context.report(
                    ISSUE,
                    element,
                    context.getLocation(element),
                    "Misspelled tag `" + tag + "`; did you mean `" + closest + "`?");
        }
    }

    private static String findClosestValidTag(String tag) {
        int minDistance = Integer.MAX_VALUE;
        String closest = null;
        for (String valid : VALID_TAGS) {
            int distance = editDistance(tag, valid);
            if (distance < minDistance) {
                minDistance = distance;
                closest = valid;
            }
        }

        if (closest != null
                && minDistance <= 2
                && (minDistance == 1 || tag.length() >= 5)) {
            return closest;
        }

        return null;
    }

    private static int editDistance(String s, String t) {
        int m = s.length();
        int n = t.length();
        if (m == 0) {
            return n;
        }
        if (n == 0) {
            return m;
        }

        int[] prev = new int[n + 1];
        int[] curr = new int[n + 1];

        for (int j = 0; j <= n; j++) {
            prev[j] = j;
        }

        for (int i = 1; i <= m; i++) {
            curr[0] = i;
            char sc = s.charAt(i - 1);
            for (int j = 1; j <= n; j++) {
                int cost = sc == t.charAt(j - 1) ? 0 : 1;
                curr[j] =
                        Math.min(
                                Math.min(curr[j - 1] + 1, prev[j] + 1),
                                prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }

        return prev[n];
    }
}