/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ru.dmerkushov.sshhelper;

import ch.ethz.ssh2.Connection;
import ch.ethz.ssh2.SFTPv3Client;
import ch.ethz.ssh2.SFTPv3FileAttributes;
import ch.ethz.ssh2.SFTPv3FileHandle;
import ch.ethz.ssh2.Session;
import ch.ethz.ssh2.StreamGobbler;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.logging.Level;
import ru.dmerkushov.loghelper.LoggerWrapper;

/**
 *
 * @author shandr
 */
public class SSHHelper {

	static LoggerWrapper loggerWrapper = LoggerWrapper.getLoggerWrapper ("SSHHelper");
	static boolean loggerWrapperInitialized = false;

	static void initializeLoggerWrapper () {
		loggerWrapper.configureByDefaultDailyRolling ("log/SSHHelper_%d_%u.log");
		loggerWrapperInitialized = true;
	}

	public static Connection startConnectionWithoutVerification (String hostname, int port, String username, final String password) throws SSHHelperException {
		if (!loggerWrapperInitialized) {
			initializeLoggerWrapper ();
		}
		Object[] methodParams = {hostname, port, username, "(password hidden)"};
		loggerWrapper.entering (methodParams);

		Connection result = startConnection (hostname, port, username, password, true, null);

		loggerWrapper.exiting (result);
		return result;
	}

	public static Connection startConnectionWithVerification (String hostname, int port, String username, final String password, SSHVerifier verifier) throws SSHHelperException {
		if (!loggerWrapperInitialized) {
			initializeLoggerWrapper ();
		}
		Object[] methodParams = {hostname, port, username, "(password hidden)", verifier};
		loggerWrapper.entering (methodParams);

		Connection result = startConnection (hostname, port, username, password, false, verifier);

		loggerWrapper.exiting (result);
		return result;
	}

	/**
	 * Starts an SSH connection
	 *
	 * @param hostname
	 * @param port
	 * @param username
	 * @param password
	 * @param ignoreHostKeyVerification true if you want to disable server's
	 * host key verification. false is recommended
	 * @return
	 * @throws SSHHelperException
	 */
	public static Connection startConnection (String hostname, int port, String username, final String password, boolean ignoreHostKeyVerification, SSHVerifier verifier)
			throws SSHHelperException {

		if (!loggerWrapperInitialized) {
			initializeLoggerWrapper ();
		}
		Object[] methodParams = {hostname, port, username, "(password hidden)", ignoreHostKeyVerification, verifier};
		loggerWrapper.entering (methodParams);

		Connection conn = new Connection (hostname, port);

		try {
			if (ignoreHostKeyVerification) {
				conn.connect ();
			} else {
				conn.connect (verifier);
			}
		} catch (IOException ioE) {
			throw new SSHHelperException ("Received an IOException when connecting. It says,\n" + ioE.getMessage ());
		}

		/* Authenticate.
		 * If you get an IOException saying something like
		 * "Authentication method password not supported by the server at this stage."
		 * then please check the Ganymed-SSH2 FAQ.
		 */
		String[] authMethods = null;
		try {
			authMethods = conn.getRemainingAuthMethods (username);
		} catch (IOException ex) {
			throw new SSHHelperException ("Could not get supported authentication methods. Nested exception is\n" + ex.getMessage ());
		}

		if (loggerWrapper.getLogger ().isLoggable (Level.INFO)) {
			StringBuilder sb = new StringBuilder ();
			sb.append ("Supported authentication methods for this connection are: ");
			for (String authMethod : authMethods) {
				sb.append ("\"" + authMethod + "\" ");
			}
			loggerWrapper.info (sb.toString ());
		}

		try {
			boolean passwordAuthenticationPermitted = conn.isAuthMethodAvailable (username, "password");
			boolean kiAuthenticationPermitted = conn.isAuthMethodAvailable (username, "keyboard-interactive");
			if (passwordAuthenticationPermitted) {
				loggerWrapper.info ("Trying password authentication");

				boolean isAuthenticated = conn.authenticateWithPassword (username, password);

				if (isAuthenticated) {
					loggerWrapper.info ("Authentication successful");
				} else {
					throw new SSHHelperException ("Authentication failed.");
				}
			} else if (kiAuthenticationPermitted) {
				loggerWrapper.info ("Trying keyboard-interactive authentication");

				ch.ethz.ssh2.InteractiveCallback ic = new ch.ethz.ssh2.InteractiveCallback () {
					public String[] replyToChallenge (String name, String instruction, int numPrompts, String[] prompt, boolean[] echo) throws Exception {
						loggerWrapper.info ("Entering replyToChallenge ()");

						loggerWrapper.info ("name: " + name);
						loggerWrapper.info ("instruction: " + instruction);
						loggerWrapper.info ("numPrompts: " + String.valueOf (numPrompts));
						for (String currPrompt : prompt) {
							loggerWrapper.info ("prompt: \"" + currPrompt + "\"");
						}

						if ((numPrompts == 1) && (prompt[0].equals ("Password: "))) {
							loggerWrapper.info ("Password being asked. Returning password as an answer...");
							String[] result = new String[1];
							result[0] = password;
							echo[0] = false;
							return result;
						} else if (numPrompts == 0) {
							loggerWrapper.info ("Nothing being asked. Sending an empty reply...");
							String[] result = new String[0];
							return result;
						} else {
							throw new UnsupportedOperationException ("Strange prompt. Not supported yet.");
						}
					}
				};

				boolean isAuthenticated = conn.authenticateWithKeyboardInteractive (username, ic);

				if (isAuthenticated) {
					loggerWrapper.info ("Authentication successful");
				} else {
					throw new SSHHelperException ("Authentication failed.");
				}
			} else {
				throw new SSHHelperException ("Neither password (\"password\") nor keyboard-interactive (\"keyboard-interactive\") authentication methods are permitted by server");
			}
		} catch (IOException ioE) {
			throw new SSHHelperException ("Received an IOException when authenticating. It says,\n" + ioE.getMessage ());
		}

		loggerWrapper.exiting (conn);
		return conn;
	}

