/**
 * 
 */
package com.hzerai;

/**
 * @author Habib Zerai
 *
 */
public class FrameworkException extends RuntimeException {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public FrameworkException(Throwable e) {
		super(e);
	}
	
	public FrameworkException(String msg) {
		super(msg);
	}
	
	public FrameworkException(String  msg , Throwable e) {
		super(e);
	}

}
