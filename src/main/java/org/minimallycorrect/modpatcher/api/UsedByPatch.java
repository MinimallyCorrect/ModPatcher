package org.minimallycorrect.modpatcher.api;

import java.lang.annotation.*;

/**
 * Indicates that a method or field is used by a patch
 * <p>
 * value should indicate class of that patch, or if not a class, a unique path
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.SOURCE)
public @interface UsedByPatch {
	String value();
}
