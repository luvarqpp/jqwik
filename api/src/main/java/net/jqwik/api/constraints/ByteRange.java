package net.jqwik.api.constraints;

import java.lang.annotation.*;

import org.apiguardian.api.*;

import static org.apiguardian.api.API.Status.*;

/**
 * Constrain the range of a generated byte or Byte parameters.
 *
 * Applies to byte or Byte parameters which are also annotated with {@code @ForAll}.
 *
 * @see net.jqwik.api.ForAll
 * @see ShortRange
 * @see IntRange
 * @see LongRange
 * @see FloatRange
 * @see DoubleRange
 * @see BigRange
 */
@Target({ ElementType.ANNOTATION_TYPE, ElementType.PARAMETER, ElementType.TYPE_USE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@API(status = MAINTAINED, since = "1.0")
public @interface ByteRange {
	byte min() default 0;

	byte max() default Byte.MAX_VALUE;
}
