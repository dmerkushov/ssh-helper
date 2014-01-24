/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ru.dmerkushov.sshhelper;

import ch.ethz.ssh2.Connection;
import ch.ethz.ssh2.SFTPv3Client;
import ch.ethz.ssh2.SFTPv3FileAttributes;
import ch.ethz.ssh2.SFTPv3FileHandle;
import ch.ethz.ssh2.StreamGobbler;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.ChannelSftpExtDM;
import com.jcraft.jsch.ConfigRepository;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpException;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.logging.Level;
import ru.dmerkushov.loghelper.LoggerWrapper;
import ru.dmerkushov.oshelper.OSHelper.ProcessReturn;

/**
 *
 * @author shandr
 */
public class SSHHelper {

	static LoggerWrapper loggerWrapper;

	public static LoggerWrapper getLoggerWrapper () {
		if (loggerWrapper == null) {
			loggerWrapper = LoggerWrapper.getLoggerWrapper ("ru.dmerkushov.sshhelper.SSHHelper");
			loggerWrapper.configureByDefaultDailyRolling ("log/SSHHelper_%d_%u.log");
		}
		return loggerWrapper;
	}

	static JSch jsch = null;

	/**
	 * Connect to an SSH host
	 *
	 * @param hostname
	 * @param port
	 * @param username
	 * @param password
	 * @param knownHostsFilePath
	 * @param identityFilePath
	 * @return
	 * @throws SSHHelperException
	 */
	public static Session connect (String hostname, int port, String username, String password, String knownHostsFilePath, String identityFilePath) throws SSHHelperException {
		getLoggerWrapper ().entering (hostname, port, username, password, knownHostsFilePath, identityFilePath);

		Session session;

		try {

			if (jsch == null) {
				jsch = new JSch ();
			}
			session = jsch.getSession (username, hostname, port);
			session.setUserInfo (new SSHUserInfo (password));
			if (knownHostsFilePath != null) {
				jsch.setKnownHosts (knownHostsFilePath);
			}
			if (identityFilePath != null) {
				jsch.addIdentity (identityFilePath);
			}
			session.connect ();

		} catch (JSchException ex) {
			throw new SSHHelperException (ex);
		}

		getLoggerWrapper ().exiting (session);
		return session;
	}

	/**
	 * Disconnect from an SSH host
	 *
	 * @param session
	 * @throws SSHHelperException
	 */
	public static void disconnect (Session session) throws SSHHelperException {
		getLoggerWrapper ().entering (session);

		if (!session.isConnected ()) {
			session.disconnect ();
		}

		getLoggerWrapper ().exiting ();
	}

	/**
	 * Open an SFTP channel in an SSH session
	 *
	 * @param session
	 * @param encoding
	 * @return
	 * @throws SSHHelperException
	 * @throws NullPointerException when there is no JSch
	 */
	public static ChannelSftpExtDM openChannelSftpExtDM (Session session, String encoding) throws SSHHelperException {
		getLoggerWrapper ().entering (session, encoding);

		if (jsch == null) {
			throw new NullPointerException ("jsch");
		}

		ChannelSftpExtDM channelSftp;

		channelSftp = new ChannelSftpExtDM ();
		channelSftp.setSession (session);
		ConfigRepository configRepository = jsch.getConfigRepository ();
		if (configRepository != null) {
			ConfigRepository.Config config = configRepository.getConfig ("127.0.0.1");

			String value = null;

			value = config.getValue ("ForwardAgent");
			if (value != null) {
				channelSftp.setAgentForwarding (value.equals ("yes"));
			}

			value = config.getValue ("RequestTTY");
			if (value != null) {
				channelSftp.setPty (value.equals ("yes"));
			}
		}

		((Channel) channelSftp).run ();

		try {
			getLoggerWrapper ().finest ("Server SFTP version: " + channelSftp.getServerVersion ());
		} catch (SftpException ex) {
			throw new SSHHelperException (ex);
		}

		if (encoding != null && Charset.isSupported (encoding)) {
			try {
				channelSftp.setFilenameEncoding (encoding);
			} catch (SftpException ex) {
				if (ex.getMessage ().contains ("encoding can not be changed for this sftp server.")) {
					getLoggerWrapper ().warning ("Encoding cannot be changed for this SFTP server");
				} else {
					throw new SSHHelperException (ex);
				}
			}
		} else if (!Charset.isSupported (encoding)) {
			getLoggerWrapper ().warning ("Encoding " + encoding + " is not supported");
		}

		getLoggerWrapper ().exiting (channelSftp);
		return channelSftp;
	}

