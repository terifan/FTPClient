package org.terifan.net.ftp.client;

import java.io.IOException;


/**
 * Exception thrown when an unexpected response code is encountered.
 */
public class ResponseCodeException extends IOException
{
	private static final long serialVersionUID = 1L;


	public ResponseCodeException(String aMessage)
	{
		super(aMessage);
	}
}