	/**
	 * Execute a command on SSH server
	 *
	 * @param conn
	 * @param command is the command to execute. With UNIX-like SSH servers
	 * (OpenSSH, etc) you may run several commands like "pwd && ls && cat
	 * test.txt"
	 * @return output of the command
	 * @throws SSHHelperException
	 */
	public static String executeCommand (Connection conn, String command) throws SSHHelperException {
		if (!loggerWrapperInitialized) {
			initializeLoggerWrapper ();
		}
		Object[] methodParams = {conn, command};
		loggerWrapper.entering (methodParams);

		String result = "";

		/* Create a session */

		try {
			Session sess = conn.openSession ();
			sess.execCommand (command);

			/*
			 * This basic example does not handle stderr, which is sometimes dangerous
			 * (please read the FAQ).
			 */

			InputStream sshStdout = new StreamGobbler (sess.getStdout ());
			InputStream sshStderr = new StreamGobbler (sess.getStderr ());

			BufferedReader sshStdoutReader = new BufferedReader (new InputStreamReader (sshStdout));

			String line = sshStdoutReader.readLine ();
			while (line != null) {
				result = result.concat (line);
				
				line = sshStdoutReader.readLine ();
				if (line != null) {
					result = result.concat ("\n");
				}
			}

			/* Show exit status, if available (otherwise "null") */

			//DEBUG
			loggerWrapper.info ("Command " + command + " has exited with exit status " + String.valueOf (sess.getExitStatus ()));

			/* Close this session */

			sess.close ();

		} catch (IOException ioE) {
			throw new SSHHelperException ("Input/output exception when trying to execute a command: " + command);
		}

		loggerWrapper.exiting (result);
		return result;
	}

	/**
	 * Start an SFTP client
	 *
	 * @param conn
	 * @param charsetName
	 * @param ignoreIllegalCharset true if the "Unsupported charset" error
	 * should be ignored
	 * @return
	 * @throws SSHHelperException
	 */
	public static SFTPv3Client startSFTP (Connection conn, String charsetName, boolean ignoreIllegalCharset) throws SSHHelperException {
		if (!loggerWrapperInitialized) {
			initializeLoggerWrapper ();
		}
		Object[] methodParams = {conn, charsetName, ignoreIllegalCharset};
		loggerWrapper.entering (methodParams);

		SFTPv3Client result;

		try {
			result = new SFTPv3Client (conn);

		} catch (IOException ioE) {
			throw new SSHHelperException ("Received an IOException when connecting. It says,\n" + ioE.getMessage ());
		}

		try {
			if (Charset.isSupported (charsetName)) {
				result.setCharset (charsetName);
			} else {
				if (!ignoreIllegalCharset) {
					throw new SSHHelperException ("Charset " + charsetName + " not supported");
				}
			}
		} catch (IOException ioE) {
			throw new SSHHelperException ("Received an IOException when setting charset. It says,\n" + ioE.getMessage ());
		}

		loggerWrapper.exiting (result);
		return result;
	}

	/**
	 * Reads the contents of a text file in the specified charset from SFTP
	 *
	 * @param sftpClient
	 * @param filename
	 * @param charset
	 * @return
	 * @throws SSHHelperException
	 */
	public static String readTextFileFromSFTP (SFTPv3Client sftpClient, String filename, String charset) throws SSHHelperException {
		if (!loggerWrapperInitialized) {
			initializeLoggerWrapper ();
		}
		Object[] methodParams = {sftpClient, filename, charset};
		loggerWrapper.entering (methodParams);

		String result = null;

		byte[] fileBytes = SSHHelper.readBinaryFileFromSFTP (sftpClient, filename);

		if (Charset.isSupported (charset)) {
			try {
				result = new String (fileBytes, charset);
			} catch (java.io.UnsupportedEncodingException ueE) {
				throw new SSHHelperException ("Received UnsupportedEncodingException when reading file '" + filename + "'. Charset provided is " + charset + ". UnsupportedEncodingException says,\n" + ueE.getMessage ());
			}
		} else {
			throw new SSHHelperException ("Charset " + charset + " is not supported");
		}

		loggerWrapper.exiting ();	// Not logging the result as it may be huge
		return result;
	}

