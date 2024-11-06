package org.terifan.net.ftp.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;


abstract class DataSocket
{
	InputStream mInputStream;
	OutputStream mOutputStream;
	ProgressListener mProgressListener;


	/**
	 * Returns the IP address and port number of the client socket.
	 */
	abstract String getAddress() throws IOException;


	/**
	 * Starts to listen to a port (active) or to send data to a port (passive).
	 */
	abstract void start() throws IOException;


	/**
	 * Blocks until this DataSocket has finished it's transfer.
	 */
	abstract void block();


	void transfer(final Socket aSocket) throws IOException
	{
		InputStream inputStream = mInputStream != null ? mInputStream : aSocket.getInputStream();
		OutputStream outputStream = mOutputStream != null ? mOutputStream : aSocket.getOutputStream();

		// this sucks, wait for receing server to get ready...
		try
		{
			Thread.sleep(500);
		}
		catch (InterruptedException ex)
		{
		}

		try
		{
			mProgressListener.progressChanged(ProgressListener.TRANSFER_STARTED);

			byte[] buf = new byte[4096];
			long progress = 0;

			for (int len; (len = inputStream.read(buf)) != -1;)
			{
				outputStream.write(buf, 0, len);

				progress += len;
				mProgressListener.progressChanged(progress);
			}

			mProgressListener.progressChanged(ProgressListener.TRANSFER_COMPLETED);
		}
		finally
		{
			try
			{
				if (mOutputStream == null)
				{
					outputStream.close();
				}
			}
			finally
			{
				if (mInputStream == null)
				{
					inputStream.close();
				}
			}
		}
	}
}
