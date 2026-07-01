package com.android.tools.lint.checks;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.detector.api.Category;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.XmlContext;
import com.android.tools.lint.detector.api.XmlScanner;
import org.w3c.dom.Element;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ManifestTypoDetector extends Detector implements XmlScanner {

    public static final Issue ISSUE =
            Issue.create(
                    "ManifestTypo",
                    "Typos in manifest tags",
                    "This check looks through the manifest, and if it finds any tags "
                            + "that look like likely misspellings, they are flagged.",
                    Category.CORRECTNESS,
                    10,
                    Severity.FATAL,
                    new Implementation(
                            ManifestTypoDetector.class, Scope.MANIFEST_SCOPE));

    private static final Set<String> VALID_TAGS = new HashSet<>();
    static {
        VALID_TAGS.add("manifest");
        VALID_TAGS.add("application");
        VALID_TAGS.add("activity");
        VALID_TAGS.add("activity-alias");
        VALID_TAGS.add("service");
        VALID_TAGS.add("receiver");
        VALID_TAGS.add("provider");
        VALID_TAGS.add("uses-permission");
        VALID_TAGS.add("uses-permission-sdk-23");
        VALID_TAGS.add("permission");
        VALID_TAGS.add("permission-group");
        VALID_TAGS.add("permission-tree");
        VALID_TAGS.add("uses-sdk");
        VALID_TAGS.add("uses-configuration");
        VALID_TAGS.add("uses-feature");
        VALID_TAGS.add("supports-screens");
        VALID_TAGS.add("compatible-screens");
        VALID_TAGS.add("supports-gl-texture");
        VALID_TAGS.add("meta-data");
        VALID_TAGS.add("intent-filter");
        VALID_TAGS.add("action");
        VALID_TAGS.add("category");
        VALID_TAGS.add("data");
        VALID_TAGS.add("grant-uri-permission");
        VALID_TAGS.add("path-permission");
        VALID_TAGS.add("instrumentation");
        VALID_TAGS.add("uses-library");
        VALID_TAGS.add("queries");
        VALID_TAGS.add("profileable");
        VALID_TAGS.add("property");
        VALID_TAGS.add("attribution");
    }

    @Override
    public Collection<String> getApplicableElements() {
        return Collections.singletonList(ALL);
    }

    @Override
    public void visitElement(@NonNull XmlContext context, @NonNull Element element) {
        String tagName = element.getTagName();
        if (VALID_TAGS.contains(tagName)) {
            return;
        }

        String closest = findClosestTag(tagName);
        if (closest != null) {
            context.report(
                    ISSUE,
                    element,
                    context.getNameLocation(element),
                    String.format("Suspected typo in tag '%1$s'; did you mean '%2$s'?", tagName, closest));
        }
    }

    @Nullable
    private static String findClosestTag(String tag) {
        String bestMatch = null;
        int bestDistance = Integer.MAX_VALUE;

        for (String validTag : VALID_TAGS) {
            int distance = getLevenshteinDistance(tag, validTag);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestMatch = validTag;
            }
        }

        // Only suggest if the edit distance is small relative to the tag length
        int threshold = tag.length() <= 5 ? 1 : 2;
        if (bestDistance <= threshold) {
            return bestMatch;
        }
        return null;
    }

    private static int getLevenshteinDistance(String s, String t) {
        int n = s.length();
        int m = t.length();
        if (n == 0) return m;
        if (m == 0) return n;
        int[] p = new int[n + 1];
        int[] d = new int[n + 1];
        for (int i = 0; i <= n; i++) {
            p[i] = i;
        }
        for (int j = 1; j <= m; j++) {
            char tj = t.charAt(j - 1);
            d[0] = j;
            for (int i = 1; i <= n; i++) {
                int cost = s.charAt(i - 1) == tj ? 0 : 1;
                d[i] = Math.min(Math.min(d[i - 1] + 1, p[i] + 1), p[i - 1] + cost);
            }
            int[] placeholder = p;
            p = d;
            d = placeholder;
        }
        return p[n];
    }
}