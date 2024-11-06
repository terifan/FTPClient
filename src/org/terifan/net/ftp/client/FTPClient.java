package org.terifan.net.ftp.client;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.LineNumberReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import org.terifan.util.Calendar;


public class FTPClient implements Closeable
{
	private final static String CRLF = "\015\012";
	private boolean mIsConnected;
	private boolean mIsUnixServer;
	private boolean mUsePassiveConnection;
	private boolean mTypeSent;
	private int mPort;
	private Socket mSocket;
	private String mHostAddress;
	private InputStream mInputStream;
	private OutputStream mOutputStream;
	private String mCurrentServerTypeSetting;
	private PrintStream mLog;


	/**
	 * Creates a new FTPClient. The clients connect method connects the client with the server at the specified host address and port.
	 *
	 * @param aHostAddress the host address or IP number of the FTP server.
	 * @param aPort the port used by the FTP server. Most servers operates on port 21.
	 * @param aUsePassive use passive (PASV) connection instead of direct (PORT) connection.
	 */
	public FTPClient(String aHostAddress, int aPort, boolean aUsePassive)
	{
		mHostAddress = aHostAddress;
		mPort = aPort;
		mUsePassiveConnection = aUsePassive;
		mCurrentServerTypeSetting = "I";
	}


	public void setIsUnixServer(boolean aIsUnixServer)
	{
		mIsUnixServer = aIsUnixServer;
	}


	public boolean getIsUnixServer()
	{
		return mIsUnixServer;
	}


	/**
	 * Sets the output for the communications log. When the log is enabled, all communications between the client and server is written to
	 * the PrintStream provided.<p>
	 *
	 * The logging is enabled if PrintStream object provided is non null.<p>
	 *
	 * Warning: the communications log may contain the user name and password of the user logging on to the server.
	 *
	 * @param aPrintStream a PrintStream object receiving the communications log.
	 */
	public void setLogOutput(PrintStream aPrintStream)
	{
		mLog = aPrintStream;
	}


	/**
	 * Returns the communications log PrintStream object.
	 *
	 * @return the communications log PrintStream object.
	 */
	public PrintStream getCommunicationsLogOutput()
	{
		return mLog;
	}


	/**
	 * Connects this client with the server with the user information provided.
	 *
	 * @param aUserName the user name used for authentication.
	 * @param aPassword the password used for authentication.
	 * @param aInitialPath initial path on the serve. Null or zero length values are ignored.
	 * @throws IOException if the client already is connected.
	 */
	public synchronized void connect(String aUserName, String aPassword, String aInitialPath) throws IOException
	{
		if (mIsConnected)
		{
			throw new IOException("Client already connected.");
		}

		mSocket = new Socket(mHostAddress, mPort);
		mSocket.setSoTimeout(360_000);
		mInputStream = mSocket.getInputStream();
		mOutputStream = mSocket.getOutputStream();
		mIsConnected = true;

		readInput();

		writeOutput("USER " + aUserName);

		Input input = readInput();

		if (input.code == 530)
		{
			throw new IOException(input.toString());
		}

		writeOutput("PASS " + aPassword);

		input = readInput();

		if (input.code == 530)
		{
			throw new IOException(input.toString());
		}

		if (aInitialPath != null && aInitialPath.length() > 0 && !getWorkingDirectory().equals(aInitialPath))
		{
			changeWorkingDirectory(aInitialPath);
		}

	}


	/**
	 * Gets the contents of a file on the server.
	 *
	 * @param aFile the reference to a file on the remote server.
	 * @param aOutputStream the file contents is written to this OutputStream.
	 * @return true if the file were successfully read.
	 * @throws IllegalArgumentException if the aFile object represents a directory.
	 * @throws ResponseCodeException when an unexpected response code is encountered.
	 * @throws IOException when network connection exceptions occur.
	 */
	public boolean getFile(RemoteFile aFile, OutputStream aOutputStream, ProgressListener aProgressListener) throws IOException, IllegalArgumentException, ResponseCodeException
	{
		if (aFile.isDirectory())
		{
			throw new IllegalArgumentException("aFile provided is a directory.");
		}

		return getFile(aFile.getAbsolutePath(), aOutputStream, aProgressListener);
	}