	/**
	 * Reads the contents of a text file in UTF-8 charset from SFTP
	 *
	 * @param sftpClient
	 * @param filename
	 * @return
	 * @throws SSHHelperException
	 */
	public static String readTextFileFromSFTP (SFTPv3Client sftpClient, String filename) throws SSHHelperException {
		if (!loggerWrapperInitialized) {
			initializeLoggerWrapper ();
		}
		Object[] methodParams = {sftpClient, filename};
		loggerWrapper.entering (methodParams);

		String result = SSHHelper.readTextFileFromSFTP (sftpClient, filename, "UTF-8");

		loggerWrapper.exiting ();	// Not logging the result as it may be huge
		return result;
	}

	/**
	 * Copies a file from SFTP to local filesystem byte by byte
	 *
	 * @param sftpClient
	 * @param remoteFilename
	 * @param localFilename
	 * @return
	 * @throws SSHHelperException
	 */
	public static File copyFileFromSFTPToLocal (SFTPv3Client sftpClient, String remoteFilename, String localFilename) throws SSHHelperException {
		if (!loggerWrapperInitialized) {
			initializeLoggerWrapper ();
		}
		Object[] methodParams = {sftpClient, remoteFilename, localFilename};
		loggerWrapper.entering (methodParams);

		File result = null;

		byte[] fileContents = readBinaryFileFromSFTP (sftpClient, remoteFilename);

		result = new File (localFilename);

		FileOutputStream resultFos;

		try {
			resultFos = new FileOutputStream (result);
		} catch (FileNotFoundException fnfE) {
			throw new SSHHelperException ("Received a FileNotFoundException when trying to create output file '" + localFilename + "'. It says,\n" + fnfE.getMessage ());
		}

		try {
			resultFos.write (fileContents);
		} catch (IOException ioE) {
			throw new SSHHelperException ("Received an IOException when trying to write to output file '" + localFilename + "'. It says,\n" + ioE.getMessage ());
		}

		try {
			resultFos.close ();
		} catch (IOException ioE) {
			throw new SSHHelperException ("Received an IOException when trying to close output file '" + localFilename + "'. It says,\n" + ioE.getMessage ());
		}

		loggerWrapper.exiting (result);
		return result;
	}

	/**
	 * Reads the contents of a binary file from SFTP. Can read no more than
	 * Integer.MAX_VALUE (currently 2^31-1) bytes
	 *
	 * @param sftpClient
	 * @param filename
	 * @return
	 * @throws SSHHelperException
	 */
	public static byte[] readBinaryFileFromSFTP (SFTPv3Client sftpClient, String filename) throws SSHHelperException {
		if (!loggerWrapperInitialized) {
			initializeLoggerWrapper ();
		}
		Object[] methodParams = {sftpClient, filename};
		loggerWrapper.entering (methodParams);

		SFTPv3FileHandle fHandle;
		SFTPv3FileAttributes fAttr;

		try {
			fHandle = sftpClient.openFileRO (filename);
		} catch (IOException ioE) {
			throw new SSHHelperException ("Received an IOException when opening file '" + filename + "'. It says,\n" + ioE.getMessage ());
		}

		try {
			fAttr = sftpClient.fstat (fHandle);
		} catch (IOException ioE) {
			throw new SSHHelperException ("Received an IOException when receiving length of file '" + filename + "'. It says,\n" + ioE.getMessage ());
		}

		long filesize = fAttr.size;
		int filesizeInt = 0;
		if (filesize > Integer.MAX_VALUE) {
			throw new SSHHelperException ("File '" + filename + "' is too big. Maximum size is " + String.valueOf (Integer.MAX_VALUE) + " bytes");
		} else {
			filesizeInt = (int) filesize;
		}

		loggerWrapper.info ("File length: " + String.valueOf (filesize));

		byte[] result = new byte[filesizeInt];

		// Ganymed SFTP implementation can only read files by blocks smaller than 32768
		int toReadBytes = filesizeInt;
		if (toReadBytes > 32767) {
			toReadBytes = 32767;
		}

		int readResult = -1;
		int currentOffset = 0;
		int totalRead = 0;
		do {
			try {
				readResult = sftpClient.read (fHandle, currentOffset, result, currentOffset, toReadBytes);
			} catch (IOException ioE) {
				throw new SSHHelperException ("Received an IOException when reading file '" + filename + "'. It says,\n" + ioE.getMessage ());
			}
			if (readResult > 0) {
				currentOffset += readResult;
				totalRead += readResult;
			}
		} while (readResult > 0);

		try {
			sftpClient.closeFile (fHandle);
		} catch (IOException ioE) {
			throw new SSHHelperException ("Received an IOException when closing file '" + filename + "'. It says,\n" + ioE.getMessage ());
		}

		loggerWrapper.info ("Successfully read " + String.valueOf (totalRead) + " bytes from file '" + filename + "'");

		loggerWrapper.exiting ();	// Not logging the result as it may be huge
		return result;
	}
}
