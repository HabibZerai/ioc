/**
 * 
 */
package com.hzerai;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Habib Zerai
 *
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE , ElementType.CONSTRUCTOR , ElementType.METHOD , ElementType.FIELD})
public @interface Autowired {

}
