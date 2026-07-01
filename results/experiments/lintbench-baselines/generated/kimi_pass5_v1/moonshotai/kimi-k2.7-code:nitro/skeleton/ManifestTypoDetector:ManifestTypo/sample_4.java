package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.w3c.dom.Element;

public class ManifestTypoDetector extends Detector implements XmlScanner {

    private static final Implementation IMPLEMENTATION =
            new Implementation(ManifestTypoDetector.class, Scope.MANIFEST_SCOPE);

    private static final Set<String> VALID_TAGS =
            new HashSet<>(
                    Arrays.asList(
                            "action",
                            "activity",
                            "activity-alias",
                            "application",
                            "category",
                            "compatible-screens",
                            "data",
                            "grant-uri-permission",
                            "instrumentation",
                            "intent-filter",
                            "layout",
                            "manifest",
                            "meta-data",
                            "path-permission",
                            "permission",
                            "permission-group",
                            "permission-tree",
                            "profileable",
                            "provider",
                            "queries",
                            "receiver",
                            "service",
                            "supports-gl-texture",
                            "supports-screens",
                            "uses-configuration",
                            "uses-feature",
                            "uses-library",
                            "uses-native-library",
                            "uses-permission",
                            "uses-permission-sdk-23",
                            "uses-sdk",
                            "uses-split",
                            "uses-static-library"));

    public static final Issue ISSUE =
            Issue.create(
                    "ManifestTypo",
                    "Typos in manifest tags",
                    "Android manifest files only recognize a specific set of XML tags. "
                            + "If a tag is misspelled it will be ignored by the build tools "
                            + "and framework, which can cause components to not be registered, "
                            + "permissions to be missing, or builds to fail. This check flags "
                            + "tags that are close to known valid manifest tags as likely "
                            + "misspellings.",
                    Category.CORRECTNESS,
                    5,
                    Severity.FATAL,
                    IMPLEMENTATION);

    @Override
    public Collection<String> getApplicableElements() {
        return null;
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tag = element.getTagName();
        if (VALID_TAGS.contains(tag)) {
            return;
        }

        String closest = findClosestTag(tag);
        if (closest != null) {
            String message =
                    String.format(
                            "\"%1$s\" looks like a misspelling of the manifest tag \"%2$s\"",
                            tag, closest);
            context.report(ISSUE, element, context.getLocation(element), message);
        }
    }

    private static String findClosestTag(String tag) {
        String closest = null;
        int minDistance = Integer.MAX_VALUE;
        for (String valid : VALID_TAGS) {
            int distance = editDistance(tag, valid);
            if (distance < minDistance) {
                minDistance = distance;
                closest = valid;
            }
        }

        if (minDistance <= 1) {
            return closest;
        }
        if (minDistance == 2 && tag.length() >= 6) {
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
            int[] swap = prev;
            prev = curr;
            curr = swap;
        }

        return prev[n];
    }
}