	/**
	 * Close SFTP channel. The session is disconnected too.
	 *
	 * @param channelSftp
	 * @throws SSHHelperException
	 */
	public static void closeChannelSftpExtDM (ChannelSftpExtDM channelSftp) throws SSHHelperException {
		getLoggerWrapper ().entering (channelSftp);

		try {

			channelSftp.disconnect ();
			disconnect (channelSftp.getSession ());

		} catch (JSchException ex) {
			throw new SSHHelperException (ex);
		}

		getLoggerWrapper ().exiting ();
	}

	/**
	 * Run a command in a session. After running, the session will be closed
	 *
	 * @param session
	 * @param command
	 * @return
	 * @throws SSHHelperException
	 */
	public static ProcessReturn executeCommand (Session session, String command) throws SSHHelperException {
		getLoggerWrapper ().entering (session, command);

		ProcessReturn processReturn = new ProcessReturn ();

		try {

			ChannelExec channelExec = (ChannelExec) session.openChannel ("exec");
			channelExec.setCommand (command);

			InputStream stdoutStream = channelExec.getInputStream ();
			InputStream stderrStream = channelExec.getErrStream ();

			channelExec.connect ();

			StringBuilder stdoutBuilder = new StringBuilder ();
			StringBuilder stderrBuilder = new StringBuilder ();

			byte[] readBuffer = new byte[1024];
			boolean channelOpen = !channelExec.isClosed ();
			while (channelOpen) {
				while (stdoutStream.available () > 0) {
					int readBytes = stdoutStream.read (readBuffer, 0, readBuffer.length);
					if (readBytes < 0) {
						break;
					}
					stdoutBuilder.append (new String (Arrays.copyOf (readBuffer, readBytes)));
				}
				while (stderrStream.available () > 0) {
					int readBytes = stderrStream.read (readBuffer, 0, readBuffer.length);
					if (readBytes < 0) {
						break;
					}
					stderrBuilder.append (new String (Arrays.copyOf (readBuffer, readBytes)));
				}
				Thread.sleep (300);
				channelOpen = !channelExec.isClosed ();
			}

			channelExec.disconnect ();

			processReturn.exitCode = channelExec.getExitStatus ();
			processReturn.stdout = stdoutBuilder.toString ();
			processReturn.stderr = stderrBuilder.toString ();
			disconnect (session);

		} catch (JSchException | IOException | InterruptedException ex) {
			throw new SSHHelperException (ex);
		}

		getLoggerWrapper ().exiting (processReturn);
		return processReturn;
	}

	/**
	 * Start a connection without verifying the host
	 *
	 * @param hostname
	 * @param port
	 * @param username
	 * @param password
	 * @return
	 * @throws SSHHelperException
	 * @deprecated
	 */
	public static Connection startConnectionWithoutVerification (String hostname, int port, String username, final String password) throws SSHHelperException {
		getLoggerWrapper ().entering (hostname, port, username, "(password hidden)");

		Connection result = startConnection (hostname, port, username, password, true, null);

		getLoggerWrapper ().exiting (result);
		return result;
	}

	/**
	 * Start a connection verifying the host
	 *
	 * @param hostname
	 * @param port
	 * @param username
	 * @param password
	 * @param verifier
	 * @return
	 * @throws SSHHelperException
	 * @deprecated
	 */
	public static Connection startConnectionWithVerification (String hostname, int port, String username, final String password, SSHVerifier verifier) throws SSHHelperException {
		getLoggerWrapper ().entering (hostname, port, username, "(password hidden)", verifier);

		Connection result = startConnection (hostname, port, username, password, false, verifier);

		getLoggerWrapper ().exiting (result);
		return result;
	}