	/**
	 * Gets the contents of a file on the remote server.
	 *
	 * @param aPath the path to the file on the remote server.
	 * @param aOutputStream the file contents is written to this OutputStream.
	 * @return true if the file were successfully read or false if reading the file failed.
	 * @throws ResponseCodeException when an unexpected response code is encountered.
	 * @throws IOException when network connection exceptions occur.
	 */
	public boolean getFile(String aPath, OutputStream aOutputStream, ProgressListener aProgressListener) throws IOException, ResponseCodeException
	{
		Input input;

		setType(mCurrentServerTypeSetting);

		DataSocket dataSocket;

		if (mUsePassiveConnection)
		{
			writeOutput("PASV");

			input = readInput();
			if (input.code != 227)
			{
				throw new ResponseCodeException("Expected response 227, response: " + input);
			}

			dataSocket = PassiveDataSocket.createInputSocket(input.toString(), aOutputStream, aProgressListener);
			dataSocket.start();
		}
		else
		{
			dataSocket = ActiveDataSocket.createInputSocket(aOutputStream, aProgressListener);
			dataSocket.start();

			writeOutput("PORT " + dataSocket.getAddress());

			input = readInput();
			if (input.code != 200)
			{
				throw new ResponseCodeException("Expected response 200, response: " + input);
			}
		}

		writeOutput("RETR " + aPath);

		input = readInput();
		if (input.code == 550)
		{
			return false;
		}
		else if (input.code != 150)
		{
			throw new ResponseCodeException("Expected response 150, response: " + input);
		}

		if (mLog != null)
		{
			mLog.println(Calendar.now() + " CLIENT: <receiving data>");
		}

		input = readInput();
		if (input.code != 226)
		{
			throw new ResponseCodeException("Expected response 226, response: " + input);
		}

		dataSocket.block();

		return true;
	}


	/**
	 * Stores a file on the remote server.
	 *
	 * @param aFile the reference to a file on the remote server.
	 * @param aInputStream a input stream containing the file data.
	 * @param aProgressListener a ProgressListener or null. The ProgressListener receive progress information from the client
	 * @return true if the file were successfully uploaded or false if upload failed.
	 * @throws IllegalArgumentException if the aFile is a directory.
	 * @throws ResponseCodeException when an unexpected response code is encountered.
	 * @throws IOException when network connection exceptions occur.
	 */
	public boolean putFile(RemoteFile aFile, InputStream aInputStream, ProgressListener aProgressListener) throws IOException, IllegalArgumentException, ResponseCodeException
	{
		if (aFile.isDirectory())
		{
			throw new IllegalArgumentException("aFile provided is a directory.");
		}

		return putFile(aFile.getAbsolutePath(), aInputStream, aProgressListener);
	}


	/**
	 * Stores a file on the remote server.
	 *
	 * @param aPath the path to the file on the remote server.
	 * @param aInputStream a input stream containing the file data.
	 * @param aProgressListener a ProgressListener or null. The ProgressListener receive progress information from the client
	 * @return true if the file were successfully uploaded or false if upload failed.
	 * @throws ResponseCodeException when an unexpected response code is encountered.
	 * @throws IOException when network connection exceptions occur.
	 */
	public boolean putFile(String aPath, InputStream aInputStream, ProgressListener aProgressListener) throws IOException, ResponseCodeException
	{
		Input input;

		setType(mCurrentServerTypeSetting);

		DataSocket dataSocket;

		if (mUsePassiveConnection)
		{
			writeOutput("PASV");

			input = readInput();
			if (input.code != 227)
			{
				throw new ResponseCodeException("Expected response 227, response: " + input);
			}

			dataSocket = PassiveDataSocket.createOutputSocket(input.toString(), aInputStream, aProgressListener);
			dataSocket.start();
		}
		else
		{
			dataSocket = ActiveDataSocket.createOutputSocket(aInputStream, aProgressListener);
			dataSocket.start();

			writeOutput("PORT " + dataSocket.getAddress());

			input = readInput();
			if (input.code != 200)
			{
				throw new ResponseCodeException("Expected response 200, response: " + input);
			}
		}

		writeOutput("STOR " + aPath);

		input = readInput();
		if (input.code == 550)
		{
			throw new ResponseCodeException("Access denied, response: " + input);
		}
		if (input.code != 150 && input.code != 125) // 125 = "connection already open"
		{
			throw new ResponseCodeException("Expected response 150 or 125, response: " + input);
		}

		if (mLog != null)
		{
			mLog.println(Calendar.now() + " CLIENT: <sending data>");
		}

		input = readInput();
		if (input.code != 226)
		{
			throw new ResponseCodeException("Expected response 226, response: " + input);
		}

		dataSocket.block();

		return true;
	}


