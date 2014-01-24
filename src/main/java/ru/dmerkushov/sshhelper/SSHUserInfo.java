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

import com.jcraft.jsch.UIKeyboardInteractive;
import com.jcraft.jsch.UserInfo;

/**
 *
 * @author Dmitriy Merkushov
 */
public class SSHUserInfo implements UserInfo, UIKeyboardInteractive {

	String password;

	public SSHUserInfo (String password) {
		this.password = password;
	}

	@Override
	public String getPassphrase () {
		return null;
	}

	@Override
	public String getPassword () {
		return password;
	}

	@Override
	public boolean promptPassword (String message) {
		return true;
	}

	@Override
	public boolean promptPassphrase (String message) {
		return true;
	}

	@Override
	public boolean promptYesNo (String message) {
		return true;
	}

	@Override
	public void showMessage (String message) {
		SSHHelper.getLoggerWrapper ().info (message);
	}

	@Override
	public String[] promptKeyboardInteractive (String destination, String name, String instruction, String[] prompt, boolean[] echo) {
		SSHHelper.getLoggerWrapper ().entering (destination, name, instruction, prompt, echo);
		String[] reply;
		if ((prompt.length == 1) && (prompt[0].equals ("Password: "))) {
			SSHHelper.getLoggerWrapper ().finer ("Password being asked. Returning the given password as an answer...");
			reply = new String[1];
			reply[0] = password;
			echo[0] = false;
		} else if (prompt.length == 0) {
			SSHHelper.getLoggerWrapper ().finer ("Nothing being asked. Sending an empty reply...");
			reply = new String[0];
		} else {
			StringBuilder promptStrBuilder = new StringBuilder ();
			for (int promptLineIndex = 0; promptLineIndex < prompt.length; promptLineIndex++) {
				promptStrBuilder.append (prompt[promptLineIndex]);
				if (promptLineIndex < prompt.length - 1) {
					promptStrBuilder.append ("\\n");
				}
			}

			throw new UnsupportedOperationException ("Strange prompt. Not supported. Prompt is: \"" + promptStrBuilder.toString () + "\"");
		}

		SSHHelper.getLoggerWrapper ().exiting (reply);
		return reply;
	}

}
