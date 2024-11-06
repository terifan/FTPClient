package org.terifan.net.ftp.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;


class PassiveDataSocket extends DataSocket
{
	private String mAddress;
	private boolean mStopped;


	/**
	 * Creates a socket for sending data to the FTP server.
	 */
	public static PassiveDataSocket createOutputSocket(String aAddress, InputStream aInputStream, ProgressListener aProgressListener) throws IOException
	{
		String address = aAddress.substring(aAddress.lastIndexOf("(")+1, aAddress.lastIndexOf(")"));

		PassiveDataSocket socket = new PassiveDataSocket();
		socket.mAddress = address;
		socket.mInputStream = aInputStream;
		socket.mProgressListener = aProgressListener != null ? aProgressListener : (e)->{};
		return socket;
	}


	/**
	 * Creates a socket for receiving data from the FTP server.
	 */
	public static PassiveDataSocket createInputSocket(String aAddress, OutputStream aOutputStream, ProgressListener aProgressListener) throws IOException
	{
		String address = aAddress.substring(aAddress.lastIndexOf("(")+1, aAddress.lastIndexOf(")"));

		PassiveDataSocket socket = new PassiveDataSocket();
		socket.mAddress = address;
		socket.mOutputStream = aOutputStream;
		socket.mProgressListener = aProgressListener != null ? aProgressListener : (e)->{};
		return socket;
	}


	/**
	 * Returns the IP address and port number of the client socket.
	 */
	@Override
	public String getAddress() throws IOException
	{
		return mAddress;
	}


	/**
	 * Blocks until this DataSocket has finished it's transfer.
	 */
	@Override
	public void block()
	{
		while (!mStopped)
		{
			try
			{
				Thread.sleep(10);
			}
			catch (Exception e)
			{
			}
		}
	}


	/**
	 * Initializes the ServerSocket and starts listening on a port.
	 */
	@Override
	public void start() throws IOException
	{
		WorkerThread workerThread = new WorkerThread();
		workerThread.start();

		while (!workerThread.mIsStarted)
		{
			try
			{
				Thread.sleep(10);
			}
			catch (Exception e)
			{
			}
		}
	}


	private class WorkerThread extends Thread
	{
		public boolean mIsStarted;


		/**
		 * Waits for server connection and transmits data with the server.
		 */
		@Override
		public void run()
		{
			mIsStarted = true;

			try
			{
				String [] address = mAddress.split(",");

				if (address.length != 6)
				{
					throw new IllegalStateException("Address has bad format: " + mAddress);
				}

				try (Socket socket = new Socket(address[0] + "." + address[1] + "." + address[2] + "." + address[3], Integer.parseInt(address[4]) * 256 + Integer.parseInt(address[5])))
				{
					socket.setSoTimeout(60_000);
					transfer(socket);
				}
			}
			catch (IOException e)
			{
				e.printStackTrace(System.out);
			}
			finally
			{
				mStopped = true;
			}
		}
	}
}