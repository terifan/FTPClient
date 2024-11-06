package org.terifan.net.ftp.client;


class Input
{
	private StringBuilder mMessage;
	int code;


	public Input()
	{
		code = -1;
		mMessage = new StringBuilder();
	}


	public void append(String aText)
	{
		mMessage.append(aText);
	}


	@Override
	public String toString()
	{
		return mMessage.toString();
	}
}
