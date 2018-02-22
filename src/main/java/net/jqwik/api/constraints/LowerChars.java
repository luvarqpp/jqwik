package net.jqwik.api.constraints;

import java.lang.annotation.*;

@Target({ ElementType.ANNOTATION_TYPE, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
@Chars(from = 'a', to = 'z')
@CharRange(min = 'a', max = 'z')
@Documented
public @interface LowerChars {
}
