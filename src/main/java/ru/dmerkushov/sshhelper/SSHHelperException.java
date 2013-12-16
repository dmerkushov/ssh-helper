/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ru.dmerkushov.sshhelper;

/**
 *
 * @author shandr
 */
public class SSHHelperException extends Exception {

	/**
	 * Creates a new instance of <code>SSHHelperException</code> without detail message.
	 */
	public SSHHelperException () {
	}

	/**
	 * Constructs an instance of <code>SSHHelperException</code> with the specified detail message.
	 * @param msg the detail message.
	 */
	public SSHHelperException (String msg) {
		super (msg);
	}
}
