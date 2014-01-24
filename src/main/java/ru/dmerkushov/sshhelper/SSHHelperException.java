/*
 * Copyright 2013 dmerkushov.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ru.dmerkushov.sshhelper;

/**
 *
 * @author Dmitriy Merkushov
 */
public class SSHHelperException extends Exception {
	private static final long serialVersionUID = 1L;

    /**
     * Creates a new instance of <code>SSHHelperException</code> without detail message.
     */
    public SSHHelperException() {
    }


    /**
     * Constructs an instance of <code>SSHHelperException</code> with the specified detail message.
     * @param msg the detail message.
     */
    public SSHHelperException(String msg) {
        super(msg);
    }


    /**
     * Constructs an instance of <code>SSHHelperException</code> with the specified cause.
	 * @param cause the cause (which is saved for later retrieval by the Exception.getCause() method). (A null value is permitted, and indicates that the cause is nonexistent or unknown.)
     */
    public SSHHelperException(Throwable cause) {
        super(cause);
    }


    /**
     * Constructs an instance of <code>SSHHelperException</code> with the specified detail message and cause.
     * @param msg the detail message.
	 * @param cause the cause (which is saved for later retrieval by the Exception.getCause() method). (A null value is permitted, and indicates that the cause is nonexistent or unknown.)
     */
    public SSHHelperException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