	/**
	 * Deletes a file on the remote server.
	 *
	 * @param aFile the reference to a file on the remote server.
	 * @return true if the directory were deleted or false if the client lack permission to delete the directory.
	 * @throws IllegalArgumentException if the aFile is a directory.
	 * @throws ResponseCodeException when an unexpected response code is encountered.
	 * @throws IOException when network connection exceptions occur.
	 */
	public boolean deleteFile(RemoteFile aFile) throws IOException, IllegalArgumentException, ResponseCodeException
	{
		if (aFile.isDirectory())
		{
			throw new IllegalArgumentException("aFile provided is a directory.");
		}

		return deleteFile(aFile.getAbsolutePath());
	}


	/**
	 * Deletes a file on the remote server.
	 *
	 * @param aPath the path to the file on the remote server.
	 * @return true if the file were deleted or false if the client lack permission to delete the file.
	 * @throws ResponseCodeException when an unexpected response code is encountered.
	 * @throws IOException when network connection exceptions occur.
	 */
	public boolean deleteFile(String aPath) throws IOException, ResponseCodeException
	{
		writeOutput("DELE " + aPath);

		Input input = readInput();
		if (input.code != 250 && input.code != 550)
		{
			throw new ResponseCodeException("Expected response 250 or 550, response: " + input);
		}

		return input.code == 250;
	}


	/**
	 * Deletes a directory on the remote server.
	 *
	 * @param aFile the reference to a directory on the remote server.
	 * @return true if the directory were deleted or false if the client lack permission to delete the directory.
	 * @throws IllegalArgumentException if the aFile isn't a directory.
	 * @throws ResponseCodeException when an unexpected response code is encountered.
	 * @throws IOException when network connection exceptions occur.
	 */
	public boolean deleteDirectory(RemoteFile aFile) throws IOException, IllegalArgumentException, ResponseCodeException
	{
		if (!aFile.isDirectory())
		{
			throw new IllegalArgumentException("aFile provided is a directory.");
		}

		return deleteDirectory(aFile.getAbsolutePath());
	}


	/**
	 * Deletes a directory on the remote server.
	 *
	 * @param aPath the path to the file on the remote server.
	 * @return true if the directory were deleted or false if the client lack permission to delete the directory.
	 * @throws ResponseCodeException when an unexpected response code is encountered.
	 * @throws IOException when network connection exceptions occur.
	 */
	public boolean deleteDirectory(String aPath) throws IOException, ResponseCodeException
	{
		writeOutput("RMD " + aPath);

		Input input = readInput();
		if (input.code != 250 && input.code != 550)
		{
			throw new ResponseCodeException("Expected response 250 or 550, response: " + input);
		}

		return input.code == 250;
	}


	/**
	 * Creates a directory on the remote server.
	 *
	 * @param aPath the path to the directory on the remote server.
	 * @return true if the directory were successfully created.
	 * @throws ResponseCodeException when an unexpected response code is encountered.
	 * @throws IOException when network connection exceptions occur.
	 */
	public boolean createDirectory(String aPath) throws IOException, ResponseCodeException
	{
		writeOutput("MKD " + aPath);

		Input input = readInput();
		if (input.code != 257 && input.code != 550)
		{
			throw new ResponseCodeException("Expected response 257 or 550, response: " + input);
		}

		return input.code == 257;
	}


	/**
	 * Returns an array of the files in the current working directory on the server.
	 *
	 * Note: empty directories are returned with a zero length array.
	 *
	 * @return an array of RemoteFile objects.
	 * @throws ResponseCodeException when an unexpected response code is encountered.
	 * @throws IOException when network connection exceptions occur.
	 */
	public RemoteFile[] getFileList() throws IOException, ResponseCodeException
	{
		return getFileList(null);
	}


	public RemoteFile[] getFileList(ProgressListener aProgressListener) throws IOException, ResponseCodeException
	{
		String workingDirectory = getWorkingDirectory();

		ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();

		DataSocket dataSocket;

		Input input;

		if (mUsePassiveConnection)
		{
			writeOutput("PASV");

			input = readInput();
			if (input.code != 227)
			{
				throw new ResponseCodeException("Expected response 227, response: " + input);
			}

			dataSocket = PassiveDataSocket.createInputSocket(input.toString(), outBuffer, aProgressListener);
			dataSocket.start();
		}
		else
		{
			dataSocket = ActiveDataSocket.createInputSocket(outBuffer, aProgressListener);
			dataSocket.start();

			writeOutput("PORT " + dataSocket.getAddress());

			input = readInput();
			if (input.code != 200)
			{
				throw new ResponseCodeException("Expected response 200, response: " + input);
			}
		}

		writeOutput("LIST");

		input = readInput();
		if (input.code != 150 && input.code != 125) // 125 = "connection already open"
		{
			throw new ResponseCodeException("Expected response 150 or 125, response: " + input);
		}

		if (mLog != null)
		{
			mLog.println(Calendar.now() + " CLIENT: <receiving data>");
		}

		input = readInput();
		if (input.code != 226 && input.code != 250)
		{
			throw new ResponseCodeException("Expected response 226, response: " + input);
		}

		dataSocket.block();

		ArrayList<RemoteFile> tempFiles = new ArrayList<>();
		LineNumberReader in = new LineNumberReader(new StringReader(new String(outBuffer.toByteArray())));
		for (String s; (s = in.readLine()) != null;)
		{
			RemoteFile file = createRemoteFile(workingDirectory, s);
			if (file != null)
			{
				tempFiles.add(file);
			}
		}
		RemoteFile[] files = new RemoteFile[tempFiles.size()];
		tempFiles.toArray(files);

		if (mLog != null)
		{
			mLog.println(Calendar.now() + " CLIENT: <received " + outBuffer.size() + " bytes, " + files.length + " file entries>");
		}

		return files;
	}


	/**
	 * This method use the MLSD command to list files and an exception is thrown if the server doesn't support the method.
	 */
	public RemoteFile[] getFileListNew() throws IOException, ResponseCodeException
	{
		return getFileListNew(null);
	}


	/**
	 * This method use the MLSD command to list files and an exception is thrown if the server doesn't support the method.
	 */
	public RemoteFile[] getFileListNew(ProgressListener aProgressListener) throws IOException, ResponseCodeException
	{
		String workingDirectory = getWorkingDirectory();

		ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();

		DataSocket dataSocket;

		Input input;

		if (mUsePassiveConnection)
		{
			writeOutput("PASV");

			input = readInput();
			if (input.code != 227)
			{
				throw new ResponseCodeException("Expected response 227, response: " + input);
			}

			dataSocket = PassiveDataSocket.createInputSocket(input.toString(), outBuffer, aProgressListener);
			dataSocket.start();
		}
		else
		{
			dataSocket = ActiveDataSocket.createInputSocket(outBuffer, aProgressListener);
			dataSocket.start();

			writeOutput("PORT " + dataSocket.getAddress());

			input = readInput();
			if (input.code != 200)
			{
				throw new ResponseCodeException("Expected response 200, response: " + input);
			}
		}

		writeOutput("MLSD");

		input = readInput();
		if (input.code != 150 && input.code != 125) // 125 = "connection already open"
		{
			throw new ResponseCodeException("Expected response 150 or 125, response: " + input);
		}

		if (mLog != null)
		{
			mLog.println(Calendar.now() + " CLIENT: <receiving data>");
		}

		input = readInput();
		if (input.code != 226)
		{
			throw new ResponseCodeException("Expected response 226, response: " + input);
		}

		dataSocket.block();

		ArrayList<RemoteFile> tempFiles = new ArrayList<>();
		LineNumberReader in = new LineNumberReader(new StringReader(new String(outBuffer.toByteArray())));
		for (String s; (s = in.readLine()) != null;)
		{
			RemoteFile file = createRemoteFileNew(workingDirectory, s);
			if (file != null && (file.getType().equals("dir") || file.getType().equals("file")))
			{
				tempFiles.add(file);
			}
		}
		RemoteFile[] files = new RemoteFile[tempFiles.size()];
		tempFiles.toArray(files);

		if (mLog != null)
		{
			mLog.println(Calendar.now() + " CLIENT: <received " + outBuffer.size() + " bytes, " + files.length + " file entries>");
		}

		return files;
	}


	/**
	 * Returns the current working directory on the server.
	 *
	 * @return the working directory of the server.
	 * @throws ResponseCodeException when an unexpected response code is encountered.
	 * @throws IOException when network connection exceptions occur.
	 */
	public String getWorkingDirectory() throws IOException, ResponseCodeException
	{
		setType("A");

		writeOutput("PWD");
		Input input = readInput();
		if (input.code != 257)
		{
			throw new ResponseCodeException("Expected response 257, response: " + input);
		}
		String path = input.toString().substring(4);
		if (path.startsWith("\""))
		{
			path = path.substring(1);
			path = path.substring(0, path.indexOf("\""));
		}
		else
		{
			throw new IOException("Failed to interpret response, expected quote-sign: " + input);
		}

		return path;
	}


	/**
	 * Changes the working directory on the server. This method can not guarantee success. A subsequent call to getWorkingDirectory() can be
	 * made to verify success.
	 *
	 * @param aPath the new working directory of the server.
	 * @throws ResponseCodeException when an unexpected response code is encountered.
	 * @throws IOException when network connection exceptions occur.
	 */
	public boolean changeWorkingDirectory(String aPath) throws IOException, ResponseCodeException
	{
		setType("A");

		writeOutput("CWD " + aPath);
		Input input = readInput();
		if (input.code != 250 && input.code != 550)
		{
			throw new ResponseCodeException("Expected response 250, response: " + input);
		}

		return input.code == 250;
	}


	/**
	 * Renames a file on the server.
	 *
	 * @param aFromPath the path of the file to be renamed.
	 * @param aToPath the new path of the file.
	 * @throws ResponseCodeException when an unexpected response code is encountered.
	 * @throws IOException when network connection exceptions occur.
	 */
	public void rename(String aFromPath, String aToPath) throws IOException, ResponseCodeException
	{
		setType("A");

		writeOutput("RNFR " + aFromPath);
		Input input = readInput();
		if (input.code != 350)
		{
			throw new ResponseCodeException("Expected response 350, response: " + input);
		}

		writeOutput("RNTO " + aToPath);
		input = readInput();
		if (input.code != 250)
		{
			throw new ResponseCodeException("Expected response 250, response: " + input);
		}
	}


	/**
	 * Sets the response encoding type either to ASCII or Binary. The user rarely needs to call this method. Most method calls this method
	 * when necessary.
	 *
	 * This method keeps track of the server state and will only instruct the server to change mode when the mode is altered.
	 *
	 * @param aType either uppercase A for ASCII or uppercase I for Binary mode.
	 * @throws ResponseCodeException when an unexpected response code is encountered.
	 * @throws IllegalArgumentException when aType is neither "A" or "I".
	 * @throws IOException when network connection exceptions occur.
	 */
	public FTPClient setType(String aType) throws IOException, ResponseCodeException, IllegalArgumentException
	{
		if (!aType.equals("A") && !aType.equals("I"))
		{
			throw new IllegalArgumentException("aType is expected to be either \"A\" or \"I\".");
		}

		if (!mTypeSent || !aType.equals(mCurrentServerTypeSetting))
		{
			mTypeSent = true;
			writeOutput("TYPE " + aType);
			Input input = readInput();
			if (input.code != 200)
			{
				throw new ResponseCodeException("Expected response 200, response: " + input);
			}

			mCurrentServerTypeSetting = aType;
		}

		return this;
	}


	/**
	 * Disconnects this client from the server. A client should always terminate it's connection with a server by disconnecting. Failing to
	 * do so can prohibit the client from reconnecting in the future.<p>
	 *
	 * Note: This method sends the QUIT command without receiving any response from the server.
	 *
	 * @throws IOException when network connection exceptions occur. Even if an exception is thrown, the network connection is disconnected
	 * and all streams are closed.
	 */
	@Override
	public synchronized void close() throws IOException
	{
		if (mIsConnected)
		{
			mIsConnected = false;

			try
			{
				writeOutput("QUIT");
			}
			catch (IOException e)
			{
			}

			if (mInputStream != null)
			{
				mInputStream.close();
				mInputStream = null;
			}
			if (mOutputStream != null)
			{
				mOutputStream.close();
				mOutputStream = null;
			}
			if (mSocket != null)
			{
				mSocket.close();
				mSocket = null;
			}
		}
	}


	private synchronized Input readInput() throws IOException
	{
		Input input = new Input();

		for (;;)
		{
			StringBuilder str = new StringBuilder();
			for (int c; (c = mInputStream.read()) != -1;)
			{
				str.append((char)c);

				if (str.length() >= 2 && str.lastIndexOf(CRLF) == str.length() - CRLF.length())
				{
					break;
				}
			}

			if (mLog != null)
			{
				mLog.print(Calendar.now() + " SERVER: " + str);
			}

			if (str.length() < 3)
			{
				throw new IOException("Expected code is too short: \"" + str + "\"");
			}

			input.code = Integer.parseInt(str.substring(0, 3));
			input.append(str.toString());

			if (str.length() < 4 || str.charAt(3) != '-')
			{
				break;
			}
		}

		return input;
	}


