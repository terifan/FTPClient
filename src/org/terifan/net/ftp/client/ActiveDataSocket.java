package org.terifan.net.ftp.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Random;


class ActiveDataSocket extends DataSocket
{
	private ServerSocket mServerSocket;
	private boolean mStopped;


	/**
	 * Creates a socket for sending data to the FTP server.
	 */
	public static ActiveDataSocket createOutputSocket(InputStream aInputStream, ProgressListener aProgressListener) throws IOException
	{
		ActiveDataSocket socket = new ActiveDataSocket();
		socket.mInputStream = aInputStream;
		socket.mProgressListener = aProgressListener != null ? aProgressListener : (e)->{};
		return socket;
	}


	/**
	 * Creates a socket for receiving data from the FTP server.
	 */
	public static ActiveDataSocket createInputSocket(OutputStream aOutputStream, ProgressListener aProgressListener) throws IOException
	{
		ActiveDataSocket socket = new ActiveDataSocket();
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
		return mServerSocket.getInetAddress().getHostAddress().replace('.',',') + "," + (mServerSocket.getLocalPort() >> 8) + "," + (mServerSocket.getLocalPort() & 255);
	}


	/**
	 * Blocks until this DataSocket has finished it's transfer.
	 */
	@Override
	public void block()
	{
		while (!mStopped)
		{
			try{Thread.sleep(1);}catch(Exception e){}
		}
	}


	/**
	 * Initializes the ServerSocket and starts listening on a port.
	 */
	@Override
	public void start() throws IOException
	{
		InetAddress [] addresses = InetAddress.getAllByName(InetAddress.getLocalHost().getHostName());

		int index = 0;

		if (addresses.length > 0)
		{
			for (; index < addresses.length; index++)
			{
				if (!addresses[index].isSiteLocalAddress())
				{
					break;
				}
			}
			index %= addresses.length;
		}

// TODO: use something other than random...

		for (;;)
		{
			try
			{
				mServerSocket = new ServerSocket(10000 + new Random().nextInt(50000), 0, addresses[index]);
				break;
			}
			catch (BindException e)
			{
				if (!e.getMessage().equals("Address already in use: JVM_Bind"))
				{
					throw e;
				}
				System.out.println("Exception Ignored - Retrying...");
			}

			try
			{
				Thread.sleep(500);
			}
			catch (Exception e)
			{
			}
		}

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


		/** Waits for server connection and transmits data with the server. */
		@Override
		public void run()
		{
			mIsStarted = true;

			try
			{
				try
				{
					mServerSocket.setSoTimeout(60_000);

					try (Socket socket = mServerSocket.accept())
					{
						socket.setSoTimeout(60_000);
						transfer(socket);
					}
				}
				finally
				{
					mServerSocket.close();
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