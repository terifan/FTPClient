package org.terifan.net.ftp.client;

import java.text.SimpleDateFormat;
import org.terifan.util.Calendar;


/**
 * Represents a file existing on a remote server.
 */
public class RemoteFile
{
	private String mType;
	private long mSize;
	private long mDateTime;
	private String mName;
	private String mPath;
	private String mPermissions;
	private FTPClient mClient;


	RemoteFile(FTPClient aClient, String aPath, String aName, String aDate, String aTime, long aSize, boolean aDirectory)
	{
		mClient = aClient;
		mPath = aPath;
		mName = aName;
		mSize = aSize;
		mType = aDirectory ? "dir" : "file";

		try
		{
			mDateTime = Calendar.parse(aDate).get();
		}
		catch (Exception e)
		{
			System.out.println("Failed to parse date: " + aDate);
		}
		try
		{
			mDateTime += Calendar.parse(aTime).get();
		}
		catch (Exception e)
		{
			System.out.println("Failed to parse time: " + aTime);
		}
	}


	RemoteFile(FTPClient aClient, String aType, String aPath, String aName, long aDateTime, long aSize, String aPermissions)
	{
		mClient = aClient;
		mType = aType;
		mPath = aPath;
		mName = aName;
		mDateTime = aDateTime;
		mSize = aSize;
		mPermissions = aPermissions;
	}


	public FTPClient getClient()
	{
		return mClient;
	}


	/**
	 * Returns the path of this remote file.
	 */
	public String getPath()
	{
		return mPath;
	}


	/**
	 * Returns the absolute path name of this remote file.
	 */
	public String getAbsolutePath()
	{
		return mPath + "/" + mName;
	}


	public long getDateTime()
	{
		return mDateTime;
	}


	/**
	 * Returns true if this remote file is a directory.
	 */
	public boolean isDirectory()
	{
		return mType.equals("dir");
	}


	/**
	 * Returns the name of this remote file.
	 */
	public String getName()
	{
		return mName;
	}


	/**
	 * Returns the size of this remote file in bytes.
	 */
	public long getSize()
	{
		return mSize;
	}


	public String getPermissions()
	{
		return mPermissions;
	}


	public String getType()
	{
		return mType;
	}


	/**
	 * Returns a description of this remote file.
	 */
	@Override
	public String toString()
	{
		return String.format("DateTime: %s, Type: %-4s, Size: %8d, Perm: %s, Name: %s", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(mDateTime), mType, mSize, mPermissions, mName);
	}


	@Override
	public int hashCode()
	{
		return mPath.hashCode() ^ mName.hashCode();
	}


	@Override
	public boolean equals(Object aObj)
	{
		if (aObj == this)
		{
			return true;
		}
		if (aObj instanceof RemoteFile)
		{
			RemoteFile other = (RemoteFile)aObj;
			return other.mName.equals(mName) && other.mPath.equals(mPath);
		}
		return false;
	}
}