	private void writeOutput(String aCommand) throws IOException
	{
		if (mOutputStream == null)
		{
			throw new IOException("Not connected to server");
		}

		mOutputStream.write(aCommand.getBytes());
		mOutputStream.write(CRLF.getBytes());

		if (mLog != null)
		{
			if (aCommand.startsWith("PASS"))
			{
				aCommand = "PASS ***********";
			}

			mLog.print(Calendar.now() + " CLIENT: " + aCommand + CRLF);
		}
	}


	private RemoteFile createRemoteFile(String aPath, String aData)
	{
		if (aData.matches("^total [0-9]{1,10}$"))
		{
			return null;
		}

		try
		{
			String date, time, name, path;
			long size;
			boolean directory;

			if (mLog != null)
			{
				mLog.println(aData);
			}

			if (mIsUnixServer)
			{
				// -rw-rw-rw-   1 user     group          1234 Nov  7 14:17 filename.ext
				// -rw-r--r--   1 ftpuser  ftpusers  15798 Nov  1 15:30 cvljgxuc
				// drwxr-xr-x 1 ftp ftp              0 May 31 14:34 lynx
				// drwxr-xr-x 1 ftp ftp              0 Feb 11  2016 lib

				String[] tmp = split(aData);

				directory = tmp[0].startsWith("d");
				// unknown 1
				// user 2
				// group 3
				size = Long.parseLong(tmp[4]);
				date = "1970-01-01"; // 5
//				// day 6
				time = "00:00:00"; // 7
				name = tmp[8];

				if (name.startsWith("/"))
				{
					name = name.substring(1);
				}
			}
			else
			{
				date = aData.substring(0, 8);
				time = aData.substring(10, 17);
				directory = aData.substring(24, 29).equals("<DIR>");
				String temp = aData.substring(29, 38).trim();
				size = 0;
				if (temp.length() > 0)
				{
					size = Long.parseLong(temp);
				}
				name = aData.substring(38).trim();
				if (name.startsWith("/"))
				{
					name = name.substring(1);
				}
			}

			path = aPath.replace('\\', '/');
			if (path.endsWith("/"))
			{
				path = path.substring(0, path.length() - 1);
			}

			return new RemoteFile(this, path, name, date, time, size, directory);
		}
		catch (Exception e)
		{
			if (mLog != null)
			{
				mLog.println("CLIENT: Failed to decode file information: " + aData);
			}
			return null;
		}
	}


	private RemoteFile createRemoteFileNew(String aWorkingDirectory, String aData)
	{
		try
		{
			if (mLog != null)
			{
				mLog.println(aData);
			}

			int i = aData.indexOf(' ');

			String name = aData.substring(aData.lastIndexOf(' ') + 1);
			String path = aWorkingDirectory + name;

			name = name.substring(name.lastIndexOf('/') + 1);

			SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss.SSS");
			String type = null;
			String permissions = null;
			long size = 0;
			long datetime = 0;

			for (String part : aData.substring(0, i).split(";"))
			{
				int j = part.indexOf("=");
				String key = part.substring(0, j);
				String value = part.substring(j + 1);

				switch (key)
				{
					case "Type":
						type = value;
						break;
					case "Modify":
						datetime = dateFormat.parse(value).getTime();
						break;
					case "Size":
						size = Long.parseLong(value);
						break;
					case "Perm":
						permissions = value;
						break;
				}
			}

			return new RemoteFile(this, type, path, name, datetime, size, permissions);
		}
		catch (Exception e)
		{
			if (mLog != null)
			{
				mLog.println("CLIENT: Failed to decode file information: " + aData);
			}
			return null;
		}
	}


	private static String[] split(String aText)
	{
		ArrayList<String> list = new ArrayList<>();

		String w = "";
		for (char c : aText.toCharArray())
		{
			if (c == ' ' && list.size() < 8)
			{
				if (!w.isEmpty())
				{
					list.add(w);
					w = "";
				}
			}
			else
			{
				w += c;
			}
		}

		list.add(w);

		return list.toArray(new String[list.size()]);
	}


	public String getHostAddress()
	{
		return mHostAddress;
	}


	public void changeTime(String aFileName, String aUTCDateTime) throws IOException
	{
		writeOutput("MFMT " + aUTCDateTime + " " + aFileName);
		Input input = readInput();
		if (input.code != 213)
		{
			throw new ResponseCodeException("Expected response 213, response: " + input);
		}
	}
}
