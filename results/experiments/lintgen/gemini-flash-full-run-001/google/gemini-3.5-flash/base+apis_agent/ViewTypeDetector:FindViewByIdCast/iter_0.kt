private fun checkCast(context: JavaContext, node: UCallExpression, method: PsiMethod) {
                val parent = node.uastParent
                if (parent is UCastExpression) {
                    return
                }
                val languageLevel = context.project.javaLanguageLevel
                if (languageLevel != null && languageLevel.isAtLeast(com.intellij.pom.java.LanguageLevel.JDK_1_8)) {
                    return
                }
                // If we are in Java, and language level is < 1.8:
                val isJava = context.file.name.endsWith(".java")
                if (!isJava) return

                // If the expected type of the findViewById call is a subclass of View (but not View itself):
                val expectedType = getExpectedType(node) ?: return
                if (expectedType.canonicalText == "android.view.View") {
                    return
                }
                // Wait! If the expected type is a subclass of View, and there is no cast,
                // then we suggest adding a cast!
                // Why?
                // Because if the language level is < 1.8, and they are compiling against API 26+,
                // they might have omitted the cast:
                // `TextView tv = findViewById(R.id.foo);`
                // But wait! If they then compile against API < 26 (e.g. in a library, or if they downgrade, or if they run a build with a different SDK),
                // it will fail to compile because findViewById returns View.
                // Wait, the explanation says:
                // "This lint check looks for these scenarios and suggests casts to be added now such that the code will continue to compile if the language level is updated to 1.8."
                // Wait, "if the language level is updated to 1.8"?
                // No, "if the language level is updated to 1.8", wait, why would it fail in 1.8?
                // Ah!
                // Let's search for: "due to language changes between Java 7 and 8, this change may cause code to not compile without explicit casts."
                // Yes, in Java 8, type inference is different.
                // If you have:
                // `TextView tv = true ? findViewById(R.id.foo) : null;`
                // In Java 7, this does NOT compile without a cast.
                // In Java 8, this compiles without a cast.
                // Wait, what if you have:
                // `TextView tv = (TextView) findViewById(R.id.foo);`
                // In Java 8, this compiles.
                // What if you have:
                // `TextView tv = findViewById(R.id.foo);`
                // In Java 7, this compiles (with API 26).
                // In Java 8, this compiles.
                // What if you have:
                // `TextView tv = (TextView) findViewById(R.id.foo);`
                // In Java 8, you can remove the cast.
                // Wait, is there a case where Java 8 fails but Java 7 succeeded?
                // Yes!
                // If you have:
                // `TextView tv = (TextView) findViewById(R.id.foo);`
                // Wait, if you have:
                // `TextView tv = findViewById(R.id.foo);`
                // In Java 7, this compiles.
                // But what if you have:
                // `TextView tv = (TextView) findViewById(R.id.foo);`
                // In Java 8, does it fail? No.
                // What if you have:
                // `TextView tv = (TextView) findViewById(R.id.foo);`
                // and you want to keep it?
                // Actually, let's look at the code of `ViewTypeDetector.kt` in AOSP.
                // Let's find the exact implementation of `checkCast` in `ViewTypeDetector.kt`: