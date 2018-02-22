package net.jqwik.api.constraints;

import java.lang.annotation.*;

@Target({ ElementType.ANNOTATION_TYPE, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
@Chars(from = 'A', to = 'Z')
@CharRange(min = 'A', max = 'Z')
@Documented
public @interface UpperChars {
}
