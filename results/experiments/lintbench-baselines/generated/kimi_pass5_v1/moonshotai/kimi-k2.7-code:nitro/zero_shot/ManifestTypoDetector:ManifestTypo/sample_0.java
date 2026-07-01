package com.android.tools.lint.checks;

import org.jetbrains.annotations.NotNull;
import com.android.tools.lint.detector.api.*;
import org.w3c.dom.Element;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class ManifestTypoDetector extends Detector implements XmlScanner {

    private static final Set<String> VALID_TAGS = new HashSet<>(Arrays.asList(
            "manifest",
            "application",
            "activity",
            "activity-alias",
            "service",
            "receiver",
            "provider",
            "uses-permission",
            "uses-permission-sdk-23",
            "permission",
            "permission-group",
            "permission-tree",
            "uses-feature",
            "uses-library",
            "uses-native-library",
            "uses-static-library",
            "uses-sdk",
            "supports-screens",
            "compatible-screens",
            "supports-gl-texture",
            "instrumentation",
            "intent-filter",
            "action",
            "category",
            "data",
            "meta-data",
            "grant-uri-permission",
            "path-permission",
            "original-package",
            "overlay",
            "restrict-update",
            "protected-broadcast",
            "key-sets",
            "public-key",
            "upgrade-key-set",
            "queries",
            "package",
            "intent",
            "screen",
            "profileable",
            "property",
            "layout",
            "eat-comment",
            "uses-split",
            "attribution",
            "processes",
            "process",
            "feature-group"
    ));

    public static final Issue ISSUE = Issue.create(
            "ManifestTypo",
            "Typo in manifest tag",
            "This check looks through the manifest and flags tags that appear to be " +
                    "misspellings of standard Android manifest tags.",
            Category.CORRECTNESS,
            6,
            Severity.WARNING,
            new Implementation(ManifestTypoDetector.class, Scope.MANIFEST_SCOPE)
    );

    @Override
    public Collection<String> getApplicableElements() {
        return XmlScannerConstants.ALL;
    }

    @Override
    public void visitElement(@NotNull XmlContext context, @NotNull Element element) {
        String tag = element.getLocalName();
        if (tag == null) {
            tag = element.getTagName();
            int colon = tag.indexOf(':');
            if (colon != -1) {
                tag = tag.substring(colon + 1);
            }
        }

        if (VALID_TAGS.contains(tag)) {
            return;
        }

        String closest = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String valid : VALID_TAGS) {
            int distance = editDistance(tag, valid);
            if (distance < bestDistance) {
                bestDistance = distance;
                closest = valid;
            }
        }

        if (closest != null && isCloseEnough(bestDistance, closest.length())) {
            String message = String.format(
                    "Possible typo: '%1$s' is not a standard manifest tag; did you mean '%2$s'?",
                    tag, closest);
            context.report(ISSUE, element, context.getLocation(element), message);
        }
    }

    private static boolean isCloseEnough(int distance, int length) {
        if (distance <= 1) {
            return true;
        }
        return distance == 2 && length >= 5;
    }

    private static int editDistance(String s, String t) {
        int n = s.length();
        int m = t.length();
        if (n == 0) {
            return m;
        }
        if (m == 0) {
            return n;
        }

        int[] prev = new int[m + 1];
        int[] curr = new int[m + 1];
        for (int j = 0; j <= m; j++) {
            prev[j] = j;
        }

        for (int i = 1; i <= n; i++) {
            curr[0] = i;
            char sc = s.charAt(i - 1);
            for (int j = 1; j <= m; j++) {
                int cost = sc == t.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(
                        Math.min(curr[j - 1] + 1, prev[j] + 1),
                        prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }

        return prev[m];
    }
}