	/**
	 * Starts an SSH connection
	 *
	 * @param hostname
	 * @param port
	 * @param username
	 * @param password
	 * @param ignoreHostKeyVerification true if you want to disable server's host key verification. false is recommended
	 * @param verifier Host key verifier. If host key verification is disabled, will not be used
	 * @return
	 * @throws SSHHelperException
	 * @deprecated
	 */
	public static Connection startConnection (String hostname, int port, String username, final String password, boolean ignoreHostKeyVerification, SSHVerifier verifier)
			throws SSHHelperException {

		Object[] methodParams = {hostname, port, username, "(password hidden)", ignoreHostKeyVerification, verifier};
		getLoggerWrapper ().entering (methodParams);

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

		/*
		 * Authenticate.
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

		if (getLoggerWrapper ().getLogger ().isLoggable (Level.INFO)) {
			StringBuilder sb = new StringBuilder ();
			sb.append ("Supported authentication methods for this connection are: ");
			for (String authMethod : authMethods) {
				sb.append ("\"").append (authMethod).append ("\" ");
			}
			getLoggerWrapper ().info (sb.toString ());
		}

		try {
			boolean passwordAuthenticationPermitted = conn.isAuthMethodAvailable (username, "password");
			boolean kiAuthenticationPermitted = conn.isAuthMethodAvailable (username, "keyboard-interactive");
			if (passwordAuthenticationPermitted) {
				getLoggerWrapper ().info ("Trying password authentication");

				boolean isAuthenticated = conn.authenticateWithPassword (username, password);

				if (isAuthenticated) {
					getLoggerWrapper ().info ("Authentication successful");
				} else {
					throw new SSHHelperException ("Authentication failed.");
				}
			} else if (kiAuthenticationPermitted) {
				getLoggerWrapper ().info ("Trying keyboard-interactive authentication");

				ch.ethz.ssh2.InteractiveCallback ic = new ch.ethz.ssh2.InteractiveCallback () {
					public String[] replyToChallenge (String name, String instruction, int numPrompts, String[] prompt, boolean[] echo) throws Exception {
						getLoggerWrapper ().entering ();

						getLoggerWrapper ().finer ("name: " + name);
						getLoggerWrapper ().finer ("instruction: " + instruction);
						getLoggerWrapper ().finer ("numPrompts: " + String.valueOf (numPrompts));
						for (String currPrompt : prompt) {
							getLoggerWrapper ().finer ("prompt: \"" + currPrompt + "\"");
						}

						String[] reply;
						if ((numPrompts == 1) && (prompt[0].equals ("Password: "))) {
							getLoggerWrapper ().finer ("Password being asked. Returning password as an answer...");
							reply = new String[1];
							reply[0] = password;
							echo[0] = false;
						} else if (numPrompts == 0) {
							getLoggerWrapper ().finer ("Nothing being asked. Sending an empty reply...");
							reply = new String[0];
						} else {
							throw new UnsupportedOperationException ("Strange prompt. Not supported yet.");
						}

						loggerWrapper.exiting (reply);
						return reply;
					}
				};

				boolean isAuthenticated = conn.authenticateWithKeyboardInteractive (username, ic);

				if (isAuthenticated) {
					getLoggerWrapper ().info ("Authentication successful");
				} else {
					throw new SSHHelperException ("Authentication failed.");
				}
			} else {
				throw new SSHHelperException ("Neither password (\"password\") nor keyboard-interactive (\"keyboard-interactive\") authentication methods are permitted by server");
			}
		} catch (IOException ioE) {
			throw new SSHHelperException ("Received an IOException when authenticating. It says,\n" + ioE.getMessage ());
		}

		getLoggerWrapper ().exiting (conn);
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
	 * @deprecated
	 */
	public static String executeCommand (Connection conn, String command) throws SSHHelperException {
		getLoggerWrapper ().entering (conn, command);

		String result = "";

		/*
		 * Create a session
		 */
		ch.ethz.ssh2.Session sess;
		try {
			sess = conn.openSession ();
		} catch (IOException ex) {
			throw new SSHHelperException (ex);
		}
		try {
			sess.execCommand (command);

			/*
			 * This basic example does not handle stderr, which is sometimes dangerous
			 * (please read the FAQ).
			 */
			InputStream sshStdout = new StreamGobbler (sess.getStdout ());
			InputStream sshStderr = new StreamGobbler (sess.getStderr ());

			BufferedReader sshStdoutReader = new BufferedReader (new InputStreamReader (sshStdout));

			String stdoutLine = sshStdoutReader.readLine ();
			StringBuilder stdoutWhole = new StringBuilder ();
			while (stdoutLine != null) {
				stdoutWhole.append (stdoutLine);
				stdoutLine = sshStdoutReader.readLine ();
				if (stdoutLine != null) {
					stdoutWhole.append ("\n");
				}
			}

			BufferedReader sshStderrReader = new BufferedReader (new InputStreamReader (sshStderr));

			String stderrLine = sshStderrReader.readLine ();
			StringBuilder stderrWhole = new StringBuilder ();
			while (stderrLine != null) {
				stderrWhole.append (stderrLine);
				stderrLine = sshStderrReader.readLine ();
				if (stderrLine != null) {
					stderrWhole.append ("\n");
				}
			}

			/*
			 * Show exit status, if available (otherwise "null")
			 */
			//DEBUG
			getLoggerWrapper ().info ("Command " + command + " has exited with exit status " + String.valueOf (sess.getExitStatus ()));
			getLoggerWrapper ().finest ("STDOUT of command " + command + ":\n" + stdoutWhole.toString () + "\nSTDERR of command " + command + ":\n" + stderrWhole.toString ());

			result = stdoutWhole.toString ();
		} catch (IOException ex) {
			throw new SSHHelperException (ex);
		} finally {
			/*
			 * Close this session
			 */
			sess.close ();
		}

		getLoggerWrapper ().exiting (result);
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
	 * @deprecated
	 */
	public static SFTPv3Client startSFTP (Connection conn, String charsetName, boolean ignoreIllegalCharset) throws SSHHelperException {
		Object[] methodParams = {conn, charsetName, ignoreIllegalCharset};
		getLoggerWrapper ().entering (methodParams);

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

		getLoggerWrapper ().exiting (result);
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
	 * @deprecated
	 */
	public static String readTextFileFromSFTP (SFTPv3Client sftpClient, String filename, String charset) throws SSHHelperException {
		Object[] methodParams = {sftpClient, filename, charset};
		getLoggerWrapper ().entering (methodParams);

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

		getLoggerWrapper ().exiting ();	// Not logging the result as it may be huge
		return result;
	}

	/**
	 * Reads the contents of a text file in UTF-8 charset from SFTP
	 *
	 * @param sftpClient
	 * @param filename
	 * @return
	 * @throws SSHHelperException
	 * @deprecated
	 */
	public static String readTextFileFromSFTP (SFTPv3Client sftpClient, String filename) throws SSHHelperException {
		Object[] methodParams = {sftpClient, filename};
		getLoggerWrapper ().entering (methodParams);

		String result = SSHHelper.readTextFileFromSFTP (sftpClient, filename, "UTF-8");

		getLoggerWrapper ().exiting ();	// Not logging the result as it may be huge
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
	 * @deprecated
	 */
	public static File copyFileFromSFTPToLocal (SFTPv3Client sftpClient, String remoteFilename, String localFilename) throws SSHHelperException {
		Object[] methodParams = {sftpClient, remoteFilename, localFilename};
		getLoggerWrapper ().entering (methodParams);

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

		getLoggerWrapper ().exiting (result);
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
	 * @deprecated
	 */
	public static byte[] readBinaryFileFromSFTP (SFTPv3Client sftpClient, String filename) throws SSHHelperException {
		Object[] methodParams = {sftpClient, filename};
		getLoggerWrapper ().entering (methodParams);

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

		getLoggerWrapper ().info ("File length: " + String.valueOf (filesize));

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

		getLoggerWrapper ().info ("Successfully read " + String.valueOf (totalRead) + " bytes from file '" + filename + "'");

		getLoggerWrapper ().exiting ();	// Not logging the result as it may be huge
		return result;
	}

	/**
	 * Reads the contents of a binary file from SFTP. Can read no more than
	 * Integer.MAX_VALUE (currently 2^31-1) bytes
	 *
	 * @param channelSftp
	 * @param filename
	 * @return
	 * @throws SSHHelperException
	 */
	public static byte[] readBinaryFileFromSFTP (ChannelSftpExtDM channelSftp, String filename) throws SSHHelperException {
		getLoggerWrapper ().entering (channelSftp, filename);

		byte[] result;

		try {

			ByteArrayOutputStream baos = new ByteArrayOutputStream ();
			channelSftp.get (filename, baos);

			result = baos.toByteArray ();

			getLoggerWrapper ().info ("Successfully read " + String.valueOf (result.length) + " bytes from file '" + filename + "'");

		} catch (SftpException ex) {
			throw new SSHHelperException (ex);
		}

		getLoggerWrapper ().exiting ();	// Not logging the result as it may be huge
		return result;
	}

	/**
	 * Reads the contents of a text file in the specified charset from SFTP
	 *
	 * @param channelSftp
	 * @param filename
	 * @param charset
	 * @return
	 * @throws SSHHelperException
	 */
	public static String readTextFileFromSFTP (ChannelSftpExtDM channelSftp, String filename, String charset) throws SSHHelperException {
		Object[] methodParams = {channelSftp, filename, charset};
		getLoggerWrapper ().entering (methodParams);

		String result = null;

		byte[] fileBytes = SSHHelper.readBinaryFileFromSFTP (channelSftp, filename);

		if (Charset.isSupported (charset)) {
			try {
				result = new String (fileBytes, charset);
			} catch (java.io.UnsupportedEncodingException ueE) {
				throw new SSHHelperException ("Received UnsupportedEncodingException when reading file '" + filename + "'. Charset provided is " + charset + ". UnsupportedEncodingException says,\n" + ueE.getMessage ());
			}
		} else {
			throw new SSHHelperException ("Charset " + charset + " is not supported");
		}

		getLoggerWrapper ().exiting ();	// Not logging the result as it may be huge
		return result;
	}

	/**
	 * Reads the contents of a text file in UTF-8 charset from SFTP
	 *
	 * @param channelSftp
	 * @param filename
	 * @return
	 * @throws SSHHelperException
	 */
	public static String readTextFileFromSFTP (ChannelSftpExtDM channelSftp, String filename) throws SSHHelperException {
		Object[] methodParams = {channelSftp, filename};
		getLoggerWrapper ().entering (methodParams);

		String result = SSHHelper.readTextFileFromSFTP (channelSftp, filename, "UTF-8");

		getLoggerWrapper ().exiting ();	// Not logging the result as it may be huge
		return result;
	}
}
