package org.terifan.net.ftp.client;

import java.io.ByteArrayOutputStream;


public class Test
{
	public static void main(String ... args)
	{
		try
		{
			try (FTPClient client = new FTPClient("example.com", 21, true))
			{
				client.setLogOutput(System.out);

				client.connect("user", "pass", "/home");

				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				client.getFile("AUEP184707", baos, null);
				System.out.println(baos.toString());
			}
		}
		catch (Throwable e)
		{
			e.printStackTrace(System.out);
		}
	}
}
