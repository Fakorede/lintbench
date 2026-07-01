public @interface DeprecatedSinceApi {
    int api();
    String message() default "";
}