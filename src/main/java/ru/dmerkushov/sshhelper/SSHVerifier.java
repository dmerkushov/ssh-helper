/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ru.dmerkushov.sshhelper;

import ch.ethz.ssh2.ServerHostKeyVerifier;

/**
 *
 * @author Dmitriy Merkushov
 * @deprecated
 */
public abstract class SSHVerifier implements ServerHostKeyVerifier {

	/**
	 * Check the hostname, port, host key algorithm and server host key (may use <code>verifyServerHostKey (byte[], String)</code>)
	 * @param hostname
	 * @param port
	 * @param serverHostKeyAlgorithm
	 * @param serverHostKey
	 * @return true if verification has found out that the server is correct
	 * @throws Exception
	 * @see SSHVerifier#verifyServerHostKey(byte[], java.lang.String)
	 */
	@Override
	public abstract boolean verifyServerHostKey (String hostname, int port, String serverHostKeyAlgorithm, byte[] serverHostKey) throws Exception;

	/**
	 * Check the host key itself
	 * @param serverHostKey
	 * @param correctServerHostKey in hex form; values separated by <code>:</code>
	 * @return
	 * @see SSHVerifier#verifyServerHostKey(java.lang.String, int, java.lang.String, byte[])
	 */
	public boolean verifyServerHostKey (byte[] serverHostKey, String correctServerHostKey) {
		boolean result = false;

		String hostKey = this.getHexStringFromByteArray (serverHostKey, ":").toUpperCase ();
		String correctHostKey = correctServerHostKey.toUpperCase ();
		if (hostKey.equals (correctHostKey)) {
			result = true;
		}

		return result;
	}

	/**
	 * Get the hex form of a byte array
	 * @param in
	 * @param separator
	 * @return
	 */
	public String getHexStringFromByteArray (byte[] in, String separator) {
		StringBuffer resultBuffer = new StringBuffer(in.length * 2);

		for (int inIndex = 0; inIndex < in.length; inIndex++) {
			byte ch;
			if (in == null || in.length <= 0) {
				return null;
			}

			String hexCodes[] = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "a", "b", "c", "d", "e", "f"};

			while (inIndex < in.length) {

				ch = (byte) (in[inIndex] & 0xF0);		// Strip off high nibble
				ch = (byte) (ch >>> 4);			// shift the bits down
				ch = (byte) (ch & 0x0F);		// must do this is high order bit is on!
				resultBuffer.append(hexCodes[(int) ch]);	// convert the nibble to a String Character
				ch = (byte) (in[inIndex] & 0x0F);		// Strip off low nibble
				resultBuffer.append(hexCodes[(int) ch]);	// convert the nibble to a String Character

				if (inIndex < in.length - 1) {
					resultBuffer.append(separator);
				}

				inIndex++;
			}
		}

		String result = new String(resultBuffer);
		return result;
	}

